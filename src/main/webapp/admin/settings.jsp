<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Settings" scope="request"/>
<c:set var="pageSubtitle" value="System Settings" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<div class="panel" style="max-width:560px;">
    <c:if test="${not empty errorMessage}">
        <div class="auth-error" role="alert"><c:out value="${errorMessage}"/></div>
    </c:if>

    <p class="field-hint" style="margin-bottom:var(--space-6);">
        Institution identity and marksheet branding, applied wherever the system prints an official document
        (digital marksheets - Phase 12; reports - Phase 15). Not covered here: academic year (set from
        <a href="${ctx}/admin/academic-setup">Academic Setup</a>), grading scale (set from
        <a href="${ctx}/admin/grading-rules">Grading Rules</a>) - see PHASE5F decisions doc for why those
        deliberately aren't duplicated on this page.
    </p>

    <form method="post" action="${ctx}/admin/settings">
        <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">

        <div class="field">
            <label for="institutionName">Institution name</label>
            <input type="text" id="institutionName" name="institutionName" required maxlength="200" value="<c:out value='${settings.institutionName}'/>">
            <c:if test="${not empty fieldErrors.institutionName}"><div class="field-error"><c:out value="${fieldErrors.institutionName}"/></div></c:if>
        </div>

        <div class="field">
            <label for="institutionAddress">Institution address <span style="font-weight:400; color:var(--color-ink-soft);">(optional)</span></label>
            <input type="text" id="institutionAddress" name="institutionAddress" maxlength="300" value="<c:out value='${settings.institutionAddress}'/>">
            <c:if test="${not empty fieldErrors.institutionAddress}"><div class="field-error"><c:out value="${fieldErrors.institutionAddress}"/></div></c:if>
        </div>

        <div class="field">
            <label for="institutionLogoPath">Institution logo path <span style="font-weight:400; color:var(--color-ink-soft);">(optional)</span></label>
            <input type="text" id="institutionLogoPath" name="institutionLogoPath" maxlength="500" placeholder="/assets/images/logo.png" value="<c:out value='${settings.institutionLogoPath}'/>">
            <div class="field-hint">A path or URL to an already-uploaded logo image - this field doesn't upload a file itself.</div>
            <c:if test="${not empty fieldErrors.institutionLogoPath}"><div class="field-error"><c:out value="${fieldErrors.institutionLogoPath}"/></div></c:if>
        </div>

        <div class="field">
            <label for="signatoryName">Authorized signatory name <span style="font-weight:400; color:var(--color-ink-soft);">(optional)</span></label>
            <input type="text" id="signatoryName" name="signatoryName" maxlength="150" value="<c:out value='${settings.signatoryName}'/>">
            <c:if test="${not empty fieldErrors.signatoryName}"><div class="field-error"><c:out value="${fieldErrors.signatoryName}"/></div></c:if>
        </div>

        <div class="field">
            <label for="signatoryDesignation">Signatory designation <span style="font-weight:400; color:var(--color-ink-soft);">(optional)</span></label>
            <input type="text" id="signatoryDesignation" name="signatoryDesignation" maxlength="150" placeholder="Controller of Examinations" value="<c:out value='${settings.signatoryDesignation}'/>">
            <div class="field-hint">Printed above the signature line on digital marksheets (Sec. 19).</div>
            <c:if test="${not empty fieldErrors.signatoryDesignation}"><div class="field-error"><c:out value="${fieldErrors.signatoryDesignation}"/></div></c:if>
        </div>

        <button type="submit" class="btn-primary" style="width:auto; padding:0.65rem 1.5rem; margin-top:var(--space-4);">Save settings</button>
    </form>

    <p class="field-hint" style="margin-top:var(--space-6); padding-top:var(--space-4); border-top:1px solid var(--color-rule);">
        Last updated <c:out value="${settings.updatedAt}"/>.
    </p>
</div>

<script>
    <c:if test="${not empty successMessage}">toastr.success('<c:out value="${successMessage}"/>');</c:if>
</script>
<%@ include file="/common/fragments/admin-foot.jspf" %>
