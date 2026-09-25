<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="isEdit" value="${not empty editingCourse}" scope="request"/>
<c:set var="pageTitle" value="${isEdit ? 'Edit Course' : 'Add Course'}" scope="request"/>
<c:set var="pageSubtitle" value="Academic Setup" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<div class="panel" style="max-width:480px;">
    <c:if test="${not empty errorMessage}">
        <div class="auth-error" role="alert" style="margin-bottom:var(--space-6);"><c:out value="${errorMessage}"/></div>
    </c:if>
    <form method="post" action="${ctx}/admin/academic-setup/courses">
        <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
        <c:if test="${isEdit}"><input type="hidden" name="id" value="${editingCourse.id}"></c:if>

        <div class="field">
            <label for="departmentId">Department</label>
            <select id="departmentId" name="departmentId" required>
                <option value="">Select a department&hellip;</option>
                <c:forEach items="${departments}" var="dept">
                    <option value="${dept.id}" ${(isEdit && editingCourse.department.id == dept.id) || param.departmentId == dept.id.toString() ? 'selected' : ''}>
                        <c:out value="${dept.name}"/>
                    </option>
                </c:forEach>
            </select>
        </div>
        <div class="field">
            <label for="name">Name</label>
            <input type="text" id="name" name="name" required value="<c:out value='${isEdit ? editingCourse.name : param.name}'/>">
            <c:if test="${not empty fieldErrors.name}"><div style="color:var(--color-danger); font-size:0.8rem; margin-top:4px;"><c:out value="${fieldErrors.name}"/></div></c:if>
        </div>
        <div class="field">
            <label for="code">Code</label>
            <input type="text" id="code" name="code" required maxlength="20" style="text-transform:uppercase;" value="<c:out value='${isEdit ? editingCourse.code : param.code}'/>">
            <c:if test="${not empty fieldErrors.code}"><div style="color:var(--color-danger); font-size:0.8rem; margin-top:4px;"><c:out value="${fieldErrors.code}"/></div></c:if>
        </div>
        <div class="field">
            <label for="totalSemesters">Total semesters</label>
            <input type="number" id="totalSemesters" name="totalSemesters" min="1" max="12" required value="<c:out value='${isEdit ? editingCourse.totalSemesters : (empty param.totalSemesters ? 8 : param.totalSemesters)}'/>">
            <c:if test="${not empty fieldErrors.totalSemesters}"><div style="color:var(--color-danger); font-size:0.8rem; margin-top:4px;"><c:out value="${fieldErrors.totalSemesters}"/></div></c:if>
        </div>

        <div style="display:flex; gap:var(--space-3); margin-top:var(--space-6);">
            <button type="submit" class="btn-primary" style="width:auto; padding:0.65rem 1.5rem;">${isEdit ? 'Save changes' : 'Add course'}</button>
            <a href="${ctx}/admin/academic-setup/courses" style="align-self:center; color:var(--color-ink-soft); text-decoration:none; font-size:0.9rem;">Cancel</a>
        </div>
    </form>
</div>

<%@ include file="/common/fragments/admin-foot.jspf" %>
