<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="Password Resets" scope="request"/>
<c:set var="pageSubtitle" value="OTP-verified password changes awaiting your approval" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<%-- New page (upgrade pass) - Sec. "Forgot Password + OTP... keep Admin in the loop." By the time a request reaches here, OTP has already verified the person's identity and the new password is already hashed and staged - there is nothing left to fill in, only to authorize or decline. --%>
<c:if test="${not empty successMessage}"><div class="auth-success" role="status" style="margin-bottom:var(--space-6);"><c:out value="${successMessage}"/></div></c:if>
<c:if test="${not empty errorMessage}"><div class="auth-error" role="alert" style="margin-bottom:var(--space-6);"><c:out value="${errorMessage}"/></div></c:if>

<div class="panel">
    <h2 style="margin:0 0 var(--space-4); font-family:var(--font-display);">Pending approval</h2>
    <c:choose>
        <c:when test="${empty pendingRequests}">
            <div class="panel-empty"><p>No password changes are waiting on review.</p></div>
        </c:when>
        <c:otherwise>
            <table class="display" style="width:100%">
                <thead><tr><th>User</th><th>Role</th><th>Verified</th><th>Requested</th><th></th></tr></thead>
                <tbody>
                <c:forEach items="${pendingRequests}" var="req">
                    <tr>
                        <td><c:out value="${req.user.fullName}"/> (<c:out value="${req.user.email}"/>)</td>
                        <td><c:out value="${req.user.role}"/></td>
                        <td><c:out value="${req.otpVerifiedAt}"/></td>
                        <td><c:out value="${req.createdAt}"/></td>
                        <td style="white-space:nowrap;">
                            <a href="#" onclick="confirmAction(${req.id}, 'approve', '<c:out value="${req.user.fullName}"/>'); return false;" style="color:var(--color-verified); margin-right:0.75rem;">Approve</a>
                            <a href="#" onclick="confirmAction(${req.id}, 'reject', '<c:out value="${req.user.fullName}"/>'); return false;" style="color:var(--color-danger);">Reject</a>
                        </td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </c:otherwise>
    </c:choose>
</div>

<div class="panel" style="margin-top:var(--space-6);">
    <h2 style="margin:0 0 var(--space-4); font-family:var(--font-display);">Review history</h2>
    <c:choose>
        <c:when test="${empty reviewedRequests}">
            <div class="panel-empty"><p>No password changes have been reviewed yet.</p></div>
        </c:when>
        <c:otherwise>
            <table class="display" style="width:100%">
                <thead><tr><th>User</th><th>Status</th><th>Reviewed by</th><th>Reviewed</th></tr></thead>
                <tbody>
                <c:forEach items="${reviewedRequests}" var="req">
                    <tr>
                        <td><c:out value="${req.user.fullName}"/></td>
                        <td><span class="badge badge-status badge-status-${fn:toLowerCase(req.status)}"><c:out value="${req.status}"/></span></td>
                        <td><c:out value="${req.reviewedBy.fullName}"/></td>
                        <td><c:out value="${req.reviewedAt}"/></td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </c:otherwise>
    </c:choose>
</div>

<form id="approveForm" method="post" action="${ctx}/admin/password-resets/approve" style="display:none;">
    <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
    <input type="hidden" name="id" id="approveFormId">
</form>
<form id="rejectForm" method="post" action="${ctx}/admin/password-resets/reject" style="display:none;">
    <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
    <input type="hidden" name="id" id="rejectFormId">
</form>

<script>
    function confirmAction(id, action, name) {
        var approving = action === 'approve';
        Swal.fire({
            title: (approving ? 'Approve' : 'Reject') + ' password change?',
            text: approving ? name + '\'s password will be updated immediately.' : name + '\'s current password remains unchanged.',
            icon: 'warning', showCancelButton: true,
            confirmButtonText: approving ? 'Approve' : 'Reject',
            confirmButtonColor: approving ? '#2F6B52' : '#A3384A'
        }).then((r) => {
            if (r.isConfirmed) {
                var form = document.getElementById(approving ? 'approveForm' : 'rejectForm');
                document.getElementById(approving ? 'approveFormId' : 'rejectFormId').value = id;
                form.submit();
            }
        });
    }
</script>
<%@ include file="/common/fragments/admin-foot.jspf" %>
