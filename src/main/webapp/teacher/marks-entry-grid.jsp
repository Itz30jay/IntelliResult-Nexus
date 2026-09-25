<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Marks Entry" scope="request"/>
<c:set var="pageSubtitle" value="${assignment.subject.subjectName} - Section ${assignment.section.name} - ${exam.name}" scope="request"/>
<%@ include file="/common/fragments/teacher-head.jspf" %>

<c:if test="${not empty errorMessage}">
    <div class="auth-error" role="alert" style="margin-bottom:var(--space-4);">
        <div>
            <strong><c:out value="${errorMessage}"/></strong>
            <c:if test="${not empty fieldErrors}">
                <ul style="margin:0.5rem 0 0 1.2rem; padding:0;">
                    <c:forEach items="${fieldErrors}" var="entry">
                        <li><c:out value="${entry.key}"/>: <c:out value="${entry.value}"/></li>
                    </c:forEach>
                </ul>
            </c:if>
        </div>
    </div>
</c:if>

<div class="panel" style="margin-bottom:var(--space-4);">
    <span class="badge badge-status badge-status-${exam.status.toString().toLowerCase()}"><c:out value="${exam.status}"/></span>
    <span class="field-hint" style="margin-left:var(--space-3);">
        <c:out value="${assignment.subject.subjectCode}"/> &mdash; Sem <c:out value="${assignment.subject.semester.semesterNumber}"/>,
        Section <c:out value="${assignment.section.name}"/>.
        <c:choose>
            <c:when test="${examOpenForEntry}">Rows already submitted are read-only (Sec. 10) - contact an administrator for a correction.</c:when>
            <c:otherwise>This exam is no longer open for marks entry - showing a read-only record of what was submitted.</c:otherwise>
        </c:choose>
    </span>
</div>

<div class="panel">
    <c:if test="${empty rows}">
        <p class="panel-empty">This section has no students yet.</p>
    </c:if>

    <c:if test="${not empty rows}">
    <form method="post" action="${ctx}/teacher/marks-entry" id="gridForm">
        <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
        <input type="hidden" name="examId" value="${exam.id}">
        <input type="hidden" name="subjectId" value="${assignment.subject.id}">
        <input type="hidden" name="sectionId" value="${assignment.section.id}">
        <input type="hidden" name="action" id="actionField" value="save">

        <table id="marksTable" style="width:100%;">
            <thead>
            <tr>
                <th style="text-align:left;">Roll No</th>
                <th style="text-align:left;">Name</th>
                <c:if test="${assignment.subject.hasTheory}"><th>Theory <span class="field-hint">(/<c:out value="${assignment.subject.theoryMaxMarks}"/>)</span></th></c:if>
                <c:if test="${assignment.subject.hasPractical}"><th>Practical <span class="field-hint">(/<c:out value="${assignment.subject.practicalMaxMarks}"/>)</span></th></c:if>
                <c:if test="${assignment.subject.hasInternal}"><th>Internal <span class="field-hint">(/<c:out value="${assignment.subject.internalMaxMarks}"/>)</span></th></c:if>
                <th>Total</th>
                <th>Status</th>
            </tr>
            </thead>
            <tbody>
            <c:forEach items="${rows}" var="row">
                <c:set var="sid" value="${row.student.id}"/>
                <c:set var="theoryKey" value="theoryMarks_${sid}"/>
                <c:set var="practicalKey" value="practicalMarks_${sid}"/>
                <c:set var="internalKey" value="internalMarks_${sid}"/>
                <tr class="${(!row.isEditable() || !examOpenForEntry) ? 'marks-row-readonly' : ''}">
                    <td><c:out value="${row.student.rollNo}"/></td>
                    <td><c:out value="${row.student.user.fullName}"/></td>
                    <c:if test="${assignment.subject.hasTheory}">
                        <td>
                            <input type="number" class="marks-input" min="0" max="${assignment.subject.theoryMaxMarks}" step="0.01"
                                   name="${theoryKey}" data-student="${sid}" oninput="updateTotal(${sid})"
                                   value="<c:out value='${not empty param[theoryKey] ? param[theoryKey] : row.existingResult.theoryMarks}'/>"
                                   ${(!row.isEditable() || !examOpenForEntry) ? 'disabled' : ''}>
                        </td>
                    </c:if>
                    <c:if test="${assignment.subject.hasPractical}">
                        <td>
                            <input type="number" class="marks-input" min="0" max="${assignment.subject.practicalMaxMarks}" step="0.01"
                                   name="${practicalKey}" data-student="${sid}" oninput="updateTotal(${sid})"
                                   value="<c:out value='${not empty param[practicalKey] ? param[practicalKey] : row.existingResult.practicalMarks}'/>"
                                   ${(!row.isEditable() || !examOpenForEntry) ? 'disabled' : ''}>
                        </td>
                    </c:if>
                    <c:if test="${assignment.subject.hasInternal}">
                        <td>
                            <input type="number" class="marks-input" min="0" max="${assignment.subject.internalMaxMarks}" step="0.01"
                                   name="${internalKey}" data-student="${sid}" oninput="updateTotal(${sid})"
                                   value="<c:out value='${not empty param[internalKey] ? param[internalKey] : row.existingResult.internalMarks}'/>"
                                   ${(!row.isEditable() || !examOpenForEntry) ? 'disabled' : ''}>
                        </td>
                    </c:if>
                    <td><span class="live-total" data-student="${sid}">&mdash;</span></td>
                    <td>
                        <c:choose>
                            <c:when test="${row.hasExistingResult()}">
                                <span class="badge badge-status badge-status-${row.existingResult.status.toString().toLowerCase()}"><c:out value="${row.existingResult.status}"/></span>
                            </c:when>
                            <c:otherwise><span class="badge badge-priority-normal">NOT ENTERED</span></c:otherwise>
                        </c:choose>
                    </td>
                </tr>
            </c:forEach>
            </tbody>
        </table>

        <c:if test="${examOpenForEntry}">
        <div style="display:flex; gap:var(--space-3); margin-top:var(--space-6);">
            <button type="button" onclick="submitAction('save');" class="btn-secondary" style="width:auto; padding:0.65rem 1.5rem;">Save Draft</button>
            <button type="button" onclick="confirmSubmit();" class="btn-primary" style="width:auto; padding:0.65rem 1.5rem;">Submit for Review</button>
        </div>
        </c:if>
    </form>
    </c:if>
