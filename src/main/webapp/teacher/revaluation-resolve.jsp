<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Evaluate Request" scope="request"/>
<c:set var="pageSubtitle" value="${revaluationRequest.student.rollNo} &mdash; ${revaluationRequest.result.subject.subjectCode}" scope="request"/>
<%@ include file="/common/fragments/teacher-head.jspf" %>

<%--
    New page (upgrade pass) - Sec. "Teacher evaluates and submits: New
    marks, Detailed re-evaluation report." Marks fields are pre-filled with
    the result's CURRENT values (not blank) so approving without any real
    change is the natural "don't touch these" path, and RevaluationService.
    resolveRequest only actually corrects the result when at least one
    value genuinely differs from what's already here.
--%>
<c:if test="${not empty errorMessage}"><div class="auth-error" role="alert" style="margin-bottom:var(--space-6);"><c:out value="${errorMessage}"/></div></c:if>

<div class="panel" style="max-width:640px;">
    <h2 style="margin:0 0 var(--space-4); font-family:var(--font-display);">Request</h2>
    <div class="field"><label>Student</label><div class="field-hint" style="padding:0.3rem 0;"><c:out value="${revaluationRequest.student.user.fullName}"/> (<c:out value="${revaluationRequest.student.rollNo}"/>)</div></div>
    <div class="field"><label>Exam / Subject</label><div class="field-hint" style="padding:0.3rem 0;"><c:out value="${revaluationRequest.result.exam.name}"/> &mdash; <c:out value="${revaluationRequest.result.subject.subjectCode}"/> (<c:out value="${revaluationRequest.result.subject.subjectName}"/>)</div></div>
    <div class="field"><label>Student's reason</label><div class="field-hint" style="padding:0.3rem 0;"><c:out value="${revaluationRequest.reason}"/></div></div>
    <c:if test="${not empty revaluationRequest.adminRemark}"><div class="field"><label>Admin's note</label><div class="field-hint" style="padding:0.3rem 0;"><c:out value="${revaluationRequest.adminRemark}"/></div></div></c:if>
</div>

<div class="panel" style="max-width:640px; margin-top:var(--space-6);">
    <h2 style="margin:0 0 var(--space-2); font-family:var(--font-display);">Your evaluation</h2>
    <form method="post" action="${ctx}/teacher/revaluations/resolve">
        <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
        <input type="hidden" name="requestId" value="${revaluationRequest.id}">

        <div style="display:flex; gap:var(--space-3);">
            <c:if test="${not empty revaluationRequest.result.theoryMarks}">
            <div class="field" style="flex:1;">
                <label for="theoryMarks">Theory</label>
                <input type="number" id="theoryMarks" name="theoryMarks" step="0.01" min="0" value="${revaluationRequest.result.theoryMarks}">
            </div>
            </c:if>
            <c:if test="${not empty revaluationRequest.result.practicalMarks}">
            <div class="field" style="flex:1;">
                <label for="practicalMarks">Practical</label>
                <input type="number" id="practicalMarks" name="practicalMarks" step="0.01" min="0" value="${revaluationRequest.result.practicalMarks}">
            </div>
            </c:if>
            <c:if test="${not empty revaluationRequest.result.internalMarks}">
            <div class="field" style="flex:1;">
                <label for="internalMarks">Internal</label>
                <input type="number" id="internalMarks" name="internalMarks" step="0.01" min="0" value="${revaluationRequest.result.internalMarks}">
            </div>
            </c:if>
        </div>
        <div class="field-hint" style="margin-bottom:var(--space-4);">Leave values as they are if you're confirming the original marks were correct - marks are only updated when you change one of them and Approve.</div>

        <div class="field">
            <label for="teacherReport">Detailed re-evaluation report</label>
            <textarea id="teacherReport" name="teacherReport" rows="4" required placeholder="Explain what you checked and what you found - this is shared with the student and admin."></textarea>
        </div>

        <div style="display:flex; gap:var(--space-3); margin-top:var(--space-4);">
            <button type="submit" name="outcome" value="APPROVED" class="btn-primary" style="width:auto; padding:0.65rem 1.5rem;">Approve</button>
            <button type="submit" name="outcome" value="REJECTED" class="btn-secondary" style="width:auto; padding:0.65rem 1.5rem; border-color:var(--color-danger); color:var(--color-danger);">Reject</button>
        </div>
    </form>
</div>

<div style="margin-top:var(--space-6);">
    <a href="${ctx}/teacher/revaluations" style="color:var(--color-ink-soft); text-decoration:none; font-size:0.9rem;">&larr; Back to Re-evaluations</a>
</div>

<%@ include file="/common/fragments/teacher-foot.jspf" %>
