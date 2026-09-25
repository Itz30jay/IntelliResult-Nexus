<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="isEdit" value="${not empty editingRule}" scope="request"/>
<c:set var="pageTitle" value="${isEdit ? 'Edit Grading Rule' : 'Add Grading Rule'}" scope="request"/>
<c:set var="pageSubtitle" value="Dynamic Grading Engine" scope="request"/>
<c:set var="backYearId" value="${isEdit ? editingRule.academicYear.id : param.academicYearId}" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<div class="panel" style="max-width:520px;">
    <c:if test="${not empty errorMessage}">
        <div class="auth-error" role="alert"><c:out value="${errorMessage}"/></div>
    </c:if>

    <c:if test="${!isEdit && empty academicYears}">
        <p class="panel-empty">
            No academic years exist yet. Create one under
            <a href="${ctx}/admin/academic-setup">Academic Setup</a> first.
        </p>
    </c:if>

    <c:if test="${isEdit || not empty academicYears}">
    <form method="post" action="${ctx}/admin/grading-rules">
        <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
        <c:if test="${isEdit}"><input type="hidden" name="id" value="${editingRule.id}"></c:if>

        <c:choose>
            <c:when test="${isEdit}">
                <input type="hidden" name="academicYearId" value="${editingRule.academicYear.id}">
                <div class="field">
                    <label>Academic year</label>
                    <div class="field-hint" style="padding:0.4rem 0;">
                        <c:out value="${editingRule.academicYear.label}"/> &mdash; fixed once created, see PHASE5E decisions doc for why.
                    </div>
                </div>
            </c:when>
            <c:otherwise>
                <div class="field">
                    <label for="academicYearId">Academic year</label>
                    <select id="academicYearId" name="academicYearId" required>
                        <option value="">Select an academic year&hellip;</option>
                        <c:forEach items="${academicYears}" var="year">
                            <option value="${year.id}" ${param.academicYearId == year.id.toString() ? 'selected' : ''}>
                                <c:out value="${year.label}"/><c:if test="${year.current}"> (current)</c:if>
                            </option>
                        </c:forEach>
                    </select>
                    <c:if test="${not empty fieldErrors.academicYearId}"><div class="field-error"><c:out value="${fieldErrors.academicYearId}"/></div></c:if>
                </div>
            </c:otherwise>
        </c:choose>

        <div class="field">
            <label for="grade">Grade</label>
            <input type="text" id="grade" name="grade" required maxlength="10" placeholder="e.g. A+, B, F"
                   value="<c:out value='${isEdit ? editingRule.grade : param.grade}'/>">
            <c:if test="${not empty fieldErrors.grade}"><div class="field-error"><c:out value="${fieldErrors.grade}"/></div></c:if>
        </div>

        <div style="display:flex; gap:var(--space-4);">
            <div class="field" style="flex:1;">
                <label for="minPercentage">Minimum %</label>
                <input type="number" id="minPercentage" name="minPercentage" required min="0" max="100" step="0.01"
                       value="<c:out value='${isEdit ? editingRule.minPercentage : param.minPercentage}'/>">
                <c:if test="${not empty fieldErrors.minPercentage}"><div class="field-error"><c:out value="${fieldErrors.minPercentage}"/></div></c:if>
            </div>
            <div class="field" style="flex:1;">
                <label for="maxPercentage">Maximum %</label>
                <input type="number" id="maxPercentage" name="maxPercentage" required min="0" max="100" step="0.01"
                       value="<c:out value='${isEdit ? editingRule.maxPercentage : param.maxPercentage}'/>">
                <c:if test="${not empty fieldErrors.maxPercentage}"><div class="field-error"><c:out value="${fieldErrors.maxPercentage}"/></div></c:if>
            </div>
        </div>
        <div class="field-hint" style="margin-top:-0.5rem; margin-bottom:var(--space-4);">Both bounds are inclusive - a maximum of 59.99 and the next rule's minimum of 60.00 sit next to each other without overlapping.</div>

        <div class="field">
            <label for="gradePoint">Grade point</label>
            <input type="number" id="gradePoint" name="gradePoint" required min="0" step="0.1"
                   value="<c:out value='${isEdit ? editingRule.gradePoint : param.gradePoint}'/>">
            <c:if test="${not empty fieldErrors.gradePoint}"><div class="field-error"><c:out value="${fieldErrors.gradePoint}"/></div></c:if>
        </div>

        <div style="display:flex; gap:var(--space-3); margin-top:var(--space-6);">
            <button type="submit" class="btn-primary" style="width:auto; padding:0.65rem 1.5rem;">${isEdit ? 'Save changes' : 'Add rule'}</button>
            <a href="${ctx}/admin/grading-rules${not empty backYearId ? '?academicYearId=' : ''}${backYearId}" style="align-self:center; color:var(--color-ink-soft); text-decoration:none; font-size:0.9rem;">Cancel</a>
        </div>
    </form>
    </c:if>
</div>

<%@ include file="/common/fragments/admin-foot.jspf" %>
