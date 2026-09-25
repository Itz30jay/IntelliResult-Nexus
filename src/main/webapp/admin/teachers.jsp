<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Teachers" scope="request"/>
<c:set var="pageSubtitle" value="Academic roster and account management" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<%-- Upgrade: same reasoning as students.jsp - this page now owns the three account actions directly instead of pointing at the (now Admin-only) /admin/users. --%>
<div class="panel">
    <c:choose>
        <c:when test="${empty teachers}">
            <div class="panel-empty"><p>No teachers yet. New teachers appear here once their public Registration is approved from the <a href="${ctx}/admin/registrations">Registrations</a> queue.</p></div>
        </c:when>
        <c:otherwise>
    <table id="teachersTable" class="display" style="width:100%">
        <thead>
        <tr>
            <th>Employee Code</th>
            <th>Name</th>
            <th>Email</th>
            <th>Department</th>
            <th>Designation</th>
            <th>Contact</th>
            <th>Status</th>
            <th>Actions</th>
        </tr>
        </thead>
        <tbody>
        <c:forEach items="${teachers}" var="t">
            <tr>
                <td><c:out value="${t.employeeCode}"/></td>
                <td><c:out value="${t.user.fullName}"/></td>
                <td><c:out value="${t.user.email}"/></td>
                <td><c:out value="${t.department.name}"/></td>
                <td><c:out value="${t.designation}"/></td>
                <td><c:out value="${t.contactNumber}"/></td>
                <td>
                    <c:choose>
                        <c:when test="${t.user.deleted}"><span class="badge" style="background:#FBEEEF;color:var(--color-danger);">Removed</span></c:when>
                        <c:when test="${t.user.active}"><span class="badge" style="background:#EAF3EE;color:var(--color-verified);">Active</span></c:when>
                        <c:otherwise><span class="badge" style="background:#F6EFE3;color:var(--color-seal);">Disabled</span></c:otherwise>
                    </c:choose>
                </td>
                <td style="white-space:nowrap;">
                    <c:choose>
                        <c:when test="${t.user.deleted}">
                            <a href="#" onclick="confirmRestore(${t.user.id}, '<c:out value="${t.user.fullName}"/>'); return false;" style="color:var(--color-verified);">Restore</a>
                        </c:when>
                        <c:otherwise>
                            <a href="#" onclick="confirmResetPassword(${t.user.id}, '<c:out value="${t.user.fullName}"/>'); return false;" style="margin-right:0.75rem;">Reset password</a>
                            <c:choose>
                                <c:when test="${t.user.active}">
                                    <a href="#" onclick="confirmStatusChange(${t.user.id}, '<c:out value="${t.user.fullName}"/>', false); return false;" style="margin-right:0.75rem; color:var(--color-danger);">Disable</a>
                                </c:when>
                                <c:otherwise>
                                    <a href="#" onclick="confirmStatusChange(${t.user.id}, '<c:out value="${t.user.fullName}"/>', true); return false;" style="margin-right:0.75rem; color:var(--color-verified);">Activate</a>
                                </c:otherwise>
                            </c:choose>
                            <a href="#" onclick="confirmDelete(${t.user.id}, '<c:out value="${t.user.fullName}"/>'); return false;" style="color:var(--color-danger);">Remove</a>
                        </c:otherwise>
                    </c:choose>
                </td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
        </c:otherwise>
    </c:choose>
</div>

<form id="statusForm" method="post" action="${ctx}/admin/users/toggle-status" style="display:none;">
    <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
    <input type="hidden" name="returnTo" value="/admin/teachers">
    <input type="hidden" name="id" id="statusFormId">
    <input type="hidden" name="activate" id="statusFormActivate">
</form>
<form id="resetForm" method="post" action="${ctx}/admin/users/reset-password" style="display:none;">
    <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
    <input type="hidden" name="returnTo" value="/admin/teachers">
    <input type="hidden" name="id" id="resetFormId">
</form>
<form id="deleteForm" method="post" action="${ctx}/admin/users/delete" style="display:none;">
    <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
    <input type="hidden" name="returnTo" value="/admin/teachers">
    <input type="hidden" name="id" id="deleteFormId">
</form>
<form id="restoreForm" method="post" action="${ctx}/admin/users/restore" style="display:none;">
    <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
    <input type="hidden" name="returnTo" value="/admin/teachers">
    <input type="hidden" name="id" id="restoreFormId">
</form>

<script>
    $(document).ready(function() {
        $('#teachersTable').DataTable({ order: [[0, 'asc']], pageLength: 25 });
    });

    function confirmStatusChange(id, name, activate) {
        Swal.fire({
            title: (activate ? 'Activate' : 'Disable') + ' account?',
            text: name + (activate ? ' will regain access to sign in.' : ' will no longer be able to sign in.'),
            icon: 'warning', showCancelButton: true,
            confirmButtonText: activate ? 'Activate' : 'Disable',
            confirmButtonColor: activate ? '#2F6B52' : '#A3384A'
        }).then((result) => {
            if (result.isConfirmed) {
                document.getElementById('statusFormId').value = id;
                document.getElementById('statusFormActivate').value = activate;
                document.getElementById('statusForm').submit();
            }
        });
    }

    function confirmResetPassword(id, name) {
        Swal.fire({
            title: 'Reset password?',
            text: 'A new temporary password will be generated for ' + name + '. Their current password stops working immediately.',
            icon: 'warning', showCancelButton: true,
            confirmButtonText: 'Reset password', confirmButtonColor: '#B8925A'
        }).then((result) => {
            if (result.isConfirmed) {
                document.getElementById('resetFormId').value = id;
                document.getElementById('resetForm').submit();
            }
        });
    }

    function confirmDelete(id, name) {
        Swal.fire({
            title: 'Remove ' + name + '?',
            text: 'Their account is soft-deleted and they immediately lose access. Their academic records are kept and this can be undone with Restore.',
            icon: 'warning', showCancelButton: true,
            confirmButtonText: 'Remove', confirmButtonColor: '#A3384A'
        }).then((result) => {
            if (result.isConfirmed) {
                document.getElementById('deleteFormId').value = id;
                document.getElementById('deleteForm').submit();
            }
        });
    }

    function confirmRestore(id, name) {
        Swal.fire({
            title: 'Restore ' + name + '?',
            text: 'Their account becomes active again and they can sign in with their existing password.',
            icon: 'question', showCancelButton: true,
            confirmButtonText: 'Restore', confirmButtonColor: '#2F6B52'
        }).then((result) => {
            if (result.isConfirmed) {
                document.getElementById('restoreFormId').value = id;
                document.getElementById('restoreForm').submit();
            }
        });
    }

    <c:if test="${not empty flashTempPassword}">
    Swal.fire({
        title: 'Temporary password generated',
        html: 'For <b><c:out value="${flashTempPasswordFor}"/></b>:<br><br>' +
              '<code style="font-size:1.2rem;background:#F7F7F5;padding:0.4rem 0.8rem;border-radius:6px;display:inline-block;">' +
              '<c:out value="${flashTempPassword}"/></code>' +
              '<br><br><small>Copy this now - it will not be shown again. Share it with the teacher through a secure channel.</small>',
        icon: 'success', confirmButtonText: 'Copied, close this'
    });
    </c:if>
    <c:if test="${not empty errorMessage}">
    toastr.error('<c:out value="${errorMessage}"/>');
    </c:if>
</script>
<%@ include file="/common/fragments/admin-foot.jspf" %>
