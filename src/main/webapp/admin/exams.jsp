<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="Exams" scope="request"/>
<c:set var="pageSubtitle" value="Examinations" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<div style="display:flex; justify-content:flex-end; margin-bottom: var(--space-4);">
    <a href="${ctx}/admin/exams/new" class="btn-primary" style="width:auto; text-decoration:none; display:inline-block; padding:0.6rem 1.2rem;">+ Add Exam</a>
</div>

<div class="panel">
    <c:choose>
        <c:when test="${empty exams}">
            <p class="panel-empty">No examinations created yet. Click <strong>+ Add Exam</strong> to schedule the first one.</p>
        </c:when>
        <c:otherwise>
            <table id="examsTable" class="display" style="width:100%">
                <thead>
                <tr>
                    <th>Name</th>
                    <th>Type</th>
                    <th>Semester</th>
                    <th>Window</th>
                    <th>Ref. max marks</th>
                    <th>Status</th>
                    <th>Actions</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach items="${exams}" var="exam">
                    <tr>
                        <td><c:out value="${exam.name}"/></td>
                        <td><c:out value="${fn:replace(exam.examType, '_', ' ')}"/></td>
                        <td>Sem <c:out value="${exam.semester.semesterNumber}"/> &mdash; <c:out value="${exam.semester.course.code}"/> (<c:out value="${exam.semester.academicYear.label}"/>)</td>
                        <td>
                            <c:out value="${exam.startTime}"/> &rarr; <c:out value="${exam.endTime}"/>
                            <c:if test="${exam.attemptLimit > 1}"><br><span class="field-hint">Up to <c:out value="${exam.attemptLimit}"/> attempts</span></c:if>
                        </td>
                        <td><c:out value="${empty exam.defaultMaxMarks ? '&mdash;' : exam.defaultMaxMarks}"/></td>
                        <td><span class="badge badge-status badge-status-${fn:toLowerCase(exam.status)}"><c:out value="${exam.status}"/></span></td>
                        <td style="white-space:nowrap;">
                            <a href="${ctx}/admin/exams/edit?id=${exam.id}" style="margin-right:0.75rem;">
                                <c:choose>
                                    <c:when test="${exam.status.name() == 'PUBLISHED' || exam.status.name() == 'LOCKED'}">View</c:when>
                                    <c:otherwise>Edit</c:otherwise>
                                </c:choose>
                            </a>
                            <c:if test="${exam.status.name() != 'LOCKED'}">
                                <a href="#" onclick="confirmAdvance(${exam.id}, '<c:out value="${exam.name}"/>', '<c:out value="${exam.status}"/>'); return false;" style="margin-right:0.75rem; color:var(--color-seal);">Advance</a>
                            </c:if>
                            <c:if test="${exam.status.name() == 'CREATED'}">
                                <a href="#" onclick="confirmDelete(${exam.id}, '<c:out value="${exam.name}"/>'); return false;" style="color:var(--color-danger);">Delete</a>
                            </c:if>
                        </td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </c:otherwise>
    </c:choose>
</div>

<c:if test="${not empty deletedExams}">
<div class="panel" style="margin-top:var(--space-6);">
    <h2>Recycle Bin</h2>
    <c:forEach items="${deletedExams}" var="exam">
        <div class="list-row">
            <span><c:out value="${exam.name}"/> &mdash; Sem <c:out value="${exam.semester.semesterNumber}"/> <c:out value="${exam.semester.course.code}"/> (<c:out value="${exam.semester.academicYear.label}"/>)</span>
            <a href="#" onclick="restoreItem(${exam.id}); return false;">Restore</a>
        </div>
    </c:forEach>
</div>
</c:if>

<form id="deleteForm" method="post" action="${ctx}/admin/exams/delete" style="display:none;">
    <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
    <input type="hidden" name="id" id="deleteFormId">
</form>
<form id="restoreForm" method="post" action="${ctx}/admin/exams/restore" style="display:none;">
    <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
    <input type="hidden" name="id" id="restoreFormId">
</form>
<form id="advanceForm" method="post" action="${ctx}/admin/exams/advance-status" style="display:none;">
    <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
    <input type="hidden" name="id" id="advanceFormId">
</form>

<script>
    $(document).ready(function() { $('#examsTable').DataTable({ order: [[3, 'desc']] }); });

    function confirmDelete(id, name) {
        Swal.fire({
            title: 'Delete exam?', text: name + ' moves to the recycle bin and can be restored later.',
            icon: 'warning', showCancelButton: true, confirmButtonText: 'Delete', confirmButtonColor: '#A3384A'
        }).then((r) => { if (r.isConfirmed) { document.getElementById('deleteFormId').value = id; document.getElementById('deleteForm').submit(); } });
    }
    function restoreItem(id) {
        document.getElementById('restoreFormId').value = id;
        document.getElementById('restoreForm').submit();
    }
    // Sec. 22's "confirm before dangerous/important operations" applied to
    // a single-exam workflow move, not just bulk ones: advancing status is
    // a one-way door for every stage except reversing out of a typo, so it
    // gets the same confirm-first treatment as delete, worded around what
    // actually happens next rather than a generic "are you sure?".
    function confirmAdvance(id, name, currentStatus) {
        Swal.fire({
            title: 'Advance ' + name + '?',
            text: 'Moves this exam from ' + currentStatus + ' to the next stage of its lifecycle.',
            icon: 'question', showCancelButton: true, confirmButtonText: 'Advance', confirmButtonColor: '#B8925A'
        }).then((r) => { if (r.isConfirmed) { document.getElementById('advanceFormId').value = id; document.getElementById('advanceForm').submit(); } });
    }
    <c:if test="${not empty errorMessage}">toastr.error('<c:out value="${errorMessage}"/>');</c:if>
    <c:if test="${not empty successMessage}">toastr.success('<c:out value="${successMessage}"/>');</c:if>
</script>
<%@ include file="/common/fragments/admin-foot.jspf" %>
