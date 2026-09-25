package com.intelliresult.nexus.service;

import com.intelliresult.nexus.dao.ResultDAO;
import com.intelliresult.nexus.dao.ResultDAOImpl;
import com.intelliresult.nexus.dao.ResultSummaryDAO;
import com.intelliresult.nexus.dao.ResultSummaryDAOImpl;
import com.intelliresult.nexus.dao.StudentDAO;
import com.intelliresult.nexus.dao.StudentDAOImpl;
import com.intelliresult.nexus.dao.TeacherSubjectDAO;
import com.intelliresult.nexus.dao.TeacherSubjectDAOImpl;
import com.intelliresult.nexus.dao.dto.SubjectAverageDTO;
import com.intelliresult.nexus.entity.Result;
import com.intelliresult.nexus.entity.ResultSummary;
import com.intelliresult.nexus.entity.Student;
import com.intelliresult.nexus.service.dto.SubjectComparison;
import com.intelliresult.nexus.service.dto.SubjectInsight;
import com.intelliresult.nexus.util.GradeUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sec. 15's Comparative Performance Analytics and Sec. 16's Subject
 * Strength &amp; Weakness Analysis. Read-only throughout - no
 * {@code inTransaction} wrapper anywhere in this class, matching
 * {@code DashboardService}'s own established precedent that a service
 * doing nothing but SELECTs doesn't open one (Hibernate's session-per-
 * request already gives each read a consistent view; there is nothing here
 * for a transaction boundary to make atomic).
 * <p>
 * Every comparison method takes the querying student's own section
 * explicitly rather than re-deriving it - Sec. 15's "never expose
 * sensitive individual student information... use aggregate statistics" is
 * enforced one layer down, in {@code ResultSummaryDAO.classAveragePercentage}/
 * {@code topperPercentage}/{@code ResultDAO.subjectAveragesForExamSection}
 * themselves (each is a single {@code AVG()}/{@code MAX()}, which cannot
 * itself carry any other student's row back to a caller) - this class adds
 * no additional exposure risk on top of that, since it only ever combines
 * an aggregate number with the querying student's *own* already-known data.
 */
public class StudentAnalyticsService {

    private final ResultDAO resultDAO = new ResultDAOImpl();
    private final ResultSummaryDAO resultSummaryDAO = new ResultSummaryDAOImpl();
    private final TeacherSubjectDAO teacherSubjectDAO = new TeacherSubjectDAOImpl();
    private final StudentDAO studentDAO = new StudentDAOImpl();

    /**
     * Upgrade: backs the new {@code /teacher/student-performance} search
     * (Sec. "Add a prominent search bar that works by student name or roll
     * number"). Scoped to students in a section this teacher currently has
     * an active assignment in - a teacher searching "any student in the
     * institution" would be broader access than My Classes/Marks Entry
     * already grant them anywhere else in this system, so this method
     * enforces the same boundary here rather than introducing a new,
     * wider one. An empty query returns every student in scope (the
     * search bar's own empty state - "who could I even search for" - is
     * itself a useful answer, not just a no-op).
     */
    public List<Student> searchStudentsTaughtBy(Long teacherId, String query) {
        Set<Long> sectionIds = teacherSubjectDAO.findActiveByTeacher(teacherId).stream()
                .map(ts -> ts.getSection().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Student> inScope = new ArrayList<>();
        Set<Long> seenStudentIds = new LinkedHashSet<>();
        for (Long sectionId : sectionIds) {
            for (Student student : studentDAO.findBySection(sectionId)) {
                if (seenStudentIds.add(student.getId())) {
                    inScope.add(student);
                }
            }
        }

        if (query == null || query.isBlank()) {
            return inScope;
        }
        String normalized = query.trim().toUpperCase();
        return inScope.stream()
                .filter(s -> s.getRollNo().toUpperCase().contains(normalized)
                        || s.getUser().getFullName().toUpperCase().contains(normalized))
                .toList();
    }

    /** Same section-scoping as {@link #searchStudentsTaughtBy} but for a single already-known id - the detail view's own authorization check (a teacher opening a direct link to a student outside their sections gets "not found," not this student's data). */
    public boolean teachesStudent(Long teacherId, Long studentId) {
        return searchStudentsTaughtBy(teacherId, null).stream().anyMatch(s -> s.getId().equals(studentId));
    }

    /** Sec. 17's "Performance trend" / Sec. 11's "Historical trends" - every exam this student has a summary for, oldest first. */
    public List<ResultSummary> performanceTrend(Long studentId) {
        return resultSummaryDAO.findAllForStudent(studentId);
    }

    /** Sec. 15's "Student vs Class Average" - empty when nobody in this section/exam has a summary yet (Sec. 56's empty state). */
    public Optional<BigDecimal> classAverage(Long examId, Long sectionId) {
        return resultSummaryDAO.classAveragePercentage(examId, sectionId);
    }

    /** Sec. 15's "Student vs Topper." */
    public Optional<BigDecimal> topperScore(Long examId, Long sectionId) {
        return resultSummaryDAO.topperPercentage(examId, sectionId);
    }

    /**
     * Sec. 15's Subject Comparison: every subject the section sat in this
     * exam, the section's average for it, and this student's own score if
     * they have one. Built by joining {@code ResultDAO.
     * subjectAveragesForExamSection} (one AVG() per subject, section-wide)
     * against this student's own {@code Result} rows for the same exam in
     * Java - see {@code SubjectAverageDTO}'s own Javadoc for why not one
     * correlated-subquery HQL statement instead.
     */
    public List<SubjectComparison> subjectComparison(Long studentId, Long examId, Long sectionId) {
        List<SubjectAverageDTO> classAverages = resultDAO.subjectAveragesForExamSection(examId, sectionId);

        Map<Long, BigDecimal> myPercentages = new LinkedHashMap<>();
        for (Result r : resultDAO.findByStudentAndExam(studentId, examId)) {
            if (r.getPercentage() != null) {
                myPercentages.put(r.getSubject().getId(), r.getPercentage());
            }
        }

        List<SubjectComparison> comparisons = new ArrayList<>(classAverages.size());
        for (SubjectAverageDTO avg : classAverages) {
            BigDecimal classAverage = BigDecimal.valueOf(avg.averagePercentage()).setScale(GradeUtil.PERCENTAGE_SCALE, RoundingMode.HALF_UP);
            comparisons.add(new SubjectComparison(avg.subjectCode(), avg.subjectName(),
                    myPercentages.get(avg.subjectId()), classAverage));
        }
        return comparisons;
    }

    /**
     * Sec. 16's Strength &amp; Weakness Analysis: one {@link SubjectInsight}
     * per subject this student has ever had a calculated result in,
     * sorted by average percentage descending - the caller (a controller,
     * not this service - splitting "the front N" vs "the back N" into
     * labeled strength/improvement lists is a display concern, not an
     * analytical one) takes the front as strengths and the back as
     * improvement areas. Grouping and trend arithmetic happen here in Java
     * because they are calculation logic (Sec. 37), not because a database
     * couldn't average a column - {@code ResultDAO.findCalculatedByStudent}
     * already does the one thing a query is well-suited for (fetch,
     * ordered by subject then by exam date) and leaves the rest to this
     * method.
     */
    public List<SubjectInsight> subjectStrengthWeakness(Long studentId) {
        List<Result> calculated = resultDAO.findCalculatedByStudent(studentId);

        Map<Long, List<Result>> bySubject = new LinkedHashMap<>();
        for (Result r : calculated) {
            bySubject.computeIfAbsent(r.getSubject().getId(), k -> new ArrayList<>()).add(r);
        }

        List<SubjectInsight> insights = new ArrayList<>(bySubject.size());
        for (List<Result> subjectResults : bySubject.values()) {
            // findCalculatedByStudent orders by exam.startTime ASC within each subject already,
            // so subjectResults is chronological here without any further sorting.
            var subject = subjectResults.get(0).getSubject();
            BigDecimal average = average(subjectResults.stream().map(Result::getPercentage).toList());

            BigDecimal trend = null;
            if (subjectResults.size() >= 2) {
                BigDecimal mostRecent = subjectResults.get(subjectResults.size() - 1).getPercentage();
                BigDecimal averageOfEarlier = average(subjectResults.subList(0, subjectResults.size() - 1).stream()
                        .map(Result::getPercentage).toList());
                trend = GradeUtil.percentageChange(mostRecent, averageOfEarlier);
            }

            insights.add(new SubjectInsight(subject.getSubjectCode(), subject.getSubjectName(), average, trend, subjectResults.size()));
        }

        insights.sort((a, b) -> b.averagePercentage().compareTo(a.averagePercentage()));
        return insights;
    }

    private BigDecimal average(List<BigDecimal> values) {
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), GradeUtil.PERCENTAGE_SCALE, RoundingMode.HALF_UP);
    }
}
