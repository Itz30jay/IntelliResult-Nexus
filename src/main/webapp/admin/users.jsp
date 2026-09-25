<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Admins" scope="request"/>
<c:set var="pageSubtitle" value="Administrator accounts for this system" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<%--
    Upgrade: this page now shows ADMIN-role accounts exclusively - Students
    and Teachers moved to their own dedicated pages (/admin/students,
    /admin/teachers), and "Import Students" moved there with them since it
    never belonged on an accounts-in-general page to begin with.
--%>
<div style="display:flex; justify-content:flex-end; gap:var(--space-3); margin-bottom: var(--space-4);">
    <a href="${ctx}/admin/users/new" class="btn-primary" style="width:auto; text-decoration:none; display:inline-block; padding:0.6rem 1.2rem;">+ Create Admin</a>
</div>

<div class="panel">
    <c:choose>
        <c:when test="${empty users}">
            <div class="panel-empty"><p>No admin accounts found.</p></div>
        </c:when>
        <c:otherwise>
    <table id="usersTable" class="display" style="width:100%">
        <thead>
        <tr>
            <th>Name</th>
            <th>Email</th>
            <th>Status</th>
            <th>Last Login</th>
            <th>Actions</th>
        </tr>
        </thead>
        <tbody>
        <c:forEach items="${users}" var="u">
            <tr>
                <td><c:out value="${u.fullName}"/></td>
                <td><c:out value="${u.email}"/></td>
                <td>
                    <c:choose>
                        <c:when test="${u.active}"><span class="badge" style="background:#EAF3EE;color:var(--color-verified);">Active</span></c:when>
                        <c:otherwise><span class="badge" style="background:#FBEEEF;color:var(--color-danger);">Disabled</span></c:otherwise>
                    </c:choose>
                </td>
                <td><c:out value="${u.lastLoginAt}"/></td>
                <td style="white-space:nowrap;">
                    <a href="${ctx}/admin/users/edit?id=${u.id}" style="margin-right:0.75rem;">Edit</a>
                    <a href="#" onclick="confirmResetPassword(${u.id}, '<c:out value="${u.fullName}"/>'); return false;" style="margin-right:0.75rem;">Reset password</a>
                    <c:choose>
                        <c:when test="${u.active}">
                            <a href="#" onclick="confirmStatusChange(${u.id}, '<c:out value="${u.fullName}"/>', false); return false;" style="margin-right:0.75rem; color:var(--color-danger);">Disable</a>
                        </c:when>
                        <c:otherwise>
                            <a href="#" onclick="confirmStatusChange(${u.id}, '<c:out value="${u.fullName}"/>', true); return false;" style="margin-right:0.75rem; color:var(--color-verified);">Activate</a>
                        </c:otherwise>
                    </c:choose>
                    <a href="#" onclick="confirmDelete(${u.id}, '<c:out value="${u.fullName}"/>'); return false;" style="color:var(--color-danger);">Remove</a>
                </td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
        </c:otherwise>
    </c:choose>
</div>

<%-- Hidden forms submitted by the SweetAlert2 confirmation callbacks below - never fired without an explicit confirm click. --%>
<form id="statusForm" method="post" action="${ctx}/admin/users/toggle-status" style="display:none;">
    <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
    <input type="hidden" name="returnTo" value="/admin/users">
    <input type="hidden" name="id" id="statusFormId">
    <input type="hidden" name="activate" id="statusFormActivate">
</form>
<form id="resetForm" method="post" action="${ctx}/admin/users/reset-password" style="display:none;">
    <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
    <input type="hidden" name="returnTo" value="/admin/users">
    <input type="hidden" name="id" id="resetFormId">
</form>
<form id="deleteForm" method="post" action="${ctx}/admin/users/delete" style="display:none;">
    <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
    <input type="hidden" name="returnTo" value="/admin/users">
    <input type="hidden" name="id" id="deleteFormId">
</form>


<script>
    $(document).ready(function() {
        $('#usersTable').DataTable({ order: [[0, 'asc']], pageLength: 25 });
    });

    function confirmStatusChange(id, name, activate) {
        Swal.fire({
            title: (activate ? 'Activate' : 'Disable') + ' account?',
            text: name + (activate ? ' will regain access to sign in.' : ' will no longer be able to sign in.'),
            icon: 'warning',
            showCancelButton: true,
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
            icon: 'warning',
            showCancelButton: true,
            confirmButtonText: 'Reset password',
            confirmButtonColor: '#B8925A'
        }).then((result) => {
            if (result.isConfirmed) {
                document.getElementById('resetFormId').value = id;
                document.getElementById('resetForm').submit();
            }
        });
    }

    function confirmDelete(id, name) {
        Swal.fire({
            title: 'Remove this admin account?',
            text: name + ' will be soft-deleted and immediately lose access. This can be reversed from the database if needed, but not from this screen.',
            icon: 'warning',
            showCancelButton: true,
            confirmButtonText: 'Remove',
            confirmButtonColor: '#A3384A'
        }).then((result) => {
            if (result.isConfirmed) {
                document.getElementById('deleteFormId').value = id;
                document.getElementById('deleteForm').submit();
            }
        });
    }

    <c:if test="${not empty flashTempPassword}">
    Swal.fire({
        title: 'Temporary password generated',
        html: 'For <b><c:out value="${flashTempPasswordFor}"/></b>:<br><br>' +
              '<code style="font-size:1.2rem;background:#F7F7F5;padding:0.4rem 0.8rem;border-radius:6px;display:inline-block;">' +
              '<c:out value="${flashTempPassword}"/></code>' +
              '<br><br><small>Copy this now - it will not be shown again. Share it with the user through a secure channel.</small>',
        icon: 'success',
        confirmButtonText: 'Copied, close this'
    });
    </c:if>

    <c:if test="${not empty errorMessage}">
    toastr.error('<c:out value="${errorMessage}"/>');
    </c:if>
</script>
<%@ include file="/common/fragments/admin-foot.jspf" %>
