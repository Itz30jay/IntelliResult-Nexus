<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Subjects" scope="request"/>
<c:set var="pageSubtitle" value="Academic Setup" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<div style="display:flex; justify-content:flex-end; margin-bottom: var(--space-4);">
    <a href="${ctx}/admin/subjects/new" class="btn-primary" style="width:auto; text-decoration:none; display:inline-block; padding:0.6rem 1.2rem;">+ Add Subject</a>
</div>

<div class="panel">
    <table id="subjectsTable" class="display" style="width:100%">
        <thead><tr><th>Code</th><th>Name</th><th>Semester</th><th>Components</th><th>Total Marks</th><th>Actions</th></tr></thead>
        <tbody>
        <c:forEach items="${subjects}" var="s">
            <tr>
                <td><c:out value="${s.subjectCode}"/></td>
                <td><c:out value="${s.subjectName}"/></td>
                <td>Sem <c:out value="${s.semester.semesterNumber}"/> &mdash; <c:out value="${s.semester.course.code}"/></td>
                <td>
                    <c:if test="${s.hasTheory}"><span class="badge badge-priority-normal">Theory</span></c:if>
                    <c:if test="${s.hasPractical}"><span class="badge badge-priority-normal">Practical</span></c:if>
                    <c:if test="${s.hasInternal}"><span class="badge badge-priority-normal">Internal</span></c:if>
                </td>
                <td class="metric"><c:out value="${s.totalMaxMarks}"/></td>
                <td>
                    <a href="${ctx}/admin/subjects/edit?id=${s.id}" style="margin-right:0.75rem;">Edit</a>
                    <a href="#" onclick="confirmDelete(${s.id}, '<c:out value="${s.subjectCode}"/>'); return false;" style="color:var(--color-danger);">Delete</a>
                </td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
</div>

<c:if test="${not empty deletedSubjects}">
<div class="panel" style="margin-top:var(--space-6);">
    <h2>Recycle Bin</h2>
    <c:forEach items="${deletedSubjects}" var="s">
        <div class="list-row">
            <span><c:out value="${s.subjectCode}"/> &mdash; <c:out value="${s.subjectName}"/></span>
            <a href="#" onclick="restoreItem(${s.id}); return false;">Restore</a>
        </div>
    </c:forEach>
</div>
</c:if>

<form id="deleteForm" method="post" action="${ctx}/admin/subjects/delete" style="display:none;">
    <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
    <input type="hidden" name="id" id="deleteFormId">
</form>
<form id="restoreForm" method="post" action="${ctx}/admin/subjects/restore" style="display:none;">
    <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
    <input type="hidden" name="id" id="restoreFormId">
</form>

<script>
    $(document).ready(function() { $('#subjectsTable').DataTable({ order: [[0, 'asc']] }); });
    function confirmDelete(id, code) {
        Swal.fire({
            title: 'Delete subject?', text: code + ' moves to the recycle bin and can be restored later.',
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
