<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Review Registration" scope="request"/>
<c:set var="pageSubtitle" value="${reviewing.fullName} &mdash; ${reviewing.role}" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<c:if test="${not empty errorMessage}">
    <div class="auth-error" role="alert" style="margin-bottom:var(--space-6);"><c:out value="${errorMessage}"/></div>
</c:if>

<div class="panel" style="max-width:640px;">
    <h2 style="margin:0 0 var(--space-4); font-family:var(--font-display);">Submission details</h2>
    <div class="field"><label>Full name</label><div class="field-hint" style="padding:0.3rem 0;"><c:out value="${reviewing.fullName}"/></div></div>
    <div class="field"><label>${reviewing.role == 'TEACHER' ? 'Employee ID' : 'Roll number'} (login ID)</label><div class="field-hint" style="padding:0.3rem 0;"><code><c:out value="${reviewing.identifier}"/></code></div></div>
    <div class="field"><label>Phone</label><div class="field-hint" style="padding:0.3rem 0;"><c:out value="${reviewing.phone}"/></div></div>
    <div class="field"><label>Email</label><div class="field-hint" style="padding:0.3rem 0;"><c:out value="${not empty reviewing.email ? reviewing.email : 'Not provided'}"/></div></div>
    <div class="field"><label>Submitted</label><div class="field-hint" style="padding:0.3rem 0;"><c:out value="${reviewing.submittedAt}"/></div></div>
</div>

<div class="panel" style="max-width:640px; margin-top:var(--space-6);">
    <h2 style="margin:0 0 var(--space-2); font-family:var(--font-display);">Approve</h2>
    <p class="field-hint" style="margin-top:0;">
        <c:choose>
            <c:when test="${reviewing.role == 'TEACHER'}">Assign the department this teacher belongs to. Their account is created immediately and they can log in with the password they chose.</c:when>
            <c:otherwise>Assign the course (and, if known, section) this student belongs to. Their account is created immediately and they can log in with the password they chose.</c:otherwise>
        </c:choose>
    </p>
    <form method="post" action="${ctx}/admin/registrations/approve">
        <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
        <input type="hidden" name="id" value="${reviewing.id}">
        <input type="hidden" name="role" value="${reviewing.role}">

        <c:choose>
            <c:when test="${reviewing.role == 'TEACHER'}">
                <div class="field">
                    <label for="departmentId">Department</label>
                    <select id="departmentId" name="departmentId" required>
                        <option value="">Select a department&hellip;</option>
                        <c:forEach items="${departments}" var="dept">
                            <option value="${dept.id}"><c:out value="${dept.name}"/></option>
                        </c:forEach>
                    </select>
                </div>
                <div class="field">
                    <label for="designation">Designation <span style="font-weight:400; color:var(--color-ink-soft);">(optional)</span></label>
                    <input type="text" id="designation" name="designation" placeholder="e.g. Assistant Professor">
                </div>
            </c:when>
            <c:otherwise>
                <div class="field">
                    <label for="courseId">Course</label>
                    <select id="courseId" name="courseId" required>
                        <option value="">Select a course&hellip;</option>
                        <c:forEach items="${courses}" var="course">
                            <option value="${course.id}"><c:out value="${course.name}"/> (<c:out value="${course.code}"/>)</option>
                        </c:forEach>
                    </select>
                </div>
                <div class="field">
                    <label for="sectionId">Section <span style="font-weight:400; color:var(--color-ink-soft);">(optional - can be assigned later)</span></label>
                    <select id="sectionId" name="sectionId">
                        <option value="">No section yet</option>
                        <c:forEach items="${sections}" var="section">
                            <option value="${section.id}"><c:out value="${section.name}"/> &mdash; Sem <c:out value="${section.semester.semesterNumber}"/> <c:out value="${section.semester.course.code}"/> (<c:out value="${section.semester.academicYear.label}"/>)</option>
                        </c:forEach>
                    </select>
                </div>
            </c:otherwise>
        </c:choose>

        <button type="submit" class="btn-primary" style="width:auto; padding:0.65rem 1.5rem; margin-top:var(--space-4);">Approve &amp; create account</button>
    </form>
</div>

<div class="panel" style="max-width:640px; margin-top:var(--space-6); border-color:var(--color-danger);">
    <h2 style="margin:0 0 var(--space-2); font-family:var(--font-display); color:var(--color-danger);">Reject</h2>
    <p class="field-hint" style="margin-top:0;">No account is created. The applicant is told the reason below (if they provided an email) and may submit a new registration at any time.</p>
    <form method="post" action="${ctx}/admin/registrations/reject" onsubmit="return confirm('Reject this registration? This cannot be undone.');">
        <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
        <input type="hidden" name="id" value="${reviewing.id}">
        <div class="field">
            <label for="remark">Reason</label>
            <textarea id="remark" name="remark" rows="3" required placeholder="e.g. Roll number could not be verified against enrollment records."></textarea>
        </div>
        <button type="submit" class="btn-secondary" style="width:auto; padding:0.65rem 1.5rem; border-color:var(--color-danger); color:var(--color-danger);">Reject request</button>
    </form>
</div>

<div style="margin-top:var(--space-6);">
    <a href="${ctx}/admin/registrations" style="color:var(--color-ink-soft); text-decoration:none; font-size:0.9rem;">&larr; Back to Registrations</a>
</div>

<%@ include file="/common/fragments/admin-foot.jspf" %>
