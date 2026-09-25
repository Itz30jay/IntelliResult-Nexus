<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
    /admin/users/import - Sec. 21/22's bulk student import. The CSRF token
    rides in the form's own action query string, not a hidden field -
    CsrfFilter reads it via request.getParameter() from a filter that runs
    before this servlet's own @MultipartConfig is in scope, and a query
    string parameter is readable regardless of how (or whether) the body
    gets parsed as multipart, which sidesteps that question entirely
    instead of depending on a specific container's behavior for it.
--%>
<c:set var="pageTitle" value="Import Students" scope="request"/>
<c:set var="pageSubtitle" value="Bulk-create student accounts from an Excel file" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<div class="panel" style="margin-bottom: var(--space-4);">
    <h3 style="margin-top:0;">1. Choose course &amp; section</h3>
    <p style="color:var(--color-ink-soft); font-size:0.9rem;">
        Every student in the file will be created in this course (and section, if given).
        Use the same selection to <strong>download the current roster</strong> below as your starting template.
    </p>
    <form id="contextForm" style="display:flex; gap:var(--space-3); align-items:flex-end; flex-wrap:wrap;">
        <div>
            <label for="courseSelect">Course</label><br/>
            <select id="courseSelect" name="courseId">
                <option value="">Select a course&hellip;</option>
                <c:forEach items="${courses}" var="c">
                    <option value="${c.id}" ${selectedCourseId == c.id ? 'selected' : ''}><c:out value="${c.name}"/></option>
                </c:forEach>
            </select>
        </div>
        <div>
            <label for="sectionSelect">Section (optional)</label><br/>
            <select id="sectionSelect" name="sectionId">
                <option value="">No specific section</option>
                <c:forEach items="${sections}" var="s">
                    <option value="${s.id}" ${selectedSectionId == s.id ? 'selected' : ''}><c:out value="${s.name}"/></option>
                </c:forEach>
            </select>
        </div>
        <button type="button" id="downloadTemplateBtn" class="btn-secondary" style="width:auto; padding:0.55rem 1.1rem;">
            Download Roster / Template
        </button>
    </form>
</div>

<div class="panel">
    <h3 style="margin-top:0;">2. Upload file</h3>
    <p style="color:var(--color-ink-soft); font-size:0.9rem;">
        Columns, in order: <strong>Roll No, Full Name, Email, Phone</strong> (Phone optional). First row is treated as a header.
    </p>

    <c:if test="${not empty importSuccessCount}">
        <div class="auth-success" style="margin-bottom:var(--space-4);">
            <c:out value="${importSuccessCount}"/> student(s) imported successfully.
        </div>
    </c:if>

    <c:if test="${not empty errorMessage}">
        <div class="auth-error" style="margin-bottom:var(--space-4);">
            <c:out value="${errorMessage}"/>
        </div>
        <c:if test="${not empty rowErrors}">
            <ul style="font-size:0.85rem; color:var(--color-danger); max-height:300px; overflow-y:auto;">
                <c:forEach items="${rowErrors}" var="err">
                    <li><c:out value="${err}"/></li>
                </c:forEach>
            </ul>
        </c:if>
    </c:if>

    <form method="POST" action="${ctx}/admin/users/import?csrfToken=${sessionScope.csrfToken}" enctype="multipart/form-data" id="importForm">
        <input type="hidden" name="courseId" id="importCourseId" value="${selectedCourseId}"/>
        <input type="hidden" name="sectionId" id="importSectionId" value="${selectedSectionId}"/>
        <input type="file" name="file" accept=".xlsx" required/>
        <button type="submit" class="btn-primary" style="width:auto; padding:0.6rem 1.2rem; margin-top:var(--space-3);">
            Import Students
        </button>
    </form>
</div>

<script>
    function currentContext() {
        return {
            courseId: document.getElementById('courseSelect').value,
            sectionId: document.getElementById('sectionSelect').value
        };
    }

    document.getElementById('downloadTemplateBtn').addEventListener('click', function () {
        var ctxValues = currentContext();
        if (!ctxValues.courseId) {
            Swal.fire('Choose a course first', 'Pick a course above so the download knows which roster to use.', 'info');
            return;
        }
        var url = '${ctx}/admin/users/export?courseId=' + encodeURIComponent(ctxValues.courseId);
        if (ctxValues.sectionId) {
            url += '&sectionId=' + encodeURIComponent(ctxValues.sectionId);
        }
        window.location = url;
    });

    document.getElementById('importForm').addEventListener('submit', function (e) {
        var ctxValues = currentContext();
        if (!ctxValues.courseId) {
            e.preventDefault();
            Swal.fire('Choose a course first', 'Pick a course above before uploading.', 'warning');
            return;
        }
        document.getElementById('importCourseId').value = ctxValues.courseId;
        document.getElementById('importSectionId').value = ctxValues.sectionId;
    });
</script>

<%@ include file="/common/fragments/admin-foot.jspf" %>
