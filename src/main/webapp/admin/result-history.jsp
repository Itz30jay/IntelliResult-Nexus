<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="Result History" scope="request"/>
<c:set var="pageSubtitle" value="${result.student.rollNo} \u2014 ${result.subject.subjectCode} \u2014 ${result.exam.name}" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<div class="panel" style="margin-bottom:var(--space-6);">
    <h2>Current Result</h2>
    <table style="width:100%;">
        <tr><td style="font-weight:600; width:180px;">Student</td><td><c:out value="${result.student.user.fullName}"/> (<c:out value="${result.student.rollNo}"/>)</td></tr>
        <tr><td style="font-weight:600;">Subject</td><td><c:out value="${result.subject.subjectCode}"/> &mdash; <c:out value="${result.subject.subjectName}"/></td></tr>
        <tr><td style="font-weight:600;">Examination</td><td><c:out value="${result.exam.name}"/></td></tr>
        <tr><td style="font-weight:600;">Status</td><td><span class="badge badge-status badge-status-${fn:toLowerCase(result.status)}">${result.status}</span></td></tr>
        <tr><td style="font-weight:600;">Theory</td><td><c:out value="${empty result.theoryMarks ? 'not applicable' : result.theoryMarks}"/></td></tr>
        <tr><td style="font-weight:600;">Practical</td><td><c:out value="${empty result.practicalMarks ? 'not applicable' : result.practicalMarks}"/></td></tr>
        <tr><td style="font-weight:600;">Internal</td><td><c:out value="${empty result.internalMarks ? 'not applicable' : result.internalMarks}"/></td></tr>
        <tr><td style="font-weight:600;">Total / Percentage / Grade</td>
            <td><c:out value="${empty result.totalMarks ? 'not yet calculated' : result.totalMarks}"/>
                <c:if test="${not empty result.percentage}"> &mdash; <c:out value="${result.percentage}"/>% &mdash; <c:out value="${result.grade}"/></c:if></td></tr>
    </table>
</div>

<div class="panel" style="margin-bottom:var(--space-6);">
    <h2>Change History <span style="font-weight:400; color:var(--color-ink-soft);">(<c:out value="${fn:length(history)}"/>)</span></h2>
    <c:choose>
        <c:when test="${empty history}">
            <p class="panel-empty">No corrections have been made to this result. Marks are exactly as originally entered.</p>
        </c:when>
        <c:otherwise>
            <table id="historyTable" class="display" style="width:100%">
                <thead>
                <tr><th>Theory</th><th>Practical</th><th>Internal</th><th>Changed By</th><th>Reason</th><th>When</th></tr>
                </thead>
                <tbody>
                <c:forEach items="${history}" var="entry">
                    <tr>
                        <td><c:out value="${empty entry.oldTheoryMarks && empty entry.newTheoryMarks ? '&mdash;' : entry.oldTheoryMarks}"/> &rarr; <c:out value="${entry.newTheoryMarks}"/></td>
                        <td><c:out value="${empty entry.oldPracticalMarks && empty entry.newPracticalMarks ? '&mdash;' : entry.oldPracticalMarks}"/> &rarr; <c:out value="${entry.newPracticalMarks}"/></td>
                        <td><c:out value="${empty entry.oldInternalMarks && empty entry.newInternalMarks ? '&mdash;' : entry.oldInternalMarks}"/> &rarr; <c:out value="${entry.newInternalMarks}"/></td>
                        <td><c:out value="${entry.changedBy.fullName}"/></td>
                        <td><c:out value="${entry.changeReason}"/></td>
                        <td><c:out value="${entry.changedAt}"/></td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </c:otherwise>
    </c:choose>
</div>

<div class="panel">
    <h2>Make a Correction</h2>
    <p style="color:var(--color-ink-soft); margin-bottom:var(--space-4);">
        Available regardless of this result's current status, including Locked - Sec. 47's authorized correction
        path. Every correction is recorded above with the reason given and cannot be edited or removed afterward.
    </p>
    <form id="correctForm" method="post" action="${ctx}/admin/results/correct">
        <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
        <input type="hidden" name="resultId" value="${result.id}">

        <c:if test="${result.subject.hasTheory}">
            <label>Theory (max ${result.subject.theoryMaxMarks})</label>
            <input type="number" id="theoryMarks" name="theoryMarks" min="0" max="${result.subject.theoryMaxMarks}" step="0.01"
                   value="${result.theoryMarks}" style="margin-bottom:var(--space-3);">
        </c:if>
        <c:if test="${result.subject.hasPractical}">
            <label>Practical (max ${result.subject.practicalMaxMarks})</label>
            <input type="number" id="practicalMarks" name="practicalMarks" min="0" max="${result.subject.practicalMaxMarks}" step="0.01"
                   value="${result.practicalMarks}" style="margin-bottom:var(--space-3);">
        </c:if>
        <c:if test="${result.subject.hasInternal}">
            <label>Internal (max ${result.subject.internalMaxMarks})</label>
            <input type="number" id="internalMarks" name="internalMarks" min="0" max="${result.subject.internalMaxMarks}" step="0.01"
                   value="${result.internalMarks}" style="margin-bottom:var(--space-3);">
        </c:if>

        <label for="reason">Reason <span style="color:var(--color-danger);">*</span></label>
        <textarea id="reason" name="reason" rows="3" required
                  placeholder="Why is this result being corrected? This is recorded permanently alongside the change."
                  style="margin-bottom:var(--space-4);"></textarea>

        <button type="button" class="btn-primary" style="width:auto; padding:0.6rem 1.2rem;" onclick="confirmCorrect();">Save Correction</button>
    </form>
</div>

<script>
    $(document).ready(function () {
        if ($('#historyTable').length) { $('#historyTable').DataTable({ order: [[5, 'desc']] }); }
    });

    function confirmCorrect() {
        var reason = document.getElementById('reason').value.trim();
        if (!reason) { toastr.warning('A reason is required.'); return; }
        Swal.fire({
            title: 'Save this correction?',
            text: 'The current marks will be preserved in the history above, and this result will be recalculated with the new values.',
            icon: 'warning', showCancelButton: true, confirmButtonText: 'Save Correction', confirmButtonColor: '#A3384A'
        }).then((r) => { if (r.isConfirmed) { document.getElementById('correctForm').submit(); } });
    }

    <c:if test="${not empty errorMessage}">toastr.error('<c:out value="${errorMessage}"/>');</c:if>
    <c:if test="${not empty successMessage}">toastr.success('<c:out value="${successMessage}"/>');</c:if>
</script>
<%@ include file="/common/fragments/admin-foot.jspf" %>
