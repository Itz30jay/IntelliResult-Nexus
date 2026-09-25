<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Activity Logs" scope="request"/>
<c:set var="pageSubtitle" value="Every recorded action across the system" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<%--
    New page (upgrade pass) - Sec. 26's audit-log viewer. Every write this
    system makes already calls activityLogDAO.save(...) (logins,
    corrections, registrations, exports, deletions, revaluation
    assignments...) - this is the first screen that actually shows any of it.
--%>
<div class="panel" style="margin-bottom:var(--space-6);">
    <form method="get" action="${ctx}/admin/activity-logs" style="display:flex; gap:var(--space-3); align-items:flex-end; flex-wrap:wrap;">
        <div class="field" style="margin-bottom:0; min-width:150px;">
            <label for="role">Role</label>
            <select id="role" name="role">
                <option value="">Any</option>
                <option value="ADMIN" ${selectedRole == 'ADMIN' ? 'selected' : ''}>Admin</option>
                <option value="TEACHER" ${selectedRole == 'TEACHER' ? 'selected' : ''}>Teacher</option>
                <option value="STUDENT" ${selectedRole == 'STUDENT' ? 'selected' : ''}>Student</option>
            </select>
        </div>
        <div class="field" style="margin-bottom:0; min-width:200px;">
            <label for="action">Action</label>
            <select id="action" name="action">
                <option value="">Any</option>
                <c:forEach items="${distinctActions}" var="a">
                    <option value="${a}" ${selectedAction == a ? 'selected' : ''}><c:out value="${a}"/></option>
                </c:forEach>
            </select>
        </div>
        <div class="field" style="margin-bottom:0; min-width:150px;">
            <label for="from">From</label>
            <input type="date" id="from" name="from" value="${fromDate}">
        </div>
        <div class="field" style="margin-bottom:0; min-width:150px;">
            <label for="to">To</label>
            <input type="date" id="to" name="to" value="${toDate}">
        </div>
        <button type="submit" class="btn-primary" style="width:auto; padding:0.65rem 1.5rem;">Filter</button>
        <a href="${ctx}/admin/activity-logs" class="btn-secondary" style="width:auto; padding:0.65rem 1.5rem; text-decoration:none; display:inline-block;">Reset</a>
    </form>
</div>

<c:if test="${resultLimited}">
    <div class="auth-success" role="status" style="margin-bottom:var(--space-6); background:#F6EFE3; color:var(--color-seal); border-color:var(--color-seal);">
        Showing the most recent 200 entries. Use the filters above to narrow down to a specific role, action, or date range.
    </div>
</c:if>

<div class="panel">
    <c:choose>
        <c:when test="${empty logs}">
            <div class="panel-empty"><p>No activity recorded<c:if test="${not empty selectedRole or not empty selectedAction or not empty fromDate}"> matching these filters</c:if>.</p></div>
        </c:when>
        <c:otherwise>
            <div class="table-scroll">
            <table id="logsTable" class="display" style="width:100%">
                <thead>
                <tr><th>When</th><th>Who</th><th>Action</th><th>Details</th><th>IP</th></tr>
                </thead>
                <tbody>
                <c:forEach items="${logs}" var="log">
                    <tr>
                        <td><c:out value="${log.createdAt}"/></td>
                        <td><c:out value="${not empty log.user ? log.user.fullName : 'System / anonymous'}"/></td>
                        <td><code><c:out value="${log.action}"/></code></td>
                        <td><c:out value="${log.details}"/></td>
                        <td><c:out value="${log.ipAddress}"/></td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
            </div>
        </c:otherwise>
    </c:choose>
</div>

<script>
    $(document).ready(function() { if ($('#logsTable tr').length) { $('#logsTable').DataTable({ order: [[0, 'desc']], pageLength: 50 }); } });
</script>
<%@ include file="/common/fragments/admin-foot.jspf" %>
