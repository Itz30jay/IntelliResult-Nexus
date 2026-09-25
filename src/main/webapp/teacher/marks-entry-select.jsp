<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Marks Entry" scope="request"/>
<c:set var="pageSubtitle" value="Select a subject, section, and exam" scope="request"/>
<%@ include file="/common/fragments/teacher-head.jspf" %>

<div class="panel">
    <c:choose>
        <c:when test="${empty groups}">
            <p class="panel-empty">
                No exams are currently open for marks entry. Once an administrator advances an exam to Active or
                Submission for one of your assigned subjects, it will appear here.
            </p>
        </c:when>
        <c:otherwise>
            <table id="pickerTable" class="display" style="width:100%">
                <thead>
                <tr>
                    <th>Exam</th>
                    <th>Subject</th>
                    <th>Section</th>
                    <th>Status</th>
                    <th>Progress</th>
                    <th></th>
                </tr>
                </thead>
                <tbody>
                <c:forEach items="${groups}" var="g">
                    <tr>
                        <td><c:out value="${g.exam.name}"/></td>
                        <td><c:out value="${g.assignment.subject.subjectCode}"/> &mdash; <c:out value="${g.assignment.subject.subjectName}"/></td>
                        <td>Sec <c:out value="${g.assignment.section.name}"/></td>
                        <td><span class="badge badge-status badge-status-${g.exam.status.toString().toLowerCase()}"><c:out value="${g.exam.status}"/></span></td>
                        <td><c:out value="${g.enteredCount()}"/> of <c:out value="${g.totalStudents}"/> entered</td>
                        <td>
                            <a href="${ctx}/teacher/marks-entry?examId=${g.exam.id}&subjectId=${g.assignment.subject.id}&sectionId=${g.assignment.section.id}"
                               style="text-decoration:none;">Enter Marks &rarr;</a>
                            &nbsp;|&nbsp;
                            <a href="${ctx}/teacher/marks-entry/import?examId=${g.exam.id}&subjectId=${g.assignment.subject.id}&sectionId=${g.assignment.section.id}"
                               style="text-decoration:none;">Import Excel</a>
                            &nbsp;|&nbsp;
                            <a href="${ctx}/teacher/reports/view?type=CLASS_RESULT&examId=${g.exam.id}&subjectId=${g.assignment.subject.id}&sectionId=${g.assignment.section.id}"
                               target="_blank" style="text-decoration:none;">Class Report</a>
                            &nbsp;|&nbsp;
                            <a href="${ctx}/teacher/reports/view?type=SUBJECT_ANALYSIS&examId=${g.exam.id}&subjectId=${g.assignment.subject.id}&sectionId=${g.assignment.section.id}"
                               target="_blank" style="text-decoration:none;">Subject Report</a>
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
        if ($('#pickerTable tr').length) { $('#pickerTable').DataTable({ order: [] }); }
    });
    <c:if test="${not empty errorMessage}">toastr.error('<c:out value="${errorMessage}"/>');</c:if>
    <c:if test="${not empty successMessage}">toastr.success('<c:out value="${successMessage}"/>');</c:if>
</script>
<%@ include file="/common/fragments/teacher-foot.jspf" %>
