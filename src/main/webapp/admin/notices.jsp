<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Notices" scope="request"/>
<c:set var="pageSubtitle" value="Notice Board" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<div style="display:flex; justify-content:flex-end; margin-bottom: var(--space-4);">
    <a href="${ctx}/admin/notices/new" class="btn-primary" style="width:auto; text-decoration:none; display:inline-block; padding:0.6rem 1.2rem;">+ Add Notice</a>
</div>

<div class="panel">
    <c:choose>
        <c:when test="${empty notices}">
            <p class="panel-empty">No notices yet. Click <strong>+ Add Notice</strong> to post the first one.</p>
        </c:when>
        <c:otherwise>
            <table id="noticesTable" class="display" style="width:100%">
                <thead>
                <tr>
                    <th>Title</th>
                    <th>Audience</th>
                    <th>Priority</th>
                    <th>Status</th>
                    <th>Expiry</th>
                    <th>Posted by</th>
                    <th>Actions</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach items="${notices}" var="notice">
                    <tr>
                        <td><c:out value="${notice.title}"/></td>
                        <td>
                            <c:forEach items="${notice.audience}" var="role" varStatus="st">
                                <c:out value="${role}"/><c:if test="${!st.last}">, </c:if>
                            </c:forEach>
                        </td>
                        <td>
                            <c:choose>
                                <c:when test="${notice.priority == 'HIGH' || notice.priority == 'URGENT'}"><span class="badge badge-priority-high"><c:out value="${notice.priority}"/></span></c:when>
                                <c:when test="${notice.priority == 'LOW'}"><span class="badge badge-priority-low"><c:out value="${notice.priority}"/></span></c:when>
                                <c:otherwise><span class="badge badge-priority-normal"><c:out value="${notice.priority}"/></span></c:otherwise>
                            </c:choose>
                        </td>
                        <td>
                            <c:choose>
                                <c:when test="${notice.published}"><span class="badge badge-status badge-status-published">PUBLISHED</span></c:when>
                                <c:otherwise><span class="badge badge-status badge-status-created">DRAFT</span></c:otherwise>
                            </c:choose>
                        </td>
                        <td><c:out value="${empty notice.expiryDate ? 'No expiry' : notice.expiryDate.toLocalDate()}"/></td>
                        <td><c:out value="${notice.createdBy.fullName}"/></td>
                        <td style="white-space:nowrap;">
                            <a href="${ctx}/admin/notices/edit?id=${notice.id}" style="margin-right:0.75rem;">Edit</a>
                            <c:choose>
                                <c:when test="${notice.published}">
                                    <a href="#" onclick="toggleNotice(${notice.id}, 'unpublish'); return false;" style="margin-right:0.75rem; color:var(--color-seal);">Unpublish</a>
                                </c:when>
                                <c:otherwise>
                                    <a href="#" onclick="toggleNotice(${notice.id}, 'publish'); return false;" style="margin-right:0.75rem; color:var(--color-verified);">Publish</a>
                                </c:otherwise>
                            </c:choose>
                            <a href="#" onclick="confirmDelete(${notice.id}, '<c:out value="${notice.title}"/>'); return false;" style="color:var(--color-danger);">Delete</a>
                        </td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </c:otherwise>
    </c:choose>
</div>

<c:if test="${not empty deletedNotices}">
<div class="panel" style="margin-top:var(--space-6);">
    <h2>Recycle Bin</h2>
    <c:forEach items="${deletedNotices}" var="notice">
        <div class="list-row">
            <span><c:out value="${notice.title}"/></span>
            <a href="#" onclick="restoreItem(${notice.id}); return false;">Restore</a>
        </div>
    </c:forEach>
</div>
</c:if>

<form id="deleteForm" method="post" action="${ctx}/admin/notices/delete" style="display:none;">
    <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
    <input type="hidden" name="id" id="deleteFormId">
</form>
<form id="restoreForm" method="post" action="${ctx}/admin/notices/restore" style="display:none;">
    <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
    <input type="hidden" name="id" id="restoreFormId">
</form>
<form id="toggleForm" method="post" style="display:none;">
    <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
    <input type="hidden" name="id" id="toggleFormId">
</form>

<script>
    $(document).ready(function() { $('#noticesTable').DataTable({ order: [[0, 'asc']] }); });

    function confirmDelete(id, title) {
        Swal.fire({
            title: 'Delete notice?', text: title + ' moves to the recycle bin and can be restored later.',
            icon: 'warning', showCancelButton: true, confirmButtonText: 'Delete', confirmButtonColor: '#A3384A'
        }).then((r) => { if (r.isConfirmed) { document.getElementById('deleteFormId').value = id; document.getElementById('deleteForm').submit(); } });
    }
    function restoreItem(id) {
        document.getElementById('restoreFormId').value = id;
        document.getElementById('restoreForm').submit();
    }
    // No confirmation for publish/unpublish - unlike Exam's advance-status
    // (a one-way door for every stage but the first), a notice can be
    // toggled back and forth freely, so a confirm dialog would just be
    // friction on a fully reversible action.
    function toggleNotice(id, action) {
        document.getElementById('toggleFormId').value = id;
        document.getElementById('toggleForm').action = '${ctx}/admin/notices/' + action;
        document.getElementById('toggleForm').submit();
    }
    <c:if test="${not empty errorMessage}">toastr.error('<c:out value="${errorMessage}"/>');</c:if>
    <c:if test="${not empty successMessage}">toastr.success('<c:out value="${successMessage}"/>');</c:if>
</script>
<%@ include file="/common/fragments/admin-foot.jspf" %>
