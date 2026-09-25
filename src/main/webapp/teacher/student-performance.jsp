<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Student Performance" scope="request"/>
<c:set var="pageSubtitle" value="Search a student in your classes for their full performance history" scope="request"/>
<%@ include file="/common/fragments/teacher-head.jspf" %>

<%--
    New page (upgrade pass) - Sec. "Student Performance (Teacher) - Make it
    fully working." The nav link existed with nothing behind it. Scoped to
    students in a section this teacher currently teaches
    (StudentAnalyticsService.searchStudentsTaughtBy) - the same boundary
    Marks Entry and My Classes already enforce.
--%>
<div class="panel" style="margin-bottom:var(--space-6);">
    <form method="get" action="${ctx}/teacher/student-performance" style="display:flex; gap:var(--space-3); align-items:flex-end;">
        <div class="field" style="margin-bottom:0; flex:1;">
            <label for="query">Search by name or roll number</label>
            <input type="text" id="query" name="query" autofocus placeholder="e.g. roll number or student name" value="<c:out value='${query}'/>" style="font-size:1.05rem; padding:0.75rem;">
        </div>
        <button type="submit" class="btn-primary" style="width:auto; padding:0.75rem 1.75rem;">Search</button>
    </form>
</div>

<div class="panel">
    <c:choose>
        <c:when test="${empty results}">
            <div class="panel-empty">
                <p><c:choose>
                    <c:when test="${not empty query}">No student in your classes matches "<c:out value="${query}"/>".</c:when>
                    <c:otherwise>You have no students in your assigned classes yet.</c:otherwise>
                </c:choose></p>
            </div>
        </c:when>
        <c:otherwise>
            <table class="display" style="width:100%">
                <thead><tr><th>Roll No.</th><th>Name</th><th>Course</th><th>Section</th><th></th></tr></thead>
                <tbody>
                <c:forEach items="${results}" var="s">
                    <tr>
                        <td><c:out value="${s.rollNo}"/></td>
                        <td><c:out value="${s.user.fullName}"/></td>
                        <td><c:out value="${s.course.code}"/></td>
                        <td><c:out value="${not empty s.currentSection ? s.currentSection.name : '-'}"/></td>
                        <td><a href="${ctx}/teacher/student-performance?studentId=${s.id}" class="btn-secondary" style="width:auto; padding:0.35rem 0.9rem; font-size:0.85rem; text-decoration:none; display:inline-block;">View performance</a></td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </c:otherwise>
    </c:choose>
</div>

<%@ include file="/common/fragments/teacher-foot.jspf" %>
