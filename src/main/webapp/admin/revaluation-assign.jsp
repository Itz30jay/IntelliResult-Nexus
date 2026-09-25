<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="Re-evaluation Request" scope="request"/>
<c:set var="pageSubtitle" value="${revaluationRequest.student.rollNo} &mdash; ${revaluationRequest.result.subject.subjectCode}" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<%--
    Upgrade: replaces the old revaluation-resolve.jsp, which let an admin
    decide the outcome and enter corrected marks directly. Sec. "Admin
    receives it and assigns it to any teacher" moved that decision to the
    assigned teacher (see /teacher/revaluations) - an admin's only action
    on a PENDING request here is choosing who evaluates it. Once ASSIGNED
    or resolved, this page becomes read-only, showing exactly what the
    assigned teacher has done (or not yet done) with it.
--%>
<c:if test="${not empty errorMessage}">
    <div class="auth-error" role="alert" style="margin-bottom:var(--space-6);"><c:out value="${errorMessage}"/></div>
</c:if>

<div class="panel" style="max-width:640px;">
    <h2 style="margin:0 0 var(--space-4); font-family:var(--font-display);">Request details</h2>
    <div class="field"><label>Student</label><div class="field-hint" style="padding:0.3rem 0;"><c:out value="${revaluationRequest.student.user.fullName}"/> (<c:out value="${revaluationRequest.student.rollNo}"/>)</div></div>
    <div class="field"><label>Exam / Subject</label><div class="field-hint" style="padding:0.3rem 0;"><c:out value="${revaluationRequest.result.exam.name}"/> &mdash; <c:out value="${revaluationRequest.result.subject.subjectCode}"/></div></div>
    <div class="field"><label>Current marks</label><div class="field-hint" style="padding:0.3rem 0;">Theory: <c:out value="${revaluationRequest.result.theoryMarks}"/> &nbsp; Practical: <c:out value="${revaluationRequest.result.practicalMarks}"/> &nbsp; Internal: <c:out value="${revaluationRequest.result.internalMarks}"/> &nbsp; Total: <c:out value="${revaluationRequest.result.totalMarks}"/></div></div>
    <div class="field"><label>Student's reason</label><div class="field-hint" style="padding:0.3rem 0;"><c:out value="${revaluationRequest.reason}"/></div></div>
    <div class="field"><label>Requested</label><div class="field-hint" style="padding:0.3rem 0;"><c:out value="${revaluationRequest.requestedAt}"/></div></div>
</div>

<c:choose>
<c:when test="${revaluationRequest.status == 'PENDING'}">
<div class="panel" style="max-width:640px; margin-top:var(--space-6);">
    <h2 style="margin:0 0 var(--space-2); font-family:var(--font-display);">Assign to a teacher</h2>
    <p class="field-hint" style="margin-top:0;">Any teacher can be assigned, not only one who teaches this subject - use this to bring in a second opinion if needed.</p>
    <form method="post" action="${ctx}/admin/revaluations/assign">
        <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
        <input type="hidden" name="requestId" value="${revaluationRequest.id}">
        <div class="field">
            <label for="teacherId">Teacher</label>
            <select id="teacherId" name="teacherId" required>
                <option value="">Select a teacher&hellip;</option>
                <c:forEach items="${teachers}" var="t">
                    <option value="${t.id}"><c:out value="${t.user.fullName}"/> (<c:out value="${t.department.name}"/>)</option>
                </c:forEach>
            </select>
        </div>
        <div class="field">
            <label for="adminRemark">Note to teacher <span style="font-weight:400; color:var(--color-ink-soft);">(optional)</span></label>
            <textarea id="adminRemark" name="adminRemark" rows="2" placeholder="Any context or instructions for the evaluator"></textarea>
        </div>
        <button type="submit" class="btn-primary" style="width:auto; padding:0.65rem 1.5rem;">Assign</button>
    </form>
</div>
</c:when>
<c:otherwise>
<div class="panel" style="max-width:640px; margin-top:var(--space-6);">
    <h2 style="margin:0 0 var(--space-4); font-family:var(--font-display);">Assignment</h2>
    <div class="field"><label>Assigned to</label><div class="field-hint" style="padding:0.3rem 0;"><c:out value="${revaluationRequest.assignedTeacher.user.fullName}"/> on <c:out value="${revaluationRequest.assignedAt}"/></div></div>
    <c:if test="${not empty revaluationRequest.adminRemark}"><div class="field"><label>Note to teacher</label><div class="field-hint" style="padding:0.3rem 0;"><c:out value="${revaluationRequest.adminRemark}"/></div></div></c:if>

    <c:if test="${revaluationRequest.status == 'ASSIGNED'}">
        <div class="panel-empty" style="margin-top:var(--space-4);"><p>Waiting on the teacher's evaluation. You'll be notified once it's resolved.</p></div>
    </c:if>
    <c:if test="${revaluationRequest.status == 'APPROVED' || revaluationRequest.status == 'REJECTED'}">
        <div class="field"><label>Outcome</label><div class="field-hint" style="padding:0.3rem 0;">
            <span class="badge badge-status badge-status-${fn:toLowerCase(revaluationRequest.status)}"><c:out value="${revaluationRequest.status}"/></span>
        </div></div>
        <div class="field"><label>Teacher's report</label><div class="field-hint" style="padding:0.3rem 0;"><c:out value="${revaluationRequest.teacherReport}"/></div></div>
        <c:if test="${revaluationRequest.status == 'APPROVED'}">
            <div class="field"><label>Updated marks</label><div class="field-hint" style="padding:0.3rem 0;">Theory: <c:out value="${revaluationRequest.newTheoryMarks}"/> &nbsp; Practical: <c:out value="${revaluationRequest.newPracticalMarks}"/> &nbsp; Internal: <c:out value="${revaluationRequest.newInternalMarks}"/></div></div>
        </c:if>
        <div class="field"><label>Resolved</label><div class="field-hint" style="padding:0.3rem 0;"><c:out value="${revaluationRequest.resolvedAt}"/></div></div>
    </c:if>
</div>
</c:otherwise>
</c:choose>

<div style="margin-top:var(--space-6);">
    <a href="${ctx}/admin/revaluations" style="color:var(--color-ink-soft); text-decoration:none; font-size:0.9rem;">&larr; Back to Re-evaluation Requests</a>
</div>

<%@ include file="/common/fragments/admin-foot.jspf" %>
