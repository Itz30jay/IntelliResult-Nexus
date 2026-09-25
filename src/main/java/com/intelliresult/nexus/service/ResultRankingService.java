package com.intelliresult.nexus.service;

import com.intelliresult.nexus.dao.ResultDAO;
import com.intelliresult.nexus.dao.ResultDAOImpl;
import com.intelliresult.nexus.dao.ResultSummaryDAO;
import com.intelliresult.nexus.dao.ResultSummaryDAOImpl;
import com.intelliresult.nexus.entity.Result;
import com.intelliresult.nexus.entity.ResultSummary;

import java.util.Comparator;
import java.util.List;

/**
 * Sec. "Result Section (Admin)" - ranking by percentage, full mark detail,
 * and the filters behind them. Deliberately a new, separate service rather
 * than added to ResultService: ResultService owns the write side (publish/
 * correct - Sec. 47's approval workflow), this owns the read side (browse/
 * filter/export), and the two have almost no logic in common beyond both
 * reading Result/ResultSummary rows.
 * <p>
 * "Ranking by percentage (high to low)" turned out to already exist:
 * ResultSummary.overallRank is populated by a {@code RANK() OVER (ORDER BY
 * overall_percentage DESC)} native query (ResultSummaryDAOImpl.
 * recalculateRanks) every time an exam is published - rank 1 already means
 * "highest percentage." The admin Results page just never surfaced it,
 * showing flat per-subject Result rows instead of this ranked, per-student
 * view. This service is what finally exposes it.
 */
public class ResultRankingService {

    private final ResultSummaryDAO resultSummaryDAO = new ResultSummaryDAOImpl();
    private final ResultDAO resultDAO = new ResultDAOImpl();

    /**
     * All filters are optional and compose freely - Section 3's own list
     * ("Ranking range, Section, Semester, Exam, any other useful filters")
     * reads as a filter panel where an admin narrows down, not a rigid
     * required sequence. Filtered and sorted here in Java rather than
     * pushed into another native query: a single exam's cohort is at most a
     * few hundred rows (already loaded via the existing findByExam/
     * findByExamAndSection DAO methods), well within the range where a
     * second bespoke SQL query per filter combination would cost more to
     * write and maintain than it would ever save.
     */
    public List<ResultSummary> rankedSummaries(Long examId, Long sectionId, Integer rankFrom, Integer rankTo,
                                                Boolean passOnly, String searchTerm) {
        List<ResultSummary> summaries = (sectionId != null)
                ? resultSummaryDAO.findByExamAndSection(examId, sectionId)
                : resultSummaryDAO.findByExam(examId);

        String normalizedSearch = (searchTerm == null || searchTerm.isBlank()) ? null : searchTerm.trim().toUpperCase();

        return summaries.stream()
                .filter(s -> rankFrom == null || (s.getOverallRank() != null && s.getOverallRank() >= rankFrom))
                .filter(s -> rankTo == null || (s.getOverallRank() != null && s.getOverallRank() <= rankTo))
                .filter(s -> passOnly == null || s.isPass() == passOnly)
                .filter(s -> normalizedSearch == null
                        || s.getStudent().getRollNo().toUpperCase().contains(normalizedSearch)
                        || s.getStudent().getUser().getFullName().toUpperCase().contains(normalizedSearch))
                .sorted(Comparator.comparing(ResultSummary::getOverallRank, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** "Display full mark details for every student" - the per-subject Result rows one ResultSummary aggregates, for the admin's expand/drill-down view of a single student's row. */
    public List<Result> subjectBreakdown(Long studentId, Long examId) {
        return resultDAO.findByStudentAndExam(studentId, examId);
    }

    /** Same filtered/ranked list as {@link #rankedSummaries}, flattened into Excel-ready rows - kept as its own method (not a JSP-side transform) so the exported file's columns can never silently drift from what ExcelUtil.writeXlsx actually receives. */
    public List<Object[]> toExcelRows(List<ResultSummary> summaries) {
        return summaries.stream()
                .map(s -> new Object[]{
                        s.getOverallRank(),
                        s.getStudent().getRollNo(),
                        s.getStudent().getUser().getFullName(),
                        s.getStudent().getCurrentSection() != null ? s.getStudent().getCurrentSection().getName() : "",
                        s.getTotalObtainedMarks(),
                        s.getTotalMaxMarks(),
                        s.getOverallPercentage(),
                        s.getOverallGrade(),
                        s.getSgpa(),
                        s.getCgpa(),
                        s.isPass() ? "PASS" : "FAIL"
                })
                .toList();
    }

    public static final String[] EXCEL_HEADERS = {
            "Rank", "Roll No", "Name", "Section", "Marks Obtained", "Max Marks", "Percentage", "Grade", "SGPA", "CGPA", "Result"
    };
}
