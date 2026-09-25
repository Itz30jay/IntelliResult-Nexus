package com.intelliresult.nexus.service;

import com.intelliresult.nexus.dao.AcademicYearDAO;
import com.intelliresult.nexus.dao.AcademicYearDAOImpl;
import com.intelliresult.nexus.dao.ExamDAO;
import com.intelliresult.nexus.dao.ExamDAOImpl;
import com.intelliresult.nexus.dao.ResultDAO;
import com.intelliresult.nexus.dao.ResultDAOImpl;
import com.intelliresult.nexus.dao.ResultSummaryDAO;
import com.intelliresult.nexus.dao.ResultSummaryDAOImpl;
import com.intelliresult.nexus.dao.SectionDAO;
import com.intelliresult.nexus.dao.SectionDAOImpl;
import com.intelliresult.nexus.dao.SubjectDAO;
import com.intelliresult.nexus.dao.SubjectDAOImpl;
import com.intelliresult.nexus.entity.AcademicYear;
import com.intelliresult.nexus.entity.Exam;
import com.intelliresult.nexus.entity.Result;
import com.intelliresult.nexus.entity.ResultSummary;
import com.intelliresult.nexus.entity.Section;
import com.intelliresult.nexus.entity.Subject;
import com.intelliresult.nexus.exception.ResourceNotFoundException;
import com.intelliresult.nexus.service.dto.ReportData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Sec. 37 names this service explicitly. Every method below only reads -
 * {@code ResultSummary} (Phase 7's own calculated totals/percentage/SGPA/
 * rank) and {@code Result} (per-subject marks) rows already computed by
 * {@code ResultCalculationService} - nothing here recalculates a
 * percentage, grade, or rank; Sec. 33's reports are views over numbers
 * that already exist, matching how {@code MarksheetService} (Phase 12)
 * treats the identical data. {@code findByExam}/{@code
 * findByExamAndSubject}'s own Javadoc (Phases 3/7) already named this
 * phase as their intended consumer.
 * <p>
 * Every row value is left as its natural type ({@link BigDecimal}, {@link
 * Integer}) rather than pre-formatted - see {@link ReportData}'s own
 * Javadoc for why. Subject grouping/deduplication throughout is keyed by
 * entity id ({@code Subject::getId}), never by relying on an entity's own
 * {@code equals()}/{@code hashCode()}, which this codebase's entities
 * (matching typical Hibernate practice) do not override for value
 * semantics - id-keyed maps are correct regardless of that.
 */
public class ReportService {

    private final ExamDAO examDAO = new ExamDAOImpl();
    private final SectionDAO sectionDAO = new SectionDAOImpl();
    private final SubjectDAO subjectDAO = new SubjectDAOImpl();
    private final AcademicYearDAO academicYearDAO = new AcademicYearDAOImpl();
    private final ResultDAO resultDAO = new ResultDAOImpl();
    private final ResultSummaryDAO resultSummaryDAO = new ResultSummaryDAOImpl();

    /** Sec. 33's Class Result Report - one row per student, one column per subject sat in this exam by this section, plus the summary totals {@code ResultSummary} already carries. */
    public ReportData classResultReport(Long examId, Long sectionId) {
        Exam exam = examDAO.findById(examId).orElseThrow(() -> new ResourceNotFoundException("Examination not found."));
        Section section = sectionDAO.findById(sectionId).orElseThrow(() -> new ResourceNotFoundException("Section not found."));

        List<ResultSummary> summaries = resultSummaryDAO.findByExamAndSection(examId, sectionId);
        List<Result> sectionResults = resultDAO.findByExam(examId).stream()
                .filter(r -> r.getStudent().getCurrentSection() != null
                        && r.getStudent().getCurrentSection().getId().equals(sectionId))
                .toList();

        // Subjects this section actually sat, ordered by code - id-keyed to
        // avoid depending on Subject's own equals()/hashCode().
        Map<Long, Subject> subjectsById = new LinkedHashMap<>();
        for (Result r : sectionResults) {
            subjectsById.putIfAbsent(r.getSubject().getId(), r.getSubject());
        }
        List<Subject> subjects = subjectsById.values().stream()
                .sorted(Comparator.comparing(Subject::getSubjectCode))
                .toList();

        Map<String, Result> byStudentAndSubject = new LinkedHashMap<>();
        for (Result r : sectionResults) {
            byStudentAndSubject.put(r.getStudent().getId() + ":" + r.getSubject().getId(), r);
        }

        List<String> headers = new ArrayList<>(List.of("Roll No", "Name"));
        for (Subject s : subjects) {
            headers.add(s.getSubjectCode());
        }
        headers.addAll(List.of("Total", "%", "Grade", "Rank", "Result"));

        List<Object[]> rows = new ArrayList<>();
        for (ResultSummary summary : summaries) {
            List<Object> row = new ArrayList<>();
            row.add(summary.getStudent().getRollNo());
            row.add(summary.getStudent().getUser().getFullName());
            for (Subject s : subjects) {
                Result r = byStudentAndSubject.get(summary.getStudent().getId() + ":" + s.getId());
                row.add(r != null ? r.getTotalMarks() : null);
            }
            row.add(summary.getTotalObtainedMarks());
            row.add(summary.getOverallPercentage());
            row.add(summary.getOverallGrade());
            row.add(summary.getClassRank());
            row.add(summary.isPass() ? "PASS" : "FAIL");
            rows.add(row.toArray());
        }

        long passCount = summaries.stream().filter(ResultSummary::isPass).count();
        List<String> summaryLines = List.of(
                "Students: " + summaries.size(),
                "Pass rate: " + formatRate(passCount, summaries.size()));

        return new ReportData("Class Result Report", exam.getName() + " \u00b7 Section " + section.getName(),
                headers.toArray(new String[0]), rows, summaryLines);
    }

