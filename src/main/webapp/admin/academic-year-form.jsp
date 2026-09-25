<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="isEdit" value="${not empty editingYear}" scope="request"/>
<c:set var="pageTitle" value="${isEdit ? 'Edit Academic Year' : 'Add Academic Year'}" scope="request"/>
<c:set var="pageSubtitle" value="Academic Setup" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<div class="panel" style="max-width:480px;">
    <c:if test="${not empty errorMessage}">
        <div class="auth-error" role="alert" style="margin-bottom:var(--space-6);"><c:out value="${errorMessage}"/></div>
    </c:if>
    <form method="post" action="${ctx}/admin/academic-setup/academic-years">
        <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
        <c:if test="${isEdit}"><input type="hidden" name="id" value="${editingYear.id}"></c:if>

        <div class="field">
            <label for="label">Label</label>
            <input type="text" id="label" name="label" placeholder="e.g. 2026-2027" required
                   value="<c:out value='${isEdit ? editingYear.label : param.label}'/>">
            <c:if test="${not empty fieldErrors.label}"><div style="color:var(--color-danger); font-size:0.8rem; margin-top:4px;"><c:out value="${fieldErrors.label}"/></div></c:if>
        </div>
        <div class="field">
            <label for="startDate">Start date</label>
            <%-- HTML date inputs both submit and expect yyyy-MM-dd, which is
                 exactly LocalDate.toString()'s format - no parsing helper needed
                 to round-trip a value back into this field on a validation error. --%>
            <input type="date" id="startDate" name="startDate" required
                   value="<c:out value='${isEdit ? editingYear.startDate : param.startDate}'/>">
            <c:if test="${not empty fieldErrors.startDate}"><div style="color:var(--color-danger); font-size:0.8rem; margin-top:4px;"><c:out value="${fieldErrors.startDate}"/></div></c:if>
        </div>
        <div class="field">
            <label for="endDate">End date</label>
            <input type="date" id="endDate" name="endDate" required
                   value="<c:out value='${isEdit ? editingYear.endDate : param.endDate}'/>">
            <c:if test="${not empty fieldErrors.endDate}"><div style="color:var(--color-danger); font-size:0.8rem; margin-top:4px;"><c:out value="${fieldErrors.endDate}"/></div></c:if>
        </div>

        <div style="display:flex; gap:var(--space-3); margin-top:var(--space-6);">
            <button type="submit" class="btn-primary" style="width:auto; padding:0.65rem 1.5rem;">${isEdit ? 'Save changes' : 'Add academic year'}</button>
            <a href="${ctx}/admin/academic-setup/academic-years" style="align-self:center; color:var(--color-ink-soft); text-decoration:none; font-size:0.9rem;">Cancel</a>
        </div>
    </form>
</div>

<%@ include file="/common/fragments/admin-foot.jspf" %>
