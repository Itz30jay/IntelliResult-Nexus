<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="isEdit" value="${not empty editingDepartment}" scope="request"/>
<c:set var="pageTitle" value="${isEdit ? 'Edit Department' : 'Add Department'}" scope="request"/>
<c:set var="pageSubtitle" value="Academic Setup" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<div class="panel" style="max-width:480px;">
    <c:if test="${not empty errorMessage}">
        <div class="auth-error" role="alert" style="margin-bottom:var(--space-6);"><c:out value="${errorMessage}"/></div>
    </c:if>
    <form method="post" action="${ctx}/admin/academic-setup/departments">
        <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
        <c:if test="${isEdit}"><input type="hidden" name="id" value="${editingDepartment.id}"></c:if>

        <div class="field">
            <label for="name">Name</label>
            <input type="text" id="name" name="name" required value="<c:out value='${isEdit ? editingDepartment.name : param.name}'/>">
            <c:if test="${not empty fieldErrors.name}"><div style="color:var(--color-danger); font-size:0.8rem; margin-top:4px;"><c:out value="${fieldErrors.name}"/></div></c:if>
        </div>
        <div class="field">
            <label for="code">Code</label>
            <input type="text" id="code" name="code" required maxlength="20" style="text-transform:uppercase;" value="<c:out value='${isEdit ? editingDepartment.code : param.code}'/>">
            <c:if test="${not empty fieldErrors.code}"><div style="color:var(--color-danger); font-size:0.8rem; margin-top:4px;"><c:out value="${fieldErrors.code}"/></div></c:if>
        </div>

        <div style="display:flex; gap:var(--space-3); margin-top:var(--space-6);">
            <button type="submit" class="btn-primary" style="width:auto; padding:0.65rem 1.5rem;">${isEdit ? 'Save changes' : 'Add department'}</button>
            <a href="${ctx}/admin/academic-setup/departments" style="align-self:center; color:var(--color-ink-soft); text-decoration:none; font-size:0.9rem;">Cancel</a>
        </div>
    </form>
</div>

<%@ include file="/common/fragments/admin-foot.jspf" %>
