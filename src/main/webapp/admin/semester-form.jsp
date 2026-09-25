<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="isEdit" value="${not empty editingSemester}" scope="request"/>
<c:set var="pageTitle" value="${isEdit ? 'Edit Semester' : 'Add Semester'}" scope="request"/>
<c:set var="pageSubtitle" value="Academic Setup" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<div class="panel" style="max-width:480px;">
    <c:if test="${not empty errorMessage}">
        <div class="auth-error" role="alert" style="margin-bottom:var(--space-6);"><c:out value="${errorMessage}"/></div>
    </c:if>
    <form method="post" action="${ctx}/admin/academic-setup/semesters">
        <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">

        <c:choose>
            <c:when test="${isEdit}">
                <input type="hidden" name="id" value="${editingSemester.id}">
                <div class="field">
                    <label>Course &amp; Academic Year</label>
                    <div style="padding:0.5rem 0; color:var(--color-ink-soft); font-size:0.9rem;">
                        Semester <c:out value="${editingSemester.semesterNumber}"/> &mdash; <c:out value="${editingSemester.course.code}"/>, <c:out value="${editingSemester.academicYear.label}"/>
                        <br><small>Fixed once created - see Phase 5c decisions doc for why.</small>
                    </div>
                </div>
            </c:when>
            <c:otherwise>
                <div class="field">
                    <label for="courseId">Course</label>
                    <select id="courseId" name="courseId" required>
                        <option value="">Select a course&hellip;</option>
                        <c:forEach items="${courses}" var="course">
                            <option value="${course.id}" ${param.courseId == course.id.toString() ? 'selected' : ''}><c:out value="${course.name}"/> (<c:out value="${course.code}"/>)</option>
                        </c:forEach>
                    </select>
                </div>
                <div class="field">
                    <label for="academicYearId">Academic Year</label>
                    <select id="academicYearId" name="academicYearId" required>
                        <option value="">Select an academic year&hellip;</option>
                        <c:forEach items="${academicYears}" var="year">
                            <option value="${year.id}" ${param.academicYearId == year.id.toString() ? 'selected' : ''}><c:out value="${year.label}"/></option>
                        </c:forEach>
                    </select>
                </div>
                <div class="field">
                    <label for="semesterNumber">Semester number</label>
                    <input type="number" id="semesterNumber" name="semesterNumber" min="1" max="12" required value="<c:out value='${param.semesterNumber}'/>">
                    <c:if test="${not empty fieldErrors.semesterNumber}"><div style="color:var(--color-danger); font-size:0.8rem; margin-top:4px;"><c:out value="${fieldErrors.semesterNumber}"/></div></c:if>
                </div>
            </c:otherwise>
        </c:choose>

        <div class="field">
            <label for="startDate">Start date <span style="font-weight:400; color:var(--color-ink-soft);">(optional)</span></label>
            <input type="date" id="startDate" name="startDate" value="<c:out value='${isEdit ? editingSemester.startDate : param.startDate}'/>">
        </div>
        <div class="field">
            <label for="endDate">End date <span style="font-weight:400; color:var(--color-ink-soft);">(optional)</span></label>
            <input type="date" id="endDate" name="endDate" value="<c:out value='${isEdit ? editingSemester.endDate : param.endDate}'/>">
        </div>

        <div style="display:flex; gap:var(--space-3); margin-top:var(--space-6);">
            <button type="submit" class="btn-primary" style="width:auto; padding:0.65rem 1.5rem;">${isEdit ? 'Save changes' : 'Add semester'}</button>
            <a href="${ctx}/admin/academic-setup/semesters" style="align-self:center; color:var(--color-ink-soft); text-decoration:none; font-size:0.9rem;">Cancel</a>
        </div>
    </form>
</div>

<%@ include file="/common/fragments/admin-foot.jspf" %>
