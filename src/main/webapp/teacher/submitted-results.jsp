<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="Submitted Results" scope="request"/>
<c:set var="pageSubtitle" value="Marks you've submitted for administrative review" scope="request"/>
<%@ include file="/common/fragments/teacher-head.jspf" %>

<%--
    Upgrade: previously this table showed only an aggregate "X of Y
    submitted" count per group - the underlying transition (a student's
    Result moving from DRAFT to SUBMITTED, and thereby off the Draft
    Results worklist and onto this one) was already correct, but the
    literal ask - a STUDENT visibly appearing here - wasn't really
    observable without clicking through to the grid. Each group row now
    expands to the actual submitted students, reusing the same
    toggleDetail() pattern admin/results.jsp already uses for its own
    per-student expansion.
--%>
<div class="panel">
    <c:choose>
        <c:when test="${empty groupRows}">
            <p class="panel-empty">Nothing submitted yet.</p>
        </c:when>
        <c:otherwise>
            <table id="submittedTable" class="display" style="width:100%">
                <thead>
                <tr>
                    <th>Exam</th>
                    <th>Subject</th>
                    <th>Section</th>
                    <th>Submitted</th>
                    <th></th>
                </tr>
                </thead>
                <tbody>
                <c:forEach items="${groupRows}" var="gr" varStatus="loop">
                    <c:set var="g" value="${gr.group}"/>
                    <tr>
                        <td><c:out value="${g.exam.name}"/></td>
                        <td><c:out value="${g.assignment.subject.subjectCode}"/> &mdash; <c:out value="${g.assignment.subject.subjectName}"/></td>
                        <td>Sec <c:out value="${g.assignment.section.name}"/></td>
                        <td><c:out value="${g.submittedOrLaterCount}"/> of <c:out value="${g.totalStudents}"/></td>
                        <td>
                            <a href="#" onclick="toggleDetail('detail-${loop.index}'); return false;">Show students</a> &middot;
                            <a href="${ctx}/teacher/marks-entry?examId=${g.exam.id}&subjectId=${g.assignment.subject.id}&sectionId=${g.assignment.section.id}"
                               style="text-decoration:none;">Open grid &rarr;</a>
                        </td>
                    </tr>
                    <tr id="detail-${loop.index}" style="display:none; background:var(--color-paper-soft);">
                        <td colspan="5" style="padding:var(--space-3) var(--space-4);">
                            <strong style="font-size:0.85rem;">Submitted students</strong>
                            <table style="width:100%; margin-top:var(--space-2); font-size:0.9rem;">
                                <thead><tr><th style="text-align:left;">Roll No.</th><th style="text-align:left;">Name</th><th style="text-align:left;">Total Marks</th><th style="text-align:left;">Grade</th><th style="text-align:left;">Status</th></tr></thead>
                                <tbody>
                                <c:forEach items="${gr.submittedRows}" var="row">
                                    <tr>
                                        <td><c:out value="${row.student.rollNo}"/></td>
                                        <td><c:out value="${row.student.user.fullName}"/></td>
                                        <td><c:out value="${row.existingResult.totalMarks}"/></td>
                                        <td><c:out value="${row.existingResult.grade}"/></td>
                                        <td><span class="badge badge-status badge-status-${fn:toLowerCase(row.existingResult.status)}"><c:out value="${row.existingResult.status}"/></span></td>
                                    </tr>
                                </c:forEach>
                                </tbody>
                            </table>
                        </td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </c:otherwise>
    </c:choose>
</div>

<script>
    $(document).ready(function() {
        if ($('#submittedTable tr').length) { $('#submittedTable').DataTable({ order: [], searching: false }); }
    });
    function toggleDetail(id) {
        var row = document.getElementById(id);
        row.style.display = (row.style.display === 'none') ? 'table-row' : 'none';
    }
</script>
<%@ include file="/common/fragments/teacher-foot.jspf" %>
