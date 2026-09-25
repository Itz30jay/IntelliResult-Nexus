<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="My Results" scope="request"/>
<c:set var="pageSubtitle" value="Every published examination result" scope="request"/>
<%@ include file="/common/fragments/student-head.jspf" %>

<c:choose>
    <c:when test="${empty resultsByExam}">
        <div class="panel">
            <p class="panel-empty">No published results available yet.</p>
        </div>
    </c:when>
    <c:otherwise>
        <%--
            Upgrade: "add a selector so the student can choose which exam /
            semester result to view" - previously every published exam was
            stacked on one page. Defaults to the most recent (see
            StudentResultServlet.resolveSelectedExamId) so the common case
            ("what's my latest result") needs no interaction at all.
        --%>
        <div class="panel" style="margin-bottom:var(--space-6);">
            <form method="get" action="${ctx}/student/results">
                <div class="field" style="margin-bottom:0;">
                    <label for="examId">Viewing result for</label>
                    <select id="examId" name="examId" onchange="this.form.submit();">
                        <c:forEach items="${resultsByExam}" var="entry">
                            <option value="${entry.key.id}" ${entry.key.id == selectedExamId ? 'selected' : ''}>
                                <c:out value="${entry.key.name}"/> (Sem <c:out value="${entry.key.semester.semesterNumber}"/>, <c:out value="${entry.key.semester.academicYear.label}"/>)
                            </option>
                        </c:forEach>
                    </select>
                </div>
            </form>
        </div>

        <c:forEach items="${resultsByExam}" var="entry">
            <c:if test="${entry.key.id == selectedExamId}">
            <c:set var="exam" value="${entry.key}"/>
            <c:set var="summary" value="${summariesByExamId[exam.id]}"/>
            <div class="panel" style="margin-bottom:var(--space-6);">
                <h2><c:out value="${exam.name}"/></h2>
                <c:if test="${not empty summary}">
                    <p style="color:var(--color-ink-soft); margin-bottom:var(--space-4);">
                        Overall <c:out value="${summary.overallPercentage}"/>% &mdash; Grade <c:out value="${summary.overallGrade}"/>
                        <c:if test="${not empty summary.sgpa}"> &mdash; SGPA <c:out value="${summary.sgpa}"/></c:if>
                        <c:if test="${not empty summary.classRank}"> &mdash; Class Rank <c:out value="${summary.classRank}"/></c:if>
                        &mdash; <span style="${summary.pass ? '' : 'color:var(--color-danger); font-weight:600;'}"><c:out value="${summary.pass ? 'Pass' : 'Fail'}"/></span>
                        <c:if test="${summary.subjectsCounted < summary.subjectsExpected}">
                            &mdash; <span style="color:var(--color-seal);">provisional (<c:out value="${summary.subjectsCounted}"/> of <c:out value="${summary.subjectsExpected}"/> subjects graded)</span>
                        </c:if>
                    </p>
                </c:if>

                <table class="display" style="width:100%">
                    <thead>
                    <tr><th>Subject</th><th>Theory</th><th>Practical</th><th>Internal</th><th>Total</th><th>Percentage</th><th>Grade</th><th>Grade Point</th></tr>
                    </thead>
                    <tbody>
                    <c:forEach items="${entry.value}" var="r">
                        <tr>
                            <td><c:out value="${r.subject.subjectCode}"/> &mdash; <c:out value="${r.subject.subjectName}"/></td>
                            <td><c:out value="${empty r.theoryMarks ? '&mdash;' : r.theoryMarks}"/></td>
                            <td><c:out value="${empty r.practicalMarks ? '&mdash;' : r.practicalMarks}"/></td>
                            <td><c:out value="${empty r.internalMarks ? '&mdash;' : r.internalMarks}"/></td>
                            <td><c:out value="${r.totalMarks}"/></td>
                            <td><c:out value="${r.percentage}"/>%</td>
                            <td><c:out value="${r.grade}"/></td>
                            <td><c:out value="${r.gradePoint}"/></td>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
            </div>
            </c:if>
        </c:forEach>
    </c:otherwise>
</c:choose>

<%@ include file="/common/fragments/student-foot.jspf" %>
