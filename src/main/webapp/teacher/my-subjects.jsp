<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="My Subjects" scope="request"/>
<c:set var="pageSubtitle" value="Subjects you teach, and to which sections" scope="request"/>
<%@ include file="/common/fragments/teacher-head.jspf" %>

<div class="panel">
    <c:choose>
        <c:when test="${empty assignments}">
            <p class="panel-empty">No active assignments yet. An administrator assigns you to a subject and section under Teacher Assignments.</p>
        </c:when>
        <c:otherwise>
            <%-- Same assignment data as my-classes.jsp - see that page's
                 own note on why this stays a flat, sortable table rather
                 than a grouped one. Subject-first column order and default
                 sort is the only real difference: same rows, framed around
                 "which subjects do I teach" instead of "which classes." --%>
            <table id="subjectsTable" class="display" style="width:100%">
                <thead>
                <tr>
                    <th>Subject</th>
                    <th>Credits</th>
                    <th>Section</th>
                    <th>Course</th>
                    <th>Assigned since</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach items="${assignments}" var="a">
                    <tr>
                        <td><c:out value="${a.subject.subjectCode}"/> &mdash; <c:out value="${a.subject.subjectName}"/></td>
                        <td><c:out value="${a.subject.credits}"/></td>
                        <td>Sec <c:out value="${a.section.name}"/></td>
                        <td>Sem <c:out value="${a.subject.semester.semesterNumber}"/> &mdash; <c:out value="${a.subject.semester.course.code}"/></td>
                        <td><c:out value="${a.assignedAt.toLocalDate()}"/></td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </c:otherwise>
    </c:choose>
</div>

<script>
    $(document).ready(function() {
        if ($('#subjectsTable tr').length) { $('#subjectsTable').DataTable({ order: [[0, 'asc']] }); }
    });
</script>
<%@ include file="/common/fragments/teacher-foot.jspf" %>
