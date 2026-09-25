package com.intelliresult.nexus.service;

import com.google.zxing.WriterException;
import com.intelliresult.nexus.dao.ExamDAO;
import com.intelliresult.nexus.dao.ExamDAOImpl;
import com.intelliresult.nexus.dao.ResultDAO;
import com.intelliresult.nexus.dao.ResultDAOImpl;
import com.intelliresult.nexus.dao.ResultSummaryDAO;
import com.intelliresult.nexus.dao.ResultSummaryDAOImpl;
import com.intelliresult.nexus.dao.StudentDAO;
import com.intelliresult.nexus.dao.StudentDAOImpl;
import com.intelliresult.nexus.entity.Exam;
import com.intelliresult.nexus.entity.Result;
import com.intelliresult.nexus.entity.ResultSummary;
import com.intelliresult.nexus.entity.Section;
import com.intelliresult.nexus.entity.Student;
import com.intelliresult.nexus.entity.SystemSetting;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.entity.enums.ResultStatus;
import com.intelliresult.nexus.exception.BusinessRuleException;
import com.intelliresult.nexus.exception.ResourceNotFoundException;
import com.intelliresult.nexus.exception.ResultProcessingException;
import com.intelliresult.nexus.service.dto.MarksheetData;
import com.intelliresult.nexus.service.dto.MarksheetSubjectRow;
import com.intelliresult.nexus.util.PDFUtil;
import com.intelliresult.nexus.util.QRCodeUtil;
import com.lowagie.text.DocumentException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Sec. 19's marksheet generation, composed from services/DAOs every earlier
 * phase already built rather than recomputing anything - {@link
 * ResultSummary} for the aggregate figures (Phase 7's {@code
 * ResultCalculationService}), {@link Result} rows for the subject-wise
 * table (Phase 6b/7), {@link VerificationService} for the QR token (this
 * phase). This class's own job is narrow: confirm a marksheet may be
 * issued at all, flatten entities into a {@link MarksheetData} PDFUtil can
 * render without touching Hibernate, and translate the PDF/QR libraries'
 * checked exceptions into this project's exception vocabulary. Scoped to
 * student self-service only for Phase 12 (studentId always comes from the
 * caller's own session, never a request parameter - see {@code
 * MarksheetServlet}); an admin-initiated "generate any student's
 * marksheet" entry point is Phase 15 Reporting's concern, not this one's -
 * this method's signature already supports it without modification
 * whenever that phase adds the controller for it.
 */
public class MarksheetService {

    private static final Logger LOGGER = LogManager.getLogger(MarksheetService.class);

    /** Source resolution for the embedded QR image, in pixels. Comfortably legible at the ~1 inch (78pt) size PDFUtil places it at, without the PNG being any larger than it needs to be for a document that also has to embed a logo and render quickly. */
    private static final int QR_SOURCE_SIZE_PX = 260;

    private final StudentDAO studentDAO = new StudentDAOImpl();
    private final ExamDAO examDAO = new ExamDAOImpl();
    private final ResultDAO resultDAO = new ResultDAOImpl();
    private final ResultSummaryDAO resultSummaryDAO = new ResultSummaryDAOImpl();
    private final SystemSettingsService systemSettingsService = new SystemSettingsService();
    private final VerificationService verificationService = new VerificationService();

    /**
     * Builds and returns the marksheet PDF as bytes.
     *
     * @param verificationBaseUrl scheme+host+port+context-path, e.g.
     *                            {@code https://results.example.edu/intelliresult-nexus}
     *                            - resolved by the servlet from the live
     *                            {@code HttpServletRequest} rather than a
     *                            static config value, so the QR code always
     *                            points at whatever host actually served
     *                            this request (see {@code MarksheetServlet}
     *                            for the one-line derivation and why).
     * @throws ResourceNotFoundException if the student or exam does not exist.
     * @throws BusinessRuleException     if this exam's result for this
     *                                   student is not yet fully published
     *                                   (Sec. 18: "only published results
     *                                   are visible" applies to the
     *                                   marksheet exactly as it does to the
     *                                   on-screen result).
     * @throws ResultProcessingException if PDF or QR rendering itself fails.
     */
    public byte[] generateMarksheetPdf(Long studentId, Long examId, User requestedBy, String verificationBaseUrl) {
        Student student = studentDAO.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found."));
        Exam exam = examDAO.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Examination not found."));

        // isComplete() (Sec. 47's "prevent publishing incomplete results"
        // signal, computed once in Phase 7) doubles as the marksheet gate:
        // an official document can't be issued for a result that is only
        // partially published, and a summary can only be complete once
        // every subject has cleared PUBLISHED, so no separate per-Result
        // status loop is needed just to answer "is this ready".
        ResultSummary summary = resultSummaryDAO.findByStudentAndExam(studentId, examId)
                .filter(ResultSummary::isComplete)
                .orElseThrow(() -> new BusinessRuleException(
                        "A marksheet is not yet available for this examination - results must be fully published first."));

        List<Result> results = resultDAO.findByStudentAndExam(studentId, examId).stream()
                .sorted(Comparator.comparing(r -> r.getSubject().getSubjectCode()))
                .toList();
        String resultStatusLabel = ResultStatus.mostFinal(results.stream().map(Result::getStatus).toList()).name();

        SystemSetting settings = systemSettingsService.getSettings();
        String token = verificationService.getOrCreateVerificationToken(studentId, examId, requestedBy);
        String verificationUrl = verificationBaseUrl + "/verify/result/" + token;

        List<MarksheetSubjectRow> subjectRows = results.stream()
                .map(r -> new MarksheetSubjectRow(
                        r.getSubject().getSubjectCode(), r.getSubject().getSubjectName(), r.getSubject().getCredits(),
                        r.getTheoryMarks(), r.getPracticalMarks(), r.getInternalMarks(),
                        r.getTotalMarks(), r.getSubject().getTotalMaxMarks(),
                        r.getPercentage(), r.getGrade(), r.getGradePoint()))
                .toList();

        Section section = student.getCurrentSection();

        try {
            byte[] qrPng = QRCodeUtil.generatePngBytes(verificationUrl, QR_SOURCE_SIZE_PX);

            MarksheetData data = new MarksheetData(
                    settings.getInstitutionName(), settings.getInstitutionAddress(), settings.getInstitutionLogoPath(),
                    settings.getSignatoryName(), settings.getSignatoryDesignation(),
                    student.getUser().getFullName(), student.getRollNo(), student.getCourse().getName(),
                    student.getCourse().getDepartment().getName(),
                    section != null ? section.getName() : null,
                    "Semester " + exam.getSemester().getSemesterNumber(),
                    exam.getAcademicYear().getLabel(),
                    exam.getName(), exam.getExamType().name().replace('_', ' '),
                    exam.getStartTime().toLocalDate(), exam.getEndTime().toLocalDate(),
                    subjectRows,
                    summary.getTotalObtainedMarks(), summary.getTotalMaxMarks(), summary.getOverallPercentage(),
                    summary.getOverallGrade(), summary.getSgpa(), summary.getCgpa(), summary.isPass(),
                    summary.getClassRank(), summary.getOverallRank(),
                    resultStatusLabel,
                    token, qrPng, verificationUrl,
                    LocalDateTime.now());

            return PDFUtil.buildMarksheet(data);
        } catch (WriterException | IOException | DocumentException e) {
            LOGGER.error("Marksheet PDF generation failed for student {} / exam {}.", studentId, examId, e);
            throw new ResultProcessingException(
                    "The marksheet could not be generated right now. Please try again in a moment.", e);
        }
    }
}
