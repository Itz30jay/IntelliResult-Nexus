<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Courses" scope="request"/>
<c:set var="pageSubtitle" value="Academic Setup" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<div style="display:flex; justify-content:flex-end; margin-bottom: var(--space-4);">
    <a href="${ctx}/admin/academic-setup/courses/new" class="btn-primary" style="width:auto; text-decoration:none; display:inline-block; padding:0.6rem 1.2rem;">+ Add Course</a>
</div>

<div class="panel">
    <table id="coursesTable" class="display" style="width:100%">
        <thead><tr><th>Name</th><th>Code</th><th>Department</th><th>Total Semesters</th><th>Actions</th></tr></thead>
        <tbody>
        <c:forEach items="${courses}" var="c">
            <tr>
                <td><c:out value="${c.name}"/></td>
                <td><c:out value="${c.code}"/></td>
                <td><c:out value="${c.department.name}"/></td>
                <td><c:out value="${c.totalSemesters}"/></td>
                <td>
                    <a href="${ctx}/admin/academic-setup/courses/edit?id=${c.id}" style="margin-right:0.75rem;">Edit</a>
                    <a href="#" onclick="confirmDelete(${c.id}, '<c:out value="${c.name}"/>'); return false;" style="color:var(--color-danger);">Delete</a>
                </td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
</div>

<c:if test="${not empty deletedCourses}">
<div class="panel" style="margin-top:var(--space-6);">
    <h2>Recycle Bin</h2>
    <c:forEach items="${deletedCourses}" var="c">
        <div class="list-row">
            <span><c:out value="${c.name}"/> (<c:out value="${c.code}"/>)</span>
            <a href="#" onclick="restoreItem(${c.id}); return false;">Restore</a>
        </div>
    </c:forEach>
</div>
</c:if>

<form id="deleteForm" method="post" action="${ctx}/admin/academic-setup/courses/delete" style="display:none;">
    <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
    <input type="hidden" name="id" id="deleteFormId">
</form>
<form id="restoreForm" method="post" action="${ctx}/admin/academic-setup/courses/restore" style="display:none;">
    <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
    <input type="hidden" name="id" id="restoreFormId">
</form>

<script>
    $(document).ready(function() { $('#coursesTable').DataTable({ order: [[0, 'asc']] }); });
    function confirmDelete(id, name) {
        Swal.fire({
            title: 'Delete course?', text: name + ' moves to the recycle bin and can be restored later.',
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
