<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Dashboard" scope="request"/>
<c:set var="pageSubtitle" value="Welcome back, ${student.user.fullName}" scope="request"/>
<%@ include file="/common/fragments/student-head.jspf" %>

<c:choose>
    <c:when test="${empty latestSummary}">
        <div class="panel">
            <p class="panel-empty">No published results available yet. Once your first exam is published, your percentage, SGPA, and rank will appear here.</p>
        </div>
    </c:when>
    <c:otherwise>
        <div class="stat-grid" style="margin-bottom:var(--space-6);">
            <div class="stat-card">
                <div class="label">Current Percentage</div>
                <div class="value"><c:out value="${latestSummary.overallPercentage}"/>%</div>
            </div>
            <div class="stat-card">
                <div class="label">SGPA</div>
                <div class="value"><c:out value="${empty latestSummary.sgpa ? '&mdash;' : latestSummary.sgpa}"/></div>
            </div>
            <div class="stat-card">
                <div class="label">CGPA</div>
                <div class="value"><c:out value="${empty currentCgpa ? '&mdash;' : currentCgpa}"/></div>
            </div>
            <div class="stat-card">
                <div class="label">Class Rank</div>
                <div class="value ${latestSummary.pass ? 'ok' : 'warn'}"><c:out value="${empty latestSummary.classRank ? '&mdash;' : latestSummary.classRank}"/></div>
            </div>
        </div>

        <div class="panel" style="margin-bottom:var(--space-6);">
            <h2>Latest Result &mdash; <c:out value="${latestSummary.exam.name}"/></h2>
            <p style="color:var(--color-ink-soft);">
                Grade <c:out value="${latestSummary.overallGrade}"/>,
                <span style="${latestSummary.pass ? '' : 'color:var(--color-danger); font-weight:600;'}"><c:out value="${latestSummary.pass ? 'Pass' : 'Fail'}"/></span>
                <c:if test="${not empty latestSummary.percentageChange}">
                    &mdash; <c:out value="${latestSummary.percentageChange >= 0 ? 'improved' : 'declined'}"/> by
                    <c:out value="${latestSummary.percentageChange >= 0 ? latestSummary.percentageChange : -1 * latestSummary.percentageChange}"/>%
                    compared with <c:out value="${latestSummary.previousExam.name}"/>
                </c:if>
            </p>
        </div>

        <div class="panel-grid" style="margin-bottom:var(--space-6);">
            <c:if test="${not empty bestSubject}">
                <div class="panel">
                    <h2>Best Subject</h2>
                    <p style="font-size:1.1rem; font-weight:600;"><c:out value="${bestSubject.subjectName}"/></p>
                    <p style="color:var(--color-ink-soft);">Average <c:out value="${bestSubject.averagePercentage}"/>% across <c:out value="${bestSubject.examCount}"/> examination<c:if test="${bestSubject.examCount != 1}">s</c:if></p>
                </div>
            </c:if>
            <c:if test="${not empty improvementSubject}">
                <div class="panel">
                    <h2>Area for Improvement</h2>
                    <p style="font-size:1.1rem; font-weight:600;"><c:out value="${improvementSubject.subjectName}"/></p>
                    <p style="color:var(--color-ink-soft);">Average <c:out value="${improvementSubject.averagePercentage}"/>% across <c:out value="${improvementSubject.examCount}"/> examination<c:if test="${improvementSubject.examCount != 1}">s</c:if></p>
                </div>
            </c:if>
        </div>

        <div class="panel-grid">
            <div class="panel">
                <h2>Performance Trend</h2>
                <canvas id="trendChart" height="220"></canvas>
            </div>
            <div class="panel">
                <h2>Grade Distribution &mdash; <c:out value="${latestSummary.exam.name}"/></h2>
                <canvas id="gradeChart" height="220"></canvas>
            </div>
        </div>
    </c:otherwise>
</c:choose>

<c:if test="${not empty latestSummary}">
<script>
    // Colors mirror this project's own tokens.css palette exactly (--color-seal,
    // --color-verified, --color-danger, --color-ink-soft) rather than new ones -
    // Chart.js configures colors in JS, which has no way to reference a CSS
    // custom property directly, so the literal hex values are copied here instead
    // of invented.
    const trendLabels = [<c:forEach items="${trend}" var="t" varStatus="loop">'<c:out value="${t.exam.name}"/>'<c:if test="${!loop.last}">,</c:if></c:forEach>];
    const trendData = [<c:forEach items="${trend}" var="t" varStatus="loop">${t.overallPercentage}<c:if test="${!loop.last}">,</c:if></c:forEach>];

    new Chart(document.getElementById('trendChart'), {
        type: 'line',
        data: {
            labels: trendLabels,
            datasets: [{
                label: 'Overall Percentage',
                data: trendData,
                borderColor: '#B8925A',
                backgroundColor: 'rgba(184, 146, 90, 0.12)',
                tension: 0.25,
                fill: true,
                pointBackgroundColor: '#B8925A'
            }]
        },
        options: {
            scales: { y: { min: 0, max: 100, ticks: { callback: v => v + '%' } } },
            plugins: { legend: { display: false } }
        }
    });

    const gradeLabels = [<c:forEach items="${gradeDistribution}" var="entry" varStatus="loop">'<c:out value="${entry.key}"/>'<c:if test="${!loop.last}">,</c:if></c:forEach>];
    const gradeCounts = [<c:forEach items="${gradeDistribution}" var="entry" varStatus="loop">${entry.value}<c:if test="${!loop.last}">,</c:if></c:forEach>];
    const gradePalette = ['#2F6B52', '#B8925A', '#D4AC76', '#5B6472', '#A3384A', '#10192B'];

    new Chart(document.getElementById('gradeChart'), {
        type: 'doughnut',
        data: {
            labels: gradeLabels,
            datasets: [{ data: gradeCounts, backgroundColor: gradePalette }]
        },
        options: { plugins: { legend: { position: 'bottom' } } }
    });
</script>
</c:if>
<%@ include file="/common/fragments/student-foot.jspf" %>
