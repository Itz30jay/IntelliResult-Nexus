<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="isEdit" value="${not empty editingUser}" scope="request"/>
<c:set var="pageTitle" value="${isEdit ? 'Edit Admin' : 'Create Admin'}" scope="request"/>
<c:set var="pageSubtitle" value="${isEdit ? editingUser.email : 'Add another administrator account'}" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<%--
    Upgrade: this form only ever creates/edits ADMIN accounts now - Student
    and Teacher accounts come exclusively through the public Registration +
    verification flow (see /admin/registrations). Validation failures
    forward back to THIS request rather than redirect, so ${param.xxx}
    below still holds whatever the admin typed even after a failed submit.
--%>
<div class="panel" style="max-width:640px;">

    <c:if test="${not empty errorMessage}">
        <div class="auth-error" role="alert" style="margin-bottom:var(--space-6);"><c:out value="${errorMessage}"/></div>
    </c:if>

    <form method="post" action="${ctx}${isEdit ? '/admin/users/edit' : '/admin/users/new'}">
        <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
        <c:if test="${isEdit}"><input type="hidden" name="id" value="${editingUser.id}"></c:if>

        <div class="field">
            <label for="fullName">Full name</label>
            <input type="text" id="fullName" name="fullName" required autofocus
                   value="<c:out value='${isEdit ? editingUser.fullName : param.fullName}'/>">
            <c:if test="${not empty fieldErrors.fullName}"><div class="field-error"><c:out value="${fieldErrors.fullName}"/></div></c:if>
        </div>

        <div class="field">
            <label for="email">Email</label>
            <input type="email" id="email" name="email" required
                   value="<c:out value='${isEdit ? editingUser.email : param.email}'/>">
            <c:if test="${not empty fieldErrors.email}"><div class="field-error"><c:out value="${fieldErrors.email}"/></div></c:if>
        </div>

        <div class="field">
            <label for="phone">Phone <span style="font-weight:400; color:var(--color-ink-soft);">(optional)</span></label>
            <input type="text" id="phone" name="phone"
                   value="<c:out value='${isEdit ? editingUser.phone : param.phone}'/>">
        </div>

        <div style="display:flex; gap:var(--space-3); margin-top:var(--space-6);">
            <button type="submit" class="btn-primary" style="width:auto; padding:0.65rem 1.5rem;">${isEdit ? 'Save changes' : 'Create admin'}</button>
            <a href="${ctx}/admin/users" style="align-self:center; color:var(--color-ink-soft); text-decoration:none; font-size:0.9rem;">Cancel</a>
        </div>
    </form>
</div>

<%@ include file="/common/fragments/admin-foot.jspf" %>
