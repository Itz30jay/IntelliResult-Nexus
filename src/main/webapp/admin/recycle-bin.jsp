<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Recycle Bin" scope="request"/>
<c:set var="pageSubtitle" value="Every soft-deleted record across the system, in one place" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<%--
    New page (upgrade pass) - Sec. 27's "View deleted records" existed only
    as a findDeleted() call quietly loaded into each entity's own list page
    (Departments/Courses/Sections/Subjects/Exams/Notices/Users each show
    their own deleted rows already) - the sidebar's "Recycle Bin" link
    itself had never had a servlet or page behind it. This aggregates all
    of them; each Restore button posts straight to that entity's own
    existing restore endpoint, so restoring here is exactly the same action
    as restoring from that entity's own page, not a new code path.
--%>
<c:if test="${not empty errorMessage}">
    <div class="auth-error" role="alert" style="margin-bottom:var(--space-6);"><c:out value="${errorMessage}"/></div>
</c:if>

<div class="panel">
    <c:choose>
        <c:when test="${empty items}">
            <div class="panel-empty"><p>Nothing has been deleted. Anything you remove from Students, Teachers, Admins, Academic Setup, Subjects, Exams, or Notices will appear here, recoverable at any time.</p></div>
        </c:when>
        <c:otherwise>
            <div class="table-scroll">
            <table id="recycleBinTable" class="display" style="width:100%">
                <thead>
                <tr><th>Type</th><th>Record</th><th>Deleted</th><th>Deleted by</th><th></th></tr>
                </thead>
                <tbody>
                <c:forEach items="${items}" var="item" varStatus="loop">
                    <tr>
                        <td><span class="badge" style="background:var(--color-paper-soft); color:var(--color-ink-soft);"><c:out value="${item.category}"/></span></td>
                        <td><c:out value="${item.label}"/></td>
                        <td><c:out value="${not empty item.deletedAt ? item.deletedAt : '&mdash;'}"/></td>
                        <td><c:out value="${not empty item.deletedByName ? item.deletedByName : 'Unknown'}"/></td>
                        <td>
                            <a href="#" onclick="confirmRestore('restoreForm${loop.index}', '<c:out value="${item.label}"/>'); return false;" style="color:var(--color-verified); font-weight:600;">Restore</a>
                            <form id="restoreForm${loop.index}" method="post" action="${ctx}${item.restoreUrl}" style="display:none;">
                                <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
                                <input type="hidden" name="id" value="${item.id}">
                                <c:if test="${not empty item.returnTo}"><input type="hidden" name="returnTo" value="${item.returnTo}"></c:if>
                            </form>
                        </td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
            </div>
        </c:otherwise>
    </c:choose>
</div>

<script>
    $(document).ready(function() { if ($('#recycleBinTable tr').length) { $('#recycleBinTable').DataTable({ order: [[2, 'desc']] }); } });
    function confirmRestore(formId, label) {
        Swal.fire({
            title: 'Restore this record?',
            text: '"' + label + '" will become active again, exactly as if it were never deleted.',
            icon: 'question', showCancelButton: true,
            confirmButtonText: 'Restore', confirmButtonColor: '#2F6B52'
        }).then((r) => { if (r.isConfirmed) { document.getElementById(formId).submit(); } });
    }
</script>
<%@ include file="/common/fragments/admin-foot.jspf" %>
