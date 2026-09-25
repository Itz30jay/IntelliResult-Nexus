<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="Result Approvals" scope="request"/>
<c:set var="pageSubtitle" value="Approve, publish, and lock examination results" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<c:choose>
    <c:when test="${empty exams}">
        <div class="panel">
            <p class="panel-empty">No examinations exist yet. Create one under <a href="${ctx}/admin/exams">Exams</a> first.</p>
        </div>
    </c:when>
    <c:otherwise>
        <div class="panel" style="margin-bottom:var(--space-6);">
            <label for="examSelect" style="font-weight:600; margin-right:0.75rem;">Examination</label>
            <select id="examSelect" onchange="location.href='${ctx}/admin/approvals?examId=' + this.value;">
                <c:forEach items="${exams}" var="exam">
                    <option value="${exam.id}" ${exam.id == selectedExamId ? 'selected' : ''}>
                        <c:out value="${exam.name}"/> &mdash; <c:out value="${fn:replace(exam.examType, '_', ' ')}"/>
                        (<c:out value="${exam.semester.academicYear.label}"/>)
                    </option>
                </c:forEach>
            </select>
        </div>

        <%-- ============================================================ Bulk Actions (Phase 13, Sec. 22) --%>
        <div class="panel" style="margin-bottom:var(--space-6);">
            <h2>Bulk Actions</h2>
            <p style="color:var(--color-ink-soft); font-size:0.9rem; margin-top:-0.5rem;">
                Select several examinations and publish or lock them all at once. Each one is evaluated independently -
                an exam with nothing ready yet is skipped and reported, the rest still go through.
            </p>
            <form id="bulkActionForm" method="post" action="${ctx}/admin/approvals/bulk-publish">
                <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
                <table id="bulkExamTable" class="display" style="width:100%">
                    <thead>
                    <tr>
                        <th><input type="checkbox" id="selectAllBulk" onclick="toggleAll(this, 'bulkExamTable')"></th>
                        <th>Examination</th>
                        <th>Type</th>
                        <th>Academic Year</th>
                    </tr>
                    </thead>
                    <tbody>
                    <c:forEach items="${exams}" var="bulkExam">
                        <tr>
                            <td><input type="checkbox" name="examIds" value="${bulkExam.id}" class="rowCheck"></td>
                            <td><c:out value="${bulkExam.name}"/></td>
                            <td><c:out value="${fn:replace(bulkExam.examType, '_', ' ')}"/></td>
                            <td><c:out value="${bulkExam.semester.academicYear.label}"/></td>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
                <div style="margin-top:var(--space-4); display:flex; gap:var(--space-3);">
                    <button type="button" class="btn-primary" style="width:auto; padding:0.6rem 1.2rem;" onclick="confirmBulk('publish')">Bulk Publish Selected</button>
                    <button type="button" class="btn-secondary" style="width:auto; padding:0.6rem 1.2rem;" onclick="confirmBulk('lock')">Bulk Lock Selected</button>
                </div>
            </form>
        </div>

        <%-- ============================================================ Pending Approval --%>
        <div class="panel" style="margin-bottom:var(--space-6);">
            <h2>Pending Approval <span style="font-weight:400; color:var(--color-ink-soft);">(<c:out value="${fn:length(pendingResults)}"/>)</span></h2>
            <c:choose>
                <c:when test="${empty pendingResults}">
                    <p class="panel-empty">No submitted results are waiting on approval for this examination.</p>
                </c:when>
                <c:otherwise>
                    <form id="approveForm" method="post" action="${ctx}/admin/approvals/approve">
                        <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
                        <input type="hidden" name="examId" value="${selectedExamId}">
                        <table id="pendingTable" class="display" style="width:100%">
                            <thead>
                            <tr>
                                <th><input type="checkbox" id="selectAllPending" onclick="toggleAll(this, 'pendingTable')"></th>
                                <th>Roll No.</th>
                                <th>Student</th>
                                <th>Subject</th>
                                <th>Theory</th>
                                <th>Practical</th>
                                <th>Internal</th>
                                <th>Submitted</th>
                            </tr>
                            </thead>
                            <tbody>
                            <c:forEach items="${pendingResults}" var="result">
                                <tr>
                                    <td><input type="checkbox" name="resultIds" value="${result.id}" class="rowCheck"></td>
                                    <td><c:out value="${result.student.rollNo}"/></td>
                                    <td><c:out value="${result.student.user.fullName}"/></td>
                                    <td><c:out value="${result.subject.subjectCode}"/> &mdash; <c:out value="${result.subject.subjectName}"/></td>
                                    <td><c:out value="${empty result.theoryMarks ? '&mdash;' : result.theoryMarks}"/></td>
                                    <td><c:out value="${empty result.practicalMarks ? '&mdash;' : result.practicalMarks}"/></td>
                                    <td><c:out value="${empty result.internalMarks ? '&mdash;' : result.internalMarks}"/></td>
                                    <td><c:out value="${result.submittedAt}"/></td>
                                </tr>
                            </c:forEach>
                            </tbody>
                        </table>
                        <div style="margin-top:var(--space-4);">
                            <button type="button" class="btn-primary" style="width:auto; padding:0.6rem 1.2rem;" onclick="confirmApprove();">Approve Selected</button>
                        </div>
                    </form>
                </c:otherwise>
            </c:choose>
        </div>

        <%-- ============================================================ Approved / ready to publish --%>
        <div class="panel" style="margin-bottom:var(--space-6);">
            <h2>Approved <span style="font-weight:400; color:var(--color-ink-soft);">(<c:out value="${fn:length(approvedResults)}"/>) &mdash; ready to publish</span></h2>
            <c:choose>
                <c:when test="${empty approvedResults}">
                    <p class="panel-empty">No approved results are waiting on publishing for this examination.</p>
                </c:when>
                <c:otherwise>
                    <table id="approvedTable" class="display" style="width:100%">
                        <thead>
                        <tr>
                            <th>Roll No.</th><th>Student</th><th>Subject</th><th>Total</th><th>Percentage</th><th>Grade</th><th>Result</th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach items="${approvedResults}" var="result">
                            <tr>
                                <td><c:out value="${result.student.rollNo}"/></td>
                                <td><c:out value="${result.student.user.fullName}"/></td>
                                <td><c:out value="${result.subject.subjectCode}"/></td>
                                <td><c:out value="${result.totalMarks}"/></td>
                                <td><c:out value="${result.percentage}"/>%</td>
                                <td><span class="badge">${result.grade}</span></td>
                                <td>
                                    <c:choose>
                                        <c:when test="${result.pass}"><span class="badge badge-status badge-status-published">PASS</span></c:when>
                                        <c:otherwise><span style="color:var(--color-danger); font-weight:600;">FAIL</span></c:otherwise>
                                    </c:choose>
                                </td>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>
                    <form id="publishForm" method="post" action="${ctx}/admin/approvals/publish" style="margin-top:var(--space-4);">
                        <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
                        <input type="hidden" name="examId" value="${selectedExamId}">
                        <button type="button" class="btn-primary" style="width:auto; padding:0.6rem 1.2rem;" onclick="confirmPublish();">Publish Exam</button>
                    </form>
                </c:otherwise>
            </c:choose>
        </div>

        <%-- ============================================================ Published / ready to lock --%>
        <div class="panel">
            <h2>Published <span style="font-weight:400; color:var(--color-ink-soft);">(<c:out value="${fn:length(publishedResults)}"/>) &mdash; ready to lock</span></h2>
            <c:choose>
                <c:when test="${empty publishedResults}">
                    <p class="panel-empty">No published results are waiting on locking for this examination.</p>
                </c:when>
                <c:otherwise>
                    <table id="publishedTable" class="display" style="width:100%">
                        <thead>
                        <tr>
                            <th>Roll No.</th><th>Student</th><th>Subject</th><th>Percentage</th><th>Grade</th><th>Published</th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach items="${publishedResults}" var="result">
                            <tr>
                                <td><c:out value="${result.student.rollNo}"/></td>
                                <td><c:out value="${result.student.user.fullName}"/></td>
                                <td><c:out value="${result.subject.subjectCode}"/></td>
                                <td><c:out value="${result.percentage}"/>%</td>
                                <td><span class="badge">${result.grade}</span></td>
                                <td><c:out value="${result.publishedAt}"/></td>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>
                    <form id="lockForm" method="post" action="${ctx}/admin/approvals/lock" style="margin-top:var(--space-4);">
                        <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
                        <input type="hidden" name="examId" value="${selectedExamId}">
                        <button type="button" class="btn-primary" style="width:auto; padding:0.6rem 1.2rem;" onclick="confirmLock();">Lock Exam</button>
                    </form>
                </c:otherwise>
            </c:choose>
        </div>
    </c:otherwise>
