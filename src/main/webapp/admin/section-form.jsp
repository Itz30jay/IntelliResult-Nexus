<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="isEdit" value="${not empty editingSection}" scope="request"/>
<c:set var="pageTitle" value="${isEdit ? 'Edit Section' : 'Add Section'}" scope="request"/>
<c:set var="pageSubtitle" value="Academic Setup" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<div class="panel" style="max-width:480px;">
    <c:if test="${not empty errorMessage}">
        <div class="auth-error" role="alert" style="margin-bottom:var(--space-6);"><c:out value="${errorMessage}"/></div>
    </c:if>
    <form method="post" action="${ctx}/admin/academic-setup/sections">
        <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
        <c:if test="${isEdit}"><input type="hidden" name="id" value="${editingSection.id}"></c:if>

        <c:choose>
            <c:when test="${isEdit}">
                <div class="field">
                    <label>Semester</label>
                    <div style="padding:0.5rem 0; color:var(--color-ink-soft); font-size:0.9rem;">
                        Semester <c:out value="${editingSection.semester.semesterNumber}"/> &mdash; <c:out value="${editingSection.semester.course.code}"/> (<c:out value="${editingSection.semester.academicYear.label}"/>)
                    </div>
                </div>
            </c:when>
            <c:otherwise>
                <div class="field">
                    <label for="semesterId">Semester</label>
                    <select id="semesterId" name="semesterId" required>
                        <option value="">Select a semester&hellip;</option>
                        <c:forEach items="${semesters}" var="sem">
                            <option value="${sem.id}" ${param.semesterId == sem.id.toString() ? 'selected' : ''}>
                                Sem <c:out value="${sem.semesterNumber}"/> &mdash; <c:out value="${sem.course.code}"/> (<c:out value="${sem.academicYear.label}"/>)
                            </option>
                        </c:forEach>
                    </select>
                </div>
            </c:otherwise>
        </c:choose>

        <div class="field">
            <label for="name">Section name</label>
            <input type="text" id="name" name="name" placeholder="e.g. A" maxlength="10" required
                   value="<c:out value='${isEdit ? editingSection.name : param.name}'/>">
            <c:if test="${not empty fieldErrors.name}"><div style="color:var(--color-danger); font-size:0.8rem; margin-top:4px;"><c:out value="${fieldErrors.name}"/></div></c:if>
        </div>
        <div class="field">
            <label for="capacity">Capacity <span style="font-weight:400; color:var(--color-ink-soft);">(optional)</span></label>
            <input type="number" id="capacity" name="capacity" min="1"
                   value="<c:out value='${isEdit ? editingSection.capacity : param.capacity}'/>">
        </div>
        <div class="field">
            <label for="sectionType">Section type</label>
            <select id="sectionType" name="sectionType">
                <option value="PERMANENT" ${(isEdit ? editingSection.sectionType : param.sectionType) != 'ONE_TIME' ? 'selected' : ''}>Permanent &mdash; an ongoing cohort</option>
                <option value="ONE_TIME" ${(isEdit ? editingSection.sectionType : param.sectionType) == 'ONE_TIME' ? 'selected' : ''}>One-time &mdash; an ad-hoc batch for a single cycle</option>
            </select>
        </div>

        <div style="display:flex; gap:var(--space-3); margin-top:var(--space-6);">
            <button type="submit" class="btn-primary" style="width:auto; padding:0.65rem 1.5rem;">${isEdit ? 'Save changes' : 'Add section'}</button>
            <a href="${ctx}/admin/academic-setup/sections" style="align-self:center; color:var(--color-ink-soft); text-decoration:none; font-size:0.9rem;">Cancel</a>
        </div>
    </form>
</div>

<%@ include file="/common/fragments/admin-foot.jspf" %>
