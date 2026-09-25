package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.Result;
import com.intelliresult.nexus.entity.enums.ResultStatus;
import com.intelliresult.nexus.dao.dto.GradeDistributionDTO;
import com.intelliresult.nexus.dao.dto.StudentPerformanceDTO;
import com.intelliresult.nexus.dao.dto.SubjectAverageDTO;
import com.intelliresult.nexus.dao.dto.SubjectPerformanceDTO;

import java.util.List;
import java.util.Optional;

public class ResultDAOImpl extends AbstractSoftDeletableDAO<Result, Long> implements ResultDAO {

    public ResultDAOImpl() {
        super(Result.class);
    }

    @Override
    public List<Result> findByStudentAndExam(Long studentId, Long examId) {
        return namedQuery("FROM Result WHERE student.id = :studentId AND exam.id = :examId AND deleted = false")
                .setParameter("studentId", studentId)
                .setParameter("examId", examId)
                .getResultList();
    }

    @Override
    public List<Result> findByExam(Long examId) {
        return namedQuery("FROM Result WHERE exam.id = :examId AND deleted = false")
                .setParameter("examId", examId)
                .getResultList();
    }

    @Override
    public Optional<Result> findByStudentExamSubject(Long studentId, Long examId, Long subjectId) {
        return namedQuery("FROM Result WHERE student.id = :studentId AND exam.id = :examId "
                        + "AND subject.id = :subjectId AND deleted = false")
                .setParameter("studentId", studentId)
                .setParameter("examId", examId)
                .setParameter("subjectId", subjectId)
                .uniqueResultOptional();
    }

    @Override
    public List<Result> findByExamAndSubject(Long examId, Long subjectId) {
        return namedQuery("FROM Result WHERE exam.id = :examId AND subject.id = :subjectId AND deleted = false "
                        + "ORDER BY student.rollNo")
                .setParameter("examId", examId)
                .setParameter("subjectId", subjectId)
                .getResultList();
    }

    @Override
    public List<Result> findPublishedByStudent(Long studentId) {
        return namedQuery("FROM Result WHERE student.id = :studentId AND status = :status AND deleted = false")
                .setParameter("studentId", studentId)
                .setParameter("status", ResultStatus.PUBLISHED)
                .getResultList();
    }

    @Override
    public List<Result> findVisibleToStudent(Long studentId) {
        return namedQuery("FROM Result WHERE student.id = :studentId AND status IN :statuses AND deleted = false "
                        + "ORDER BY exam.startTime DESC")
                .setParameter("studentId", studentId)
                .setParameter("statuses", List.of(ResultStatus.PUBLISHED, ResultStatus.LOCKED))
                .getResultList();
    }

    @Override
    public List<Result> findByExamAndStatus(Long examId, ResultStatus status) {
        return namedQuery("FROM Result WHERE exam.id = :examId AND status = :status AND deleted = false")
                .setParameter("examId", examId)
                .setParameter("status", status)
                .getResultList();
    }

    @Override
    public List<Result> findBySubmittedByAndStatus(Long userId, ResultStatus status) {
        return namedQuery("FROM Result WHERE submittedBy.id = :userId AND status = :status AND deleted = false "
                        + "ORDER BY submittedAt DESC")
                .setParameter("userId", userId)
                .setParameter("status", status)
                .getResultList();
    }

    @Override
    public long countByStatus(ResultStatus status) {
        return session()
                .createQuery("SELECT COUNT(r) FROM Result r WHERE r.status = :status AND r.deleted = false", Long.class)
                .setParameter("status", status)
                .getSingleResult();
    }

    @Override
    public long countPublishedByPass(boolean isPass) {
        return session()
                .createQuery("SELECT COUNT(r) FROM Result r WHERE r.status = com.intelliresult.nexus.entity.enums.ResultStatus.PUBLISHED "
                                + "AND r.pass = :isPass AND r.deleted = false", Long.class)
                .setParameter("isPass", isPass)
                .getSingleResult();
    }

    @Override
    public List<SubjectPerformanceDTO> subjectPerformance() {
        return session()
                .createQuery("SELECT new com.intelliresult.nexus.dao.dto.SubjectPerformanceDTO("
                                + "s.subjectCode, s.subjectName, AVG(r.percentage)) "
                                + "FROM Result r JOIN r.subject s "
                                + "WHERE r.status = com.intelliresult.nexus.entity.enums.ResultStatus.PUBLISHED AND r.deleted = false "
                                + "GROUP BY s.subjectCode, s.subjectName ORDER BY s.subjectCode",
                        SubjectPerformanceDTO.class)
                .getResultList();
    }

    @Override
    public List<GradeDistributionDTO> gradeDistribution() {
        return session()
                .createQuery("SELECT new com.intelliresult.nexus.dao.dto.GradeDistributionDTO(r.grade, COUNT(r)) "
                                + "FROM Result r "
                                + "WHERE r.status = com.intelliresult.nexus.entity.enums.ResultStatus.PUBLISHED AND r.deleted = false "
                                + "GROUP BY r.grade",
                        GradeDistributionDTO.class)
                .getResultList();
    }

    @Override
    public List<StudentPerformanceDTO> topPerformers(int limit) {
        return session()
                .createQuery("SELECT new com.intelliresult.nexus.dao.dto.StudentPerformanceDTO("
                                + "st.id, u.fullName, st.rollNo, AVG(r.percentage)) "
                                + "FROM Result r JOIN r.student st JOIN st.user u "
                                + "WHERE r.status = com.intelliresult.nexus.entity.enums.ResultStatus.PUBLISHED AND r.deleted = false "
                                + "GROUP BY st.id, u.fullName, st.rollNo "
                                + "ORDER BY AVG(r.percentage) DESC",
                        StudentPerformanceDTO.class)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    public List<Long> findDistinctStudentIdsByExam(Long examId) {
        return session()
                .createQuery("SELECT DISTINCT r.student.id FROM Result r WHERE r.exam.id = :examId AND r.deleted = false",
                        Long.class)
                .setParameter("examId", examId)
                .getResultList();
    }

    @Override
    public List<SubjectAverageDTO> subjectAveragesForExamSection(Long examId, Long sectionId) {
        return session()
                .createQuery("SELECT new com.intelliresult.nexus.dao.dto.SubjectAverageDTO("
                                + "sub.id, sub.subjectCode, sub.subjectName, AVG(r.percentage)) "
                                + "FROM Result r JOIN r.subject sub "
                                + "WHERE r.exam.id = :examId AND r.student.currentSection.id = :sectionId "
                                + "AND r.status IN (com.intelliresult.nexus.entity.enums.ResultStatus.PUBLISHED, "
                                + "com.intelliresult.nexus.entity.enums.ResultStatus.LOCKED) AND r.deleted = false "
                                + "GROUP BY sub.id, sub.subjectCode, sub.subjectName ORDER BY sub.subjectCode",
                        SubjectAverageDTO.class)
                .setParameter("examId", examId)
                .setParameter("sectionId", sectionId)
                .getResultList();
    }

    @Override
    public List<Result> findCalculatedByStudent(Long studentId) {
        return namedQuery("FROM Result WHERE student.id = :studentId AND percentage IS NOT NULL AND deleted = false "
                        + "ORDER BY subject.id ASC, exam.startTime ASC")
                .setParameter("studentId", studentId)
                .getResultList();
    }
}
