package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.RevaluationRequest;
import com.intelliresult.nexus.entity.enums.RevaluationStatus;

import java.util.List;

public interface RevaluationDAO extends GenericDAO<RevaluationRequest, Long> {
    List<RevaluationRequest> findByStudent(Long studentId);

    /** The admin review queue, filtered by status (Sec. 14: "View / Filter requests"). */
    List<RevaluationRequest> findByStatus(RevaluationStatus status);

    /** The assigned teacher's own queue (Sec. "Admin ... assigns it to any teacher" -> that teacher needs to see what landed on their desk) - every request ever assigned to this teacher, any status, so their history (resolved) and active queue (ASSIGNED) both come from one query the servlet can partition in Java. */
    List<RevaluationRequest> findByAssignedTeacher(Long teacherId);
}
