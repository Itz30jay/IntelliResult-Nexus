<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="Re-evaluation" scope="request"/>
<c:set var="pageSubtitle" value="Request a second look at one of your published results" scope="request"/>
<%@ include file="/common/fragments/student-head.jspf" %>

<div class="panel" style="margin-bottom:var(--space-6);">
    <h2>Request Re-evaluation</h2>
    <c:choose>
        <c:when test="${empty eligibleResults}">
            <p class="panel-empty">No published results are available to request re-evaluation for yet.</p>
        </c:when>
        <c:otherwise>
            <form id="requestForm" method="post" action="${ctx}/student/revaluation">
                <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">

                <label for="resultSelect">Result</label>
                <select id="resultSelect" name="resultId" required style="margin-bottom:var(--space-3);">
                    <option value="">Select a result&hellip;</option>
                    <c:forEach items="${eligibleResults}" var="r">
                        <option value="${r.id}">
                            <c:out value="${r.exam.name}"/> &mdash; <c:out value="${r.subject.subjectCode}"/>
                            <c:if test="${not empty r.percentage}"> (<c:out value="${r.percentage}"/>%, <c:out value="${r.grade}"/>)</c:if>
                        </option>
                    </c:forEach>
                </select>

                <label for="reason">Reason <span style="color:var(--color-danger);">*</span></label>
                <textarea id="reason" name="reason" rows="3" required
                          placeholder="Why do you believe this result should be reviewed?"
                          style="margin-bottom:var(--space-4);"></textarea>

                <button type="button" class="btn-primary" style="width:auto; padding:0.6rem 1.2rem;" onclick="confirmSubmitRequest();">Submit Request</button>
            </form>
        </c:otherwise>
    </c:choose>
</div>

<div class="panel">
    <h2>My Requests <span style="font-weight:400; color:var(--color-ink-soft);">(<c:out value="${fn:length(myRequests)}"/>)</span></h2>
    <c:choose>
        <c:when test="${empty myRequests}">
            <p class="panel-empty">You haven't submitted any re-evaluation requests yet.</p>
        </c:when>
        <c:otherwise>
            <table id="requestsTable" class="display" style="width:100%">
                <thead>
                <tr><th>Exam</th><th>Subject</th><th>Reason</th><th>Status</th><th>Evaluator</th><th>Report</th><th>Requested</th></tr>
                </thead>
                <tbody>
                <c:forEach items="${myRequests}" var="req">
                    <tr>
                        <td><c:out value="${req.result.exam.name}"/></td>
                        <td><c:out value="${req.result.subject.subjectCode}"/></td>
                        <td><c:out value="${req.reason}"/></td>
                        <td>
                            <c:choose>
                                <c:when test="${req.status == 'PENDING'}"><span class="badge badge-status badge-status-pending">PENDING</span></c:when>
                                <c:when test="${req.status == 'ASSIGNED'}"><span class="badge badge-status badge-status-assigned">UNDER REVIEW</span></c:when>
                                <c:when test="${req.status == 'APPROVED'}"><span class="badge badge-status badge-status-approved">APPROVED</span></c:when>
                                <c:otherwise><span class="badge badge-status badge-status-rejected">REJECTED</span></c:otherwise>
                            </c:choose>
                        </td>
                        <td><c:out value="${not empty req.assignedTeacher ? req.assignedTeacher.user.fullName : '&mdash;'}"/></td>
                        <td><c:out value="${empty req.teacherReport ? '&mdash;' : req.teacherReport}"/></td>
                        <td><c:out value="${req.requestedAt}"/></td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </c:otherwise>
    </c:choose>
</div>

<script>
    $(document).ready(function () {
        if ($('#requestsTable').length) { $('#requestsTable').DataTable({ order: [[5, 'desc']] }); }
    });

    function confirmSubmitRequest() {
        var resultId = document.getElementById('resultSelect').value;
        var reason = document.getElementById('reason').value.trim();
        if (!resultId) { toastr.warning('Select a result first.'); return; }
        if (!reason) { toastr.warning('A reason is required.'); return; }
        Swal.fire({
            title: 'Submit this request?',
            text: 'An administrator will review it and you\'ll see the outcome here once resolved.',
            icon: 'question', showCancelButton: true, confirmButtonText: 'Submit', confirmButtonColor: '#B8925A'
        }).then((r) => { if (r.isConfirmed) { document.getElementById('requestForm').submit(); } });
    }

    <c:if test="${not empty errorMessage}">toastr.error('<c:out value="${errorMessage}"/>');</c:if>
    <c:if test="${not empty successMessage}">toastr.success('<c:out value="${successMessage}"/>');</c:if>
</script>
<%@ include file="/common/fragments/student-foot.jspf" %>