</c:choose>

<script>
    $(document).ready(function () {
        // paging/search only - sorting the checkbox column makes selection state
        // confusing to track across pages, so it (column 0) is excluded here.
        if ($('#pendingTable').length) { $('#pendingTable').DataTable({ columnDefs: [{ orderable: false, targets: 0 }] }); }
        if ($('#approvedTable').length) { $('#approvedTable').DataTable(); }
        if ($('#publishedTable').length) { $('#publishedTable').DataTable(); }
        if ($('#bulkExamTable').length) { $('#bulkExamTable').DataTable({ columnDefs: [{ orderable: false, targets: 0 }] }); }
    });

    function toggleAll(source, tableId) {
        document.querySelectorAll('#' + tableId + ' .rowCheck').forEach(function (cb) { cb.checked = source.checked; });
    }

    // Sec. 22: SweetAlert2 confirmation before every bulk/workflow-moving
    // action here, worded around the actual consequence rather than a
    // generic "are you sure?" - matching exams.jsp's confirmAdvance().
    function confirmApprove() {
        var selected = document.querySelectorAll('#pendingTable .rowCheck:checked').length;
        if (selected === 0) { toastr.warning('Select at least one result to approve.'); return; }
        Swal.fire({
            title: 'Approve ' + selected + ' result' + (selected === 1 ? '' : 's') + '?',
            text: 'Each selected result will be scored (percentage, grade, pass/fail) and marked Approved.',
            icon: 'question', showCancelButton: true, confirmButtonText: 'Approve', confirmButtonColor: '#B8925A'
        }).then((r) => { if (r.isConfirmed) { document.getElementById('approveForm').submit(); } });
    }
    function confirmPublish() {
        Swal.fire({
            title: 'Publish this exam?',
            text: 'Every approved result becomes visible to its student, and overall percentage, SGPA, and rank are calculated across the whole exam.',
            icon: 'question', showCancelButton: true, confirmButtonText: 'Publish', confirmButtonColor: '#2F6F4E'
        }).then((r) => { if (r.isConfirmed) { document.getElementById('publishForm').submit(); } });
    }
    function confirmLock() {
        Swal.fire({
            title: 'Lock this exam?',
            text: 'Published results become immutable. Any further change will require the re-evaluation process.',
            icon: 'warning', showCancelButton: true, confirmButtonText: 'Lock', confirmButtonColor: '#A3384A'
        }).then((r) => { if (r.isConfirmed) { document.getElementById('lockForm').submit(); } });
    }

    // Sec. 22's bulk actions - one shared form, its action swapped to
    // /bulk-publish or /bulk-lock depending on which button was pressed,
    // rather than two near-identical forms differing only in a URL.
    function confirmBulk(action) {
        var selected = document.querySelectorAll('#bulkExamTable .rowCheck:checked').length;
        if (selected === 0) { toastr.warning('Select at least one examination first.'); return; }
        var isPublish = action === 'publish';
        Swal.fire({
            title: (isPublish ? 'Publish ' : 'Lock ') + selected + ' exam' + (selected === 1 ? '' : 's') + '?',
            text: isPublish
                ? 'Each selected exam\'s approved results become visible to students. Exams with nothing approved yet are skipped.'
                : 'Each selected exam\'s published results become immutable. Exams with nothing published yet are skipped.',
            icon: isPublish ? 'question' : 'warning', showCancelButton: true,
            confirmButtonText: isPublish ? 'Publish' : 'Lock',
            confirmButtonColor: isPublish ? '#2F6F4E' : '#A3384A'
        }).then((r) => {
            if (r.isConfirmed) {
                document.getElementById('bulkActionForm').action = '${ctx}/admin/approvals/' + (isPublish ? 'bulk-publish' : 'bulk-lock');
                document.getElementById('bulkActionForm').submit();
            }
        });
    }

    <c:if test="${not empty errorMessage}">toastr.error('<c:out value="${errorMessage}"/>');</c:if>
    <c:if test="${not empty successMessage}">toastr.success('<c:out value="${successMessage}"/>');</c:if>
</script>
<%@ include file="/common/fragments/admin-foot.jspf" %>
