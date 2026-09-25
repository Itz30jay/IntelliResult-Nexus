<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="Re-evaluation Requests" scope="request"/>
<c:set var="pageSubtitle" value="Review student requests and assign them to a teacher" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<div class="panel" style="margin-bottom:var(--space-6);">
    <label for="statusSelect" style="font-weight:600; margin-right:0.75rem;">Status</label>
    <select id="statusSelect" onchange="location.href='${ctx}/admin/revaluations?status=' + this.value;">
        <option value="PENDING" ${statusFilter == 'PENDING' ? 'selected' : ''}>Pending</option>
        <option value="ASSIGNED" ${statusFilter == 'ASSIGNED' ? 'selected' : ''}>Assigned</option>
        <option value="APPROVED" ${statusFilter == 'APPROVED' ? 'selected' : ''}>Approved</option>
        <option value="REJECTED" ${statusFilter == 'REJECTED' ? 'selected' : ''}>Rejected</option>
        <option value="ALL" ${statusFilter == 'ALL' ? 'selected' : ''}>All</option>
    </select>
</div>

<div class="panel">
    <c:choose>
        <c:when test="${empty requests}">
            <p class="panel-empty">No re-evaluation requests found<c:if test="${statusFilter != 'ALL'}"> with status <c:out value="${statusFilter}"/></c:if>.</p>
        </c:when>
        <c:otherwise>
            <table id="requestsTable" class="display" style="width:100%">
                <thead>
                <tr><th>Roll No.</th><th>Student</th><th>Exam</th><th>Subject</th><th>Status</th><th>Assigned To</th><th>Requested</th><th></th></tr>
                </thead>
                <tbody>
                <c:forEach items="${requests}" var="req">
                    <tr>
                        <td><c:out value="${req.student.rollNo}"/></td>
                        <td><c:out value="${req.student.user.fullName}"/></td>
                        <td><c:out value="${req.result.exam.name}"/></td>
                        <td><c:out value="${req.result.subject.subjectCode}"/></td>
                        <td>
                            <c:choose>
                                <c:when test="${req.status == 'PENDING'}"><span class="badge badge-status badge-status-pending">PENDING</span></c:when>
                                <c:when test="${req.status == 'ASSIGNED'}"><span class="badge badge-status badge-status-assigned">ASSIGNED</span></c:when>
                                <c:when test="${req.status == 'APPROVED'}"><span class="badge badge-status badge-status-approved">APPROVED</span></c:when>
                                <c:otherwise><span class="badge badge-status badge-status-rejected">REJECTED</span></c:otherwise>
                            </c:choose>
                        </td>
                        <td><c:out value="${not empty req.assignedTeacher ? req.assignedTeacher.user.fullName : '-'}"/></td>
                        <td><c:out value="${req.requestedAt}"/></td>
                        <td>
                            <c:choose>
                                <c:when test="${req.status == 'PENDING'}"><a href="${ctx}/admin/revaluations/assign?requestId=${req.id}">Assign</a></c:when>
                                <c:otherwise><a href="${ctx}/admin/revaluations/assign?requestId=${req.id}">View</a></c:otherwise>
                            </c:choose>
                        </td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </c:otherwise>
    </c:choose>
</div>

<script>
    $(document).ready(function () {
        if ($('#requestsTable').length) { $('#requestsTable').DataTable({ order: [[6, 'desc']] }); }
    });
    <c:if test="${not empty errorMessage}">toastr.error('<c:out value="${errorMessage}"/>');</c:if>
    <c:if test="${not empty successMessage}">toastr.success('<c:out value="${successMessage}"/>');</c:if>
</script>
<%@ include file="/common/fragments/admin-foot.jspf" %>