</div>

<script>
    // Live total (Sec. 29's "auto total calculation") - computed here in
    // JS, purely for on-screen feedback, and deliberately never sent to
    // the server as a value the backend trusts: total_marks is set
    // exclusively by ResultCalculationService (Phase 7), never by this
    // screen. See MarksEntryService's own class Javadoc for the full
    // reasoning.
    function updateTotal(studentId) {
        var inputs = document.querySelectorAll('.marks-input[data-student="' + studentId + '"]');
        var total = 0;
        var any = false;
        inputs.forEach(function (el) {
            if (el.value !== '') { total += parseFloat(el.value) || 0; any = true; }
        });
        var span = document.querySelector('.live-total[data-student="' + studentId + '"]');
        if (span) { span.textContent = any ? total.toFixed(2) : '\u2014'; }
    }

    document.querySelectorAll('.live-total').forEach(function (span) {
        updateTotal(span.getAttribute('data-student'));
    });

    function submitAction(action) {
        document.getElementById('actionField').value = action;
        document.getElementById('gridForm').submit();
    }

    // Confirms before Submit specifically, not Save Draft: submitting
    // closes off editing for every currently-drafted row in this grid
    // (Sec. 10), the same "confirm before a consequential workflow move"
    // treatment Phase 5e's exam-status advance gets. Counts against the
    // form's live DOM state (whatever is filled in right now, whether
    // pre-existing or just typed), not a server round-trip.
    function confirmSubmit() {
        var rows = document.querySelectorAll('#marksTable tbody tr').length;
        var withMarks = 0;
        document.querySelectorAll('#marksTable tbody tr').forEach(function (row) {
            var hasValue = false;
            row.querySelectorAll('.marks-input').forEach(function (el) { if (el.value !== '') { hasValue = true; } });
            if (hasValue) { withMarks++; }
        });
        var text = withMarks < rows
            ? withMarks + ' of ' + rows + ' students have marks entered. The rest will remain unsubmitted.'
            : 'All ' + rows + ' students have marks entered.';
        Swal.fire({
            title: 'Submit for administrative review?', text: text,
            icon: 'question', showCancelButton: true, confirmButtonText: 'Submit', confirmButtonColor: '#B8925A'
        }).then(function (r) { if (r.isConfirmed) { submitAction('submit'); } });
    }

    <c:if test="${not empty successMessage}">toastr.success('<c:out value="${successMessage}"/>');</c:if>
</script>
<%@ include file="/common/fragments/teacher-foot.jspf" %>
