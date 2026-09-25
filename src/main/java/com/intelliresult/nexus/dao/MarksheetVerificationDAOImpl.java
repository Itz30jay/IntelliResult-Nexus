package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.MarksheetVerification;

import java.util.Optional;

public class MarksheetVerificationDAOImpl extends AbstractDAO<MarksheetVerification, Long>
        implements MarksheetVerificationDAO {

    public MarksheetVerificationDAOImpl() {
        super(MarksheetVerification.class);
    }

    @Override
    public Optional<MarksheetVerification> findByStudentAndExam(Long studentId, Long examId) {
        return namedQuery("FROM MarksheetVerification WHERE student.id = :studentId AND exam.id = :examId")
                .setParameter("studentId", studentId)
                .setParameter("examId", examId)
                .uniqueResultOptional();
    }

    @Override
    public Optional<MarksheetVerification> findByToken(String token) {
        return namedQuery("FROM MarksheetVerification WHERE verificationToken = :token")
                .setParameter("token", token)
                .uniqueResultOptional();
    }
}
