<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
    /teacher/marks-entry/import - reached from a specific row on
    /teacher/marks-entry (the picker), so exam/subject/section are already
    fixed by the time this page loads - no context selector here, unlike
    the admin student-import page. Same query-string CSRF token approach
    as student-import.jsp, for the same multipart/filter-ordering reason.
--%>
<c:set var="pageTitle" value="Import Marks" scope="request"/>
<c:set var="pageSubtitle" value="Bulk-enter marks for one exam and subject from an Excel file" scope="request"/>
<%@ include file="/common/fragments/teacher-head.jspf" %>

<div class="panel" style="margin-bottom: var(--space-4);">
    <div style="display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:var(--space-3);">
        <div>
            <div style="font-weight:600;"><c:out value="${exam.name}"/></div>
            <div style="color:var(--color-ink-soft); font-size:0.85rem;">
                <c:out value="${assignment.subject.subjectCode}"/> &mdash; <c:out value="${assignment.subject.subjectName}"/>
                &middot; Section <c:out value="${assignment.section.name}"/>
            </div>
        </div>
        <a class="btn-secondary" style="width:auto; padding:0.55rem 1.1rem; text-decoration:none;"
           href="${ctx}/teacher/marks-entry/export?examId=${exam.id}&subjectId=${assignment.subject.id}&sectionId=${assignment.section.id}">
            Download Current Roster / Template
        </a>
    </div>
</div>

<div class="panel">
    <h3 style="margin-top:0;">Upload file</h3>
    <p style="color:var(--color-ink-soft); font-size:0.9rem;">
        Columns, in order: <strong>Roll No, Student Name (reference only), Theory, Practical, Internal</strong>.
        Matching is by Roll No; leave a marks column blank for any component this subject doesn't use.
    </p>

    <c:if test="${not empty importSuccessCount}">
        <div class="auth-success" style="margin-bottom:var(--space-4);">
            <c:out value="${importSuccessCount}"/> row(s) ${wasSubmitted ? 'submitted for approval' : 'saved as draft'}.
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

    <form method="POST"
          action="${ctx}/teacher/marks-entry/import?examId=${exam.id}&subjectId=${assignment.subject.id}&sectionId=${assignment.section.id}&csrfToken=${sessionScope.csrfToken}"
          enctype="multipart/form-data">
        <input type="file" name="file" accept=".xlsx" required/>
        <div style="margin-top:var(--space-3);">
            <label>
                <input type="checkbox" name="alsoSubmit" value="true"/>
                Also submit for administrative approval immediately (otherwise saved as Draft)
            </label>
        </div>
        <button type="submit" class="btn-primary" style="width:auto; padding:0.6rem 1.2rem; margin-top:var(--space-3);">
            Import Marks
        </button>
    </form>
</div>

<%@ include file="/common/fragments/teacher-foot.jspf" %>
