<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="${student.user.fullName}" scope="request"/>
<c:set var="pageSubtitle" value="${student.rollNo} &mdash; ${student.course.name}" scope="request"/>
<%@ include file="/common/fragments/teacher-head.jspf" %>

<%-- New page (upgrade pass) - reuses StudentAnalyticsService entirely (performanceTrend, subjectStrengthWeakness), the same analytics the student's own dashboard/analysis pages already compute, just presented to their teacher. --%>
<div style="margin-bottom:var(--space-4);">
    <a href="${ctx}/teacher/student-performance?query=${query}" style="color:var(--color-ink-soft); text-decoration:none; font-size:0.9rem;">&larr; Back to search</a>
</div>

<div class="stat-grid" style="margin-bottom:var(--space-6);">
    <div class="stat-card">
        <div class="label">Exams on record</div>
        <div class="value"><c:out value="${trend.size()}"/></div>
    </div>
    <c:if test="${not empty trend}">
        <div class="stat-card">
            <div class="label">Most recent %</div>
            <div class="value"><c:out value="${trend[trend.size()-1].overallPercentage}"/>%</div>
        </div>
        <div class="stat-card">
            <div class="label">Most recent grade</div>
            <div class="value"><c:out value="${trend[trend.size()-1].overallGrade}"/></div>
        </div>
    </c:if>
</div>

<c:choose>
<c:when test="${empty trend}">
    <div class="panel panel-empty"><p>No published exam results for this student yet.</p></div>
</c:when>
<c:otherwise>

<div class="panel" style="margin-bottom:var(--space-6);">
    <h2 style="margin:0 0 var(--space-4); font-family:var(--font-display);">Performance trend</h2>
    <canvas id="trendChart" height="90"></canvas>
</div>

<div class="panel" style="margin-bottom:var(--space-6);">
    <h2 style="margin:0 0 var(--space-4); font-family:var(--font-display);">All exams taken</h2>
    <div class="table-scroll">
    <table class="display" style="width:100%">
        <thead><tr><th>Exam</th><th>Semester</th><th>Marks</th><th>%</th><th>Grade</th><th>SGPA</th><th>Result</th></tr></thead>
        <tbody>
        <c:forEach items="${trend}" var="s">
            <tr>
                <td><c:out value="${s.exam.name}"/></td>
                <td>Sem <c:out value="${s.exam.semester.semesterNumber}"/></td>
                <td><c:out value="${s.totalObtainedMarks}"/> / <c:out value="${s.totalMaxMarks}"/></td>
                <td><c:out value="${s.overallPercentage}"/>%</td>
                <td><c:out value="${s.overallGrade}"/></td>
                <td><c:out value="${not empty s.sgpa ? s.sgpa : '-'}"/></td>
                <td>
                    <c:choose>
                        <c:when test="${s.pass}"><span class="badge" style="background:#EAF3EE;color:var(--color-verified);">PASS</span></c:when>
                        <c:otherwise><span class="badge" style="background:#FBEEEF;color:var(--color-danger);">FAIL</span></c:otherwise>
                    </c:choose>
                </td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
    </div>
</div>

<div class="panel" style="margin-bottom:var(--space-6);">
    <h2 style="margin:0 0 var(--space-2); font-family:var(--font-display);">Subject strengths &amp; improvement areas</h2>
    <p class="field-hint" style="margin-top:0;">Ranked by average percentage across every exam - the top subjects are strengths, the bottom are where this student needs the most support.</p>
    <div class="table-scroll">
    <table class="display" style="width:100%">
        <thead><tr><th>Subject</th><th>Average %</th><th>Exams</th><th>Trend</th></tr></thead>
        <tbody>
        <c:forEach items="${insights}" var="i">
            <tr>
                <td><c:out value="${i.subjectCode}"/> &mdash; <c:out value="${i.subjectName}"/></td>
                <td><c:out value="${i.averagePercentage}"/>%</td>
                <td><c:out value="${i.examCount}"/></td>
                <td>
                    <c:choose>
                        <c:when test="${empty i.trend}"><span style="color:var(--color-ink-soft);">-</span></c:when>
                        <c:when test="${i.trend >= 0}"><span style="color:var(--color-verified);">&#9650; <c:out value="${i.trend}"/>%</span></c:when>
                        <c:otherwise><span style="color:var(--color-danger);">&#9660; <c:out value="${-1 * i.trend}"/>%</span></c:otherwise>
                    </c:choose>
                </td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
    </div>
</div>

<div class="panel">
    <h2 style="margin:0 0 var(--space-2); font-family:var(--font-display);">Marks obtained vs. required, by subject</h2>
    <p class="field-hint" style="margin-top:0;">"Required" is each subject's own passing threshold for that component.</p>
    <c:forEach items="${subjectHistory}" var="entry">
        <h3 style="font-size:0.95rem; margin:var(--space-4) 0 var(--space-2);"><c:out value="${entry.key}"/></h3>
        <div class="table-scroll">
        <table style="width:100%; font-size:0.9rem;">
            <thead><tr><th style="text-align:left;">Exam</th><th style="text-align:left;">Theory (obt/req)</th><th style="text-align:left;">Practical (obt/req)</th><th style="text-align:left;">Internal (obt/req)</th><th style="text-align:left;">Result</th></tr></thead>
            <tbody>
            <c:forEach items="${entry.value}" var="r">
                <tr>
                    <td><c:out value="${r.exam.name}"/></td>
                    <td><c:choose><c:when test="${not empty r.theoryMarks}"><c:out value="${r.theoryMarks}"/> / <c:out value="${r.subject.theoryPassingMarks}"/></c:when><c:otherwise>-</c:otherwise></c:choose></td>
                    <td><c:choose><c:when test="${not empty r.practicalMarks}"><c:out value="${r.practicalMarks}"/> / <c:out value="${r.subject.practicalPassingMarks}"/></c:when><c:otherwise>-</c:otherwise></c:choose></td>
                    <td><c:choose><c:when test="${not empty r.internalMarks}"><c:out value="${r.internalMarks}"/> / <c:out value="${r.subject.internalPassingMarks}"/></c:when><c:otherwise>-</c:otherwise></c:choose></td>
                    <td><c:out value="${r.grade}"/></td>
                </tr>
            </c:forEach>
            </tbody>
        </table>
        </div>
    </c:forEach>
</div>

</c:otherwise>
</c:choose>

<c:if test="${not empty trend}">
<script>
    new Chart(document.getElementById('trendChart'), {
        type: 'line',
        data: {
            labels: [<c:forEach items="${trend}" var="s" varStatus="st">"<c:out value="${s.exam.name}"/>"<c:if test="${!st.last}">,</c:if></c:forEach>],
            datasets: [{
                label: 'Percentage',
                data: [<c:forEach items="${trend}" var="s" varStatus="st">${s.overallPercentage}<c:if test="${!st.last}">,</c:if></c:forEach>],
                borderColor: '#B8925A',
                backgroundColor: 'rgba(184,146,90,0.12)',
                tension: 0.3,
                fill: true
            }]
        },
        options: { scales: { y: { beginAtZero: true, max: 100 } }, plugins: { legend: { display: false } } }
    });
</script>
</c:if>
<%@ include file="/common/fragments/teacher-foot.jspf" %>
