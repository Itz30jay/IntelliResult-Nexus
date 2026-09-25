<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="isEdit" value="${not empty editingSubject}" scope="request"/>
<c:set var="pageTitle" value="${isEdit ? 'Edit Subject' : 'Add Subject'}" scope="request"/>
<c:set var="pageSubtitle" value="Academic Setup" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<div class="panel" style="max-width:560px;">
    <c:if test="${not empty errorMessage}">
        <div class="auth-error" role="alert" style="margin-bottom:var(--space-6);"><c:out value="${errorMessage}"/></div>
    </c:if>
    <c:if test="${not empty fieldErrors.components}">
        <div class="auth-error" role="alert" style="margin-bottom:var(--space-6);"><c:out value="${fieldErrors.components}"/></div>
    </c:if>

    <form method="post" action="${ctx}/admin/subjects">
        <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
        <c:if test="${isEdit}"><input type="hidden" name="id" value="${editingSubject.id}"></c:if>

        <div class="field">
            <label for="semesterId">Semester</label>
            <select id="semesterId" name="semesterId" required>
                <option value="">Select a semester&hellip;</option>
                <c:forEach items="${semesters}" var="sem">
                    <option value="${sem.id}" ${(isEdit && editingSubject.semester.id == sem.id) || param.semesterId == sem.id.toString() ? 'selected' : ''}>
                        Sem <c:out value="${sem.semesterNumber}"/> &mdash; <c:out value="${sem.course.code}"/> (<c:out value="${sem.academicYear.label}"/>)
                    </option>
                </c:forEach>
            </select>
        </div>
        <div class="field">
            <label for="departmentId">Teaching Department</label>
            <select id="departmentId" name="departmentId" required>
                <option value="">Select a department&hellip;</option>
                <c:forEach items="${departments}" var="dept">
                    <option value="${dept.id}" ${(isEdit && editingSubject.department.id == dept.id) || param.departmentId == dept.id.toString() ? 'selected' : ''}><c:out value="${dept.name}"/></option>
                </c:forEach>
            </select>
        </div>
        <div class="field">
            <label for="subjectCode">Subject code</label>
            <input type="text" id="subjectCode" name="subjectCode" required style="text-transform:uppercase;" value="<c:out value='${isEdit ? editingSubject.subjectCode : param.subjectCode}'/>">
            <c:if test="${not empty fieldErrors.subjectCode}"><div style="color:var(--color-danger); font-size:0.8rem; margin-top:4px;"><c:out value="${fieldErrors.subjectCode}"/></div></c:if>
        </div>
        <div class="field">
            <label for="subjectName">Subject name</label>
            <input type="text" id="subjectName" name="subjectName" required value="<c:out value='${isEdit ? editingSubject.subjectName : param.subjectName}'/>">
            <c:if test="${not empty fieldErrors.subjectName}"><div style="color:var(--color-danger); font-size:0.8rem; margin-top:4px;"><c:out value="${fieldErrors.subjectName}"/></div></c:if>
        </div>
        <div class="field">
            <label for="credits">Credits <span style="font-weight:400; color:var(--color-ink-soft);">(optional)</span></label>
            <input type="number" id="credits" name="credits" step="0.5" min="0" value="<c:out value='${isEdit ? editingSubject.credits : param.credits}'/>">
        </div>

        <hr style="border:none; border-top:1px solid var(--color-rule); margin:var(--space-6) 0;">
        <p style="font-size:0.85rem; color:var(--color-ink-soft); margin-bottom:var(--space-4);">Enable at least one mark component. A subject can have more than one at once (e.g. a lab course with both theory and practical).</p>

        <%-- Theory component --%>
        <div class="field">
            <label><input type="checkbox" id="hasTheory" name="hasTheory" onchange="toggleComponent('theory')"
                   ${(isEdit && editingSubject.hasTheory) || (not isEdit && (empty param.subjectCode || param.hasTheory == 'on')) ? 'checked' : ''}> Theory</label>
        </div>
        <div id="theoryFields" style="display:none; padding-left:1.5rem; margin-bottom:var(--space-4);">
            <div class="field"><label for="theoryMaxMarks">Max marks</label>
                <input type="number" id="theoryMaxMarks" name="theoryMaxMarks" step="0.01" min="0" value="<c:out value='${isEdit ? editingSubject.theoryMaxMarks : param.theoryMaxMarks}'/>">
                <c:if test="${not empty fieldErrors.theoryMaxMarks}"><div style="color:var(--color-danger); font-size:0.8rem; margin-top:4px;"><c:out value="${fieldErrors.theoryMaxMarks}"/></div></c:if>
            </div>
            <div class="field"><label for="theoryPassingMarks">Passing marks</label>
                <input type="number" id="theoryPassingMarks" name="theoryPassingMarks" step="0.01" min="0" value="<c:out value='${isEdit ? editingSubject.theoryPassingMarks : param.theoryPassingMarks}'/>">
                <c:if test="${not empty fieldErrors.theoryPassingMarks}"><div style="color:var(--color-danger); font-size:0.8rem; margin-top:4px;"><c:out value="${fieldErrors.theoryPassingMarks}"/></div></c:if>
            </div>
        </div>

        <%-- Practical component --%>
        <div class="field">
            <label><input type="checkbox" id="hasPractical" name="hasPractical" onchange="toggleComponent('practical')"
                   ${(isEdit && editingSubject.hasPractical) || param.hasPractical == 'on' ? 'checked' : ''}> Practical</label>
        </div>
        <div id="practicalFields" style="display:none; padding-left:1.5rem; margin-bottom:var(--space-4);">
            <div class="field"><label for="practicalMaxMarks">Max marks</label>
                <input type="number" id="practicalMaxMarks" name="practicalMaxMarks" step="0.01" min="0" value="<c:out value='${isEdit ? editingSubject.practicalMaxMarks : param.practicalMaxMarks}'/>">
                <c:if test="${not empty fieldErrors.practicalMaxMarks}"><div style="color:var(--color-danger); font-size:0.8rem; margin-top:4px;"><c:out value="${fieldErrors.practicalMaxMarks}"/></div></c:if>
            </div>
            <div class="field"><label for="practicalPassingMarks">Passing marks</label>
                <input type="number" id="practicalPassingMarks" name="practicalPassingMarks" step="0.01" min="0" value="<c:out value='${isEdit ? editingSubject.practicalPassingMarks : param.practicalPassingMarks}'/>">
                <c:if test="${not empty fieldErrors.practicalPassingMarks}"><div style="color:var(--color-danger); font-size:0.8rem; margin-top:4px;"><c:out value="${fieldErrors.practicalPassingMarks}"/></div></c:if>
            </div>
        </div>

        <%-- Internal component --%>
        <div class="field">
            <label><input type="checkbox" id="hasInternal" name="hasInternal" onchange="toggleComponent('internal')"
                   ${(isEdit && editingSubject.hasInternal) || param.hasInternal == 'on' ? 'checked' : ''}> Internal</label>
        </div>
        <div id="internalFields" style="display:none; padding-left:1.5rem; margin-bottom:var(--space-4);">
            <div class="field"><label for="internalMaxMarks">Max marks</label>
                <input type="number" id="internalMaxMarks" name="internalMaxMarks" step="0.01" min="0" value="<c:out value='${isEdit ? editingSubject.internalMaxMarks : param.internalMaxMarks}'/>">
                <c:if test="${not empty fieldErrors.internalMaxMarks}"><div style="color:var(--color-danger); font-size:0.8rem; margin-top:4px;"><c:out value="${fieldErrors.internalMaxMarks}"/></div></c:if>
            </div>
            <div class="field"><label for="internalPassingMarks">Passing marks</label>
                <input type="number" id="internalPassingMarks" name="internalPassingMarks" step="0.01" min="0" value="<c:out value='${isEdit ? editingSubject.internalPassingMarks : param.internalPassingMarks}'/>">
                <c:if test="${not empty fieldErrors.internalPassingMarks}"><div style="color:var(--color-danger); font-size:0.8rem; margin-top:4px;"><c:out value="${fieldErrors.internalPassingMarks}"/></div></c:if>
            </div>
        </div>

        <div style="display:flex; gap:var(--space-3); margin-top:var(--space-6);">
            <button type="submit" class="btn-primary" style="width:auto; padding:0.65rem 1.5rem;">${isEdit ? 'Save changes' : 'Add subject'}</button>
            <a href="${ctx}/admin/subjects" style="align-self:center; color:var(--color-ink-soft); text-decoration:none; font-size:0.9rem;">Cancel</a>
        </div>
    </form>
</div>

<script>
    function toggleComponent(name) {
        var checked = document.getElementById('has' + name.charAt(0).toUpperCase() + name.slice(1)).checked;
        document.getElementById(name + 'Fields').style.display = checked ? 'block' : 'none';
    }
    // Sync visibility with whatever came back from the server (edit mode's
    // existing flags, or a validation-error redisplay's submitted checkboxes)
    // rather than defaulting everything to hidden on page load.
    ['theory', 'practical', 'internal'].forEach(toggleComponent);
</script>
<%@ include file="/common/fragments/admin-foot.jspf" %>
