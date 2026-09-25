<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="Registrations" scope="request"/>
<c:set var="pageSubtitle" value="Review and verify public sign-ups before an account goes live" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<c:if test="${not empty successMessage}">
    <div class="auth-success" role="status" style="margin-bottom:var(--space-6);"><c:out value="${successMessage}"/></div>
</c:if>
<c:if test="${not empty errorMessage}">
    <div class="auth-error" role="alert" style="margin-bottom:var(--space-6);"><c:out value="${errorMessage}"/></div>
</c:if>

<div class="panel">
    <div style="display:flex; justify-content:space-between; align-items:baseline; margin-bottom:var(--space-4);">
        <h2 style="margin:0; font-family:var(--font-display);">Pending verification</h2>
        <span class="field-hint"><c:out value="${pendingRequests.size()}"/> waiting</span>
    </div>

    <c:choose>
        <c:when test="${empty pendingRequests}">
            <div class="panel-empty">
                <p>No registrations are waiting on review. New Student/Teacher sign-ups from the public Registration page will appear here.</p>
            </div>
        </c:when>
        <c:otherwise>
            <div class="table-scroll">
                <table class="data-table">
                    <thead>
                        <tr>
                            <th>Name</th>
                            <th>Role</th>
                            <th>Login ID</th>
                            <th>Phone</th>
                            <th>Submitted</th>
                            <th></th>
                        </tr>
                    </thead>
                    <tbody>
                        <c:forEach items="${pendingRequests}" var="req">
                            <tr>
                                <td><c:out value="${req.fullName}"/></td>
                                <td><c:out value="${req.role}"/></td>
                                <td><code><c:out value="${req.identifier}"/></code></td>
                                <td><c:out value="${req.phone}"/></td>
                                <td><c:out value="${req.submittedAt}"/></td>
                                <td><a href="${ctx}/admin/registrations/review?id=${req.id}" class="btn-secondary" style="padding:0.35rem 0.9rem; font-size:0.85rem;">Review</a></td>
                            </tr>
                        </c:forEach>
                    </tbody>
                </table>
            </div>
        </c:otherwise>
    </c:choose>
</div>

<div class="panel" style="margin-top:var(--space-6);">
    <h2 style="margin:0 0 var(--space-4); font-family:var(--font-display);">Review history</h2>
    <c:choose>
        <c:when test="${empty reviewedRequests}">
            <div class="panel-empty"><p>No registrations have been reviewed yet.</p></div>
        </c:when>
        <c:otherwise>
            <div class="table-scroll">
                <table class="data-table">
                    <thead>
                        <tr><th>Name</th><th>Role</th><th>Login ID</th><th>Status</th><th>Reviewed by</th><th>Remark</th></tr>
                    </thead>
                    <tbody>
                        <c:forEach items="${reviewedRequests}" var="req">
                            <tr>
                                <td><c:out value="${req.fullName}"/></td>
                                <td><c:out value="${req.role}"/></td>
                                <td><code><c:out value="${req.identifier}"/></code></td>
                                <td><span class="badge badge-status badge-status-${fn:toLowerCase(req.status)}"><c:out value="${req.status}"/></span></td>
                                <td><c:out value="${not empty req.reviewedBy ? req.reviewedBy.fullName : '&mdash;'}"/></td>
                                <td><c:out value="${req.adminRemark}"/></td>
                            </tr>
                        </c:forEach>
                    </tbody>
                </table>
            </div>
        </c:otherwise>
    </c:choose>
</div>

<%@ include file="/common/fragments/admin-foot.jspf" %>
