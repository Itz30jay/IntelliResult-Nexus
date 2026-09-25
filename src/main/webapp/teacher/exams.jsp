<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="Exams" scope="request"/>
<c:set var="pageSubtitle" value="Exams for your assigned subjects" scope="request"/>
<%@ include file="/common/fragments/teacher-head.jspf" %>

<div class="panel">
    <c:choose>
        <c:when test="${empty exams}">
            <p class="panel-empty">No exams found for your assigned subjects yet.</p>
        </c:when>
        <c:otherwise>
            <table id="examsTable" class="display" style="width:100%">
                <thead>
                <tr>
                    <th>Name</th>
                    <th>Type</th>
                    <th>Semester</th>
                    <th>Window</th>
                    <th>Status</th>
                    <th></th>
                </tr>
                </thead>
                <tbody>
                <c:forEach items="${exams}" var="exam">
                    <tr>
                        <td><c:out value="${exam.name}"/></td>
                        <td><c:out value="${fn:replace(exam.examType, '_', ' ')}"/></td>
                        <td>Sem <c:out value="${exam.semester.semesterNumber}"/> &mdash; <c:out value="${exam.semester.course.code}"/></td>
                        <td><c:out value="${exam.startTime}"/> &rarr; <c:out value="${exam.endTime}"/></td>
                        <td><span class="badge badge-status badge-status-${fn:toLowerCase(exam.status)}"><c:out value="${exam.status}"/></span></td>
                        <td>
                            <%-- Links to the picker, not a specific grid: one
                                 exam can match more than one of this
                                 teacher's assignments (e.g. two subjects in
                                 the same semester), so there's no single
                                 correct grid to deep-link to from here - the
                                 picker already disambiguates that cleanly. --%>
                            <c:if test="${exam.status.isOpenForMarksEntry()}">
                                <a href="${ctx}/teacher/marks-entry" style="text-decoration:none;">Enter Marks &rarr;</a>
                            </c:if>
                        </td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </c:otherwise>
    </c:choose>
</div>

<script>
    $(document).ready(function() { $('#examsTable').DataTable({ order: [[3, 'desc']] }); });
</script>
<%@ include file="/common/fragments/teacher-foot.jspf" %>