    /** Sec. 33's Topper Report - top {@code limit} passing students by percentage, optionally scoped to one section. */
    public ReportData topperReport(Long examId, Long sectionId, int limit) {
        Exam exam = examDAO.findById(examId).orElseThrow(() -> new ResourceNotFoundException("Examination not found."));
        List<ResultSummary> summaries = sectionId != null
                ? resultSummaryDAO.findByExamAndSection(examId, sectionId)
                : resultSummaryDAO.findByExam(examId);

        List<ResultSummary> top = summaries.stream()
                .filter(ResultSummary::isPass)
                .sorted(Comparator.comparing(ResultSummary::getOverallPercentage,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(limit)
                .toList();

        List<Object[]> rows = new ArrayList<>();
        int rank = 1;
        for (ResultSummary s : top) {
            rows.add(new Object[]{rank++, s.getStudent().getRollNo(), s.getStudent().getUser().getFullName(),
                    s.getOverallPercentage(), s.getOverallGrade(), s.getSgpa()});
        }

        String scopeLabel = sectionId != null
                ? sectionDAO.findById(sectionId).map(sec -> "Section " + sec.getName()).orElse("")
                : "All Sections";
        return new ReportData("Topper Report", exam.getName() + " \u00b7 " + scopeLabel,
                new String[]{"Rank", "Roll No", "Name", "Percentage", "Grade", "SGPA"}, rows, List.of());
    }

    /**
     * Sec. 33's "Fail/Improvement Report" - displayed as "Improvement
     * Report" throughout (Sec. 33's own "neutral terminology" instruction,
     * matching Sec. 16's identical precedent from Phase 11). Never
     * reachable by a student (this service's callers are Admin/Teacher
     * only) - the privacy control Sec. 33 also asks for is enforced by
     * that access scoping, not by hiding names within the report itself,
     * which would defeat an internal report's actual purpose.
     */
    public ReportData improvementReport(Long examId, Long sectionId) {
        Exam exam = examDAO.findById(examId).orElseThrow(() -> new ResourceNotFoundException("Examination not found."));
        List<ResultSummary> summaries = sectionId != null
                ? resultSummaryDAO.findByExamAndSection(examId, sectionId)
                : resultSummaryDAO.findByExam(examId);

        List<ResultSummary> needsAttention = summaries.stream()
                .filter(s -> !s.isPass())
                .sorted(Comparator.comparing(ResultSummary::getOverallPercentage,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<Object[]> rows = new ArrayList<>();
        for (ResultSummary s : needsAttention) {
            rows.add(new Object[]{s.getStudent().getRollNo(), s.getStudent().getUser().getFullName(),
                    s.getOverallPercentage(), s.getOverallGrade()});
        }

        List<String> summaryLines = List.of(
                needsAttention.size() + " of " + summaries.size() + " student(s) are below the passing threshold.");

        return new ReportData("Improvement Report", exam.getName(),
                new String[]{"Roll No", "Name", "Percentage", "Grade"}, rows, summaryLines);
    }

    /** Sec. 33's Subject Analysis - average/highest/lowest/pass-rate/grade-distribution for one subject in one exam, computed here since no existing DAO method aggregates at this exact (exam, subject) grain. */
    public ReportData subjectAnalysisReport(Long examId, Long subjectId) {
        Exam exam = examDAO.findById(examId).orElseThrow(() -> new ResourceNotFoundException("Examination not found."));
        Subject subject = subjectDAO.findById(subjectId).orElseThrow(() -> new ResourceNotFoundException("Subject not found."));

        List<Result> results = resultDAO.findByExamAndSubject(examId, subjectId);

        List<Object[]> rows = new ArrayList<>();
        for (Result r : results) {
            rows.add(new Object[]{r.getStudent().getRollNo(), r.getStudent().getUser().getFullName(),
                    r.getTotalMarks(), r.getPercentage(), r.getGrade(),
                    Boolean.TRUE.equals(r.getPass()) ? "PASS" : "FAIL"});
        }

        List<BigDecimal> percentages = results.stream().map(Result::getPercentage).filter(Objects::nonNull).toList();
        BigDecimal average = averageOf(percentages);
        BigDecimal highest = percentages.stream().max(Comparator.naturalOrder()).orElse(null);
        BigDecimal lowest = percentages.stream().min(Comparator.naturalOrder()).orElse(null);
        long passCount = results.stream().filter(r -> Boolean.TRUE.equals(r.getPass())).count();

        // TreeMap for a stable, alphabetical grade ordering (A+, A, B+, ...) in the printed summary.
        Map<String, Long> gradeCounts = new TreeMap<>();
        for (Result r : results) {
            if (r.getGrade() != null) {
                gradeCounts.merge(r.getGrade(), 1L, Long::sum);
            }
        }

        List<String> summaryLines = new ArrayList<>(List.of(
                "Average: " + formatPercent(average),
                "Highest: " + formatPercent(highest),
                "Lowest: " + formatPercent(lowest),
                "Pass rate: " + formatRate(passCount, results.size())));
        gradeCounts.forEach((grade, count) -> summaryLines.add("Grade " + grade + ": " + count));

        return new ReportData("Subject Analysis", exam.getName() + " \u00b7 " + subject.getSubjectName(),
                new String[]{"Roll No", "Name", "Total", "Percentage", "Grade", "Result"}, rows, summaryLines);
    }

    /** Sec. 33's Exam Report - a whole-exam roll-up, one row per subject, plus the overall pass rate across every student's ResultSummary. */
    public ReportData examSummaryReport(Long examId) {
        Exam exam = examDAO.findById(examId).orElseThrow(() -> new ResourceNotFoundException("Examination not found."));
        List<ResultSummary> summaries = resultSummaryDAO.findByExam(examId);
        List<Result> results = resultDAO.findByExam(examId);

        Map<Long, Subject> subjectsById = new LinkedHashMap<>();
        Map<Long, List<Result>> resultsBySubjectId = new LinkedHashMap<>();
        for (Result r : results) {
            Long subjectId = r.getSubject().getId();
            subjectsById.putIfAbsent(subjectId, r.getSubject());
            resultsBySubjectId.computeIfAbsent(subjectId, id -> new ArrayList<>()).add(r);
        }

        List<Subject> orderedSubjects = subjectsById.values().stream()
                .sorted(Comparator.comparing(Subject::getSubjectCode))
                .toList();

        List<Object[]> rows = new ArrayList<>();
        for (Subject subject : orderedSubjects) {
            List<Result> subjectResults = resultsBySubjectId.get(subject.getId());
            List<BigDecimal> percentages = subjectResults.stream().map(Result::getPercentage).filter(Objects::nonNull).toList();
            long passCount = subjectResults.stream().filter(r -> Boolean.TRUE.equals(r.getPass())).count();
            rows.add(new Object[]{subject.getSubjectCode(), subject.getSubjectName(), subjectResults.size(),
                    averageOf(percentages), formatRate(passCount, subjectResults.size())});
        }

        long overallPass = summaries.stream().filter(ResultSummary::isPass).count();
        List<String> summaryLines = List.of(
                "Total students: " + summaries.size(),
                "Overall pass rate: " + formatRate(overallPass, summaries.size()));

        return new ReportData("Exam Report", exam.getName(),
                new String[]{"Subject Code", "Subject", "Students", "Average %", "Pass Rate"}, rows, summaryLines);
    }

    /** Sec. 33's Academic Year Report - one row per exam in the year that has at least one calculated result (Sec. 56 - an exam with nothing yet is omitted, not shown as a row of nothing). */
    public ReportData academicYearReport(Long academicYearId) {
        AcademicYear year = academicYearDAO.findById(academicYearId)
                .orElseThrow(() -> new ResourceNotFoundException("Academic year not found."));
        List<Exam> exams = examDAO.findByAcademicYear(academicYearId);

        List<Object[]> rows = new ArrayList<>();
        for (Exam exam : exams) {
            List<ResultSummary> summaries = resultSummaryDAO.findByExam(exam.getId());
            if (summaries.isEmpty()) {
                continue;
            }
            List<BigDecimal> percentages = summaries.stream()
                    .map(ResultSummary::getOverallPercentage).filter(Objects::nonNull).toList();
            long passCount = summaries.stream().filter(ResultSummary::isPass).count();
            rows.add(new Object[]{exam.getName(), exam.getExamType().name().replace('_', ' '),
                    summaries.size(), averageOf(percentages), formatRate(passCount, summaries.size())});
        }

        return new ReportData("Academic Year Report", year.getLabel(),
                new String[]{"Examination", "Type", "Students", "Average %", "Pass Rate"}, rows, List.of());
    }

    // ---------------------------------------------------------------- helpers

    private BigDecimal averageOf(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private String formatPercent(BigDecimal value) {
        return value == null ? "N/A" : value + "%";
    }

    private String formatRate(long numerator, int denominator) {
        if (denominator == 0) {
            return "N/A";
        }
        return String.format("%.1f%%", (double) numerator / denominator * 100);
    }
}
