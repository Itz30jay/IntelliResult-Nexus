<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Draft Results" scope="request"/>
<c:set var="pageSubtitle" value="Marks entered but not yet submitted" scope="request"/>
<%@ include file="/common/fragments/teacher-head.jspf" %>

<div class="panel">
    <c:choose>
        <c:when test="${empty groups}">
            <p class="panel-empty">No drafts in progress. Start from <a href="${ctx}/teacher/marks-entry">Marks Entry</a>.</p>
        </c:when>
        <c:otherwise>
            <table id="draftsTable" class="display" style="width:100%">
                <thead>
                <tr>
                    <th>Exam</th>
                    <th>Subject</th>
                    <th>Section</th>
                    <th>Drafted</th>
                    <th></th>
                </tr>
                </thead>
                <tbody>
                <c:forEach items="${groups}" var="g">
                    <tr>
                        <td><c:out value="${g.exam.name}"/></td>
                        <td><c:out value="${g.assignment.subject.subjectCode}"/> &mdash; <c:out value="${g.assignment.subject.subjectName}"/></td>
                        <td>Sec <c:out value="${g.assignment.section.name}"/></td>
                        <td><c:out value="${g.draftCount}"/> of <c:out value="${g.totalStudents}"/></td>
                        <td>
                            <a href="${ctx}/teacher/marks-entry?examId=${g.exam.id}&subjectId=${g.assignment.subject.id}&sectionId=${g.assignment.section.id}"
                               style="text-decoration:none;">Continue Entry &rarr;</a>
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
        if ($('#draftsTable tr').length) { $('#draftsTable').DataTable({ order: [] }); }
    });
</script>
<%@ include file="/common/fragments/teacher-foot.jspf" %>
