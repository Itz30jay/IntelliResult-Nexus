<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Sections" scope="request"/>
<c:set var="pageSubtitle" value="Academic Setup" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<div style="display:flex; justify-content:flex-end; margin-bottom: var(--space-4);">
    <a href="${ctx}/admin/academic-setup/sections/new" class="btn-primary" style="width:auto; text-decoration:none; display:inline-block; padding:0.6rem 1.2rem;">+ Add Section</a>
</div>

<div class="panel">
    <table id="sectionsTable" class="display" style="width:100%">
        <thead><tr><th>Name</th><th>Semester</th><th>Type</th><th>Capacity</th><th>Actions</th></tr></thead>
        <tbody>
        <c:forEach items="${sections}" var="s">
            <tr>
                <td><c:out value="${s.name}"/></td>
                <td>Sem <c:out value="${s.semester.semesterNumber}"/> &mdash; <c:out value="${s.semester.course.code}"/> (<c:out value="${s.semester.academicYear.label}"/>)</td>
                <td><c:if test="${s.sectionType == 'ONE_TIME'}"><span class="badge" style="background:#F6EFE3;color:var(--color-seal);">One-time</span></c:if><c:if test="${s.sectionType != 'ONE_TIME'}"><span style="color:var(--color-ink-soft); font-size:0.85rem;">Permanent</span></c:if></td>
                <td><c:out value="${s.capacity}"/></td>
                <td>
                    <a href="${ctx}/admin/academic-setup/sections/edit?id=${s.id}" style="margin-right:0.75rem;">Edit</a>
                    <a href="#" onclick="confirmDelete(${s.id}, '<c:out value="${s.name}"/>'); return false;" style="color:var(--color-danger);">Delete</a>
                </td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
</div>

<c:if test="${not empty deletedSections}">
<div class="panel" style="margin-top:var(--space-6);">
    <h2>Recycle Bin</h2>
    <c:forEach items="${deletedSections}" var="s">
        <div class="list-row">
            <span><c:out value="${s.name}"/> &mdash; Sem <c:out value="${s.semester.semesterNumber}"/> <c:out value="${s.semester.course.code}"/></span>
            <a href="#" onclick="restoreItem(${s.id}); return false;">Restore</a>
        </div>
    </c:forEach>
</div>
</c:if>

<form id="deleteForm" method="post" action="${ctx}/admin/academic-setup/sections/delete" style="display:none;">
    <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
    <input type="hidden" name="id" id="deleteFormId">
</form>
<form id="restoreForm" method="post" action="${ctx}/admin/academic-setup/sections/restore" style="display:none;">
    <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
    <input type="hidden" name="id" id="restoreFormId">
</form>

<script>
    $(document).ready(function() { $('#sectionsTable').DataTable({ order: [[0, 'asc']] }); });
    function confirmDelete(id, name) {
        Swal.fire({
            title: 'Delete section?', text: name + ' moves to the recycle bin and can be restored later.',
            icon: 'warning', showCancelButton: true, confirmButtonText: 'Delete', confirmButtonColor: '#A3384A'
        }).then((r) => { if (r.isConfirmed) { document.getElementById('deleteFormId').value = id; document.getElementById('deleteForm').submit(); } });
    }
    function restoreItem(id) {
        document.getElementById('restoreFormId').value = id;
        document.getElementById('restoreForm').submit();
    }
    <c:if test="${not empty errorMessage}">toastr.error('<c:out value="${errorMessage}"/>');</c:if>
</script>
<%@ include file="/common/fragments/admin-foot.jspf" %>
