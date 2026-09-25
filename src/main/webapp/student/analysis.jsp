<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="Performance Analysis" scope="request"/>
<c:set var="pageSubtitle" value="How you compare, and where your strengths lie" scope="request"/>
<%@ include file="/common/fragments/student-head.jspf" %>

<c:choose>
    <c:when test="${empty exams}">
        <div class="panel">
            <p class="panel-empty">No published results available yet - comparisons and subject analysis will appear once your first exam is published.</p>
        </div>
    </c:when>
    <c:otherwise>
        <div class="panel" style="margin-bottom:var(--space-6);">
            <label for="examSelect" style="font-weight:600; margin-right:0.75rem;">Examination</label>
            <select id="examSelect" onchange="location.href='${ctx}/student/analysis?examId=' + this.value;">
                <c:forEach items="${exams}" var="exam">
                    <option value="${exam.id}" ${exam.id == selectedExamId ? 'selected' : ''}><c:out value="${exam.name}"/></option>
                </c:forEach>
            </select>
        </div>

        <c:if test="${not empty myPercentage}">
        <div class="stat-grid" style="margin-bottom:var(--space-6);">
            <div class="stat-card">
                <div class="label">Your percentage</div>
                <div class="value accent"><c:out value="${myPercentage}"/>%</div>
            </div>
        </div>
        </c:if>

        <c:choose>
            <c:when test="${empty hasSection}">
                <div class="panel" style="margin-bottom:var(--space-6);">
                    <p class="panel-empty">
                        You haven't been assigned to a section yet, so class-average and topper comparisons
                        aren't available<c:if test="${not empty myPercentage}"> - your own percentage above is still accurate</c:if>.
                        Contact your administration office to be added to a section.
                    </p>
                </div>
            </c:when>
            <c:when test="${empty classAverage}">
                <div class="panel" style="margin-bottom:var(--space-6);">
                    <p class="panel-empty">
                        No class-wide comparison is available for this examination yet
                        <c:if test="${empty myPercentage}"> - you don't have a calculated result for it either</c:if>.
                    </p>
                </div>
            </c:when>
            <c:otherwise>
                <div class="panel-grid" style="margin-bottom:var(--space-6);">
                    <div class="panel">
                        <h2>You vs. Class Average</h2>
                        <canvas id="classChart" height="200"></canvas>
                        <c:if test="${not empty myPercentage}">
                            <p style="color:var(--color-ink-soft); margin-top:var(--space-3);">
                                You are
                                <span style="${myPercentage >= classAverage ? 'color:var(--color-verified); font-weight:600;' : 'color:var(--color-danger); font-weight:600;'}">
                                    <c:out value="${myPercentage >= classAverage ? myPercentage - classAverage : classAverage - myPercentage}"/>%
                                    <c:out value="${myPercentage >= classAverage ? 'above' : 'below'}"/>
                                </span>
                                the class average.
                            </p>
                        </c:if>
                    </div>
                    <div class="panel">
                        <h2>You vs. Topper</h2>
                        <canvas id="topperChart" height="200"></canvas>
                        <c:if test="${not empty myPercentage and not empty topperScore}">
                            <p style="color:var(--color-ink-soft); margin-top:var(--space-3);">
                                <c:choose>
                                    <c:when test="${myPercentage == topperScore}">You are the top scorer in your section.</c:when>
                                    <c:otherwise><c:out value="${topperScore - myPercentage}"/>% behind the top score in your section.</c:otherwise>
                                </c:choose>
                            </p>
                        </c:if>
                    </div>
                </div>

                <c:if test="${not empty subjectComparisons}">
                    <div class="panel" style="margin-bottom:var(--space-6);">
                        <h2>Subject Comparison</h2>
                        <canvas id="subjectChart" height="240"></canvas>
                    </div>
                </c:if>
            </c:otherwise>
        </c:choose>

        <div class="panel-grid">
            <div class="panel">
                <h2>Strength Areas</h2>
                <c:choose>
                    <c:when test="${empty strengths}">
                        <p class="panel-empty">Not enough result history yet to identify strength areas.</p>
                    </c:when>
                    <c:otherwise>
                        <c:forEach items="${strengths}" var="s">
                            <p style="margin-bottom:var(--space-3);">
                                <strong><c:out value="${s.subjectName}"/></strong> &mdash;
                                averaging <c:out value="${s.averagePercentage}"/>% across <c:out value="${s.examCount}"/> examination<c:if test="${s.examCount != 1}">s</c:if>
                            </p>
                        </c:forEach>
                    </c:otherwise>
                </c:choose>
            </div>
            <div class="panel">
                <h2>Improvement Areas</h2>
                <c:choose>
                    <c:when test="${empty improvementAreas}">
                        <p class="panel-empty">Not enough result history yet to identify improvement areas.</p>
                    </c:when>
                    <c:otherwise>
                        <c:forEach items="${improvementAreas}" var="s">
                            <p style="margin-bottom:var(--space-3);">
                                Needs improvement in <strong><c:out value="${s.subjectName}"/></strong> &mdash;
                                averaging <c:out value="${s.averagePercentage}"/>% across <c:out value="${s.examCount}"/> examination<c:if test="${s.examCount != 1}">s</c:if>
                                <c:if test="${not empty s.trend and s.trend < 0}"> (declining <c:out value="${-1 * s.trend}"/>% from earlier results)</c:if>
                            </p>
                        </c:forEach>
                    </c:otherwise>
                </c:choose>
            </div>
        </div>
    </c:otherwise>
</c:choose>

<c:if test="${not empty classAverage}">
<script>
    // Same tokens.css-derived palette as dashboard.jsp: seal (#B8925A) for "me",
    // ink-soft (#5B6472) for the class average, verified (#2F6B52) for the topper.
    new Chart(document.getElementById('classChart'), {
        type: 'bar',
        data: {
            labels: ['You', 'Class Average'],
            datasets: [{
                data: [${empty myPercentage ? 'null' : myPercentage}, ${classAverage}],
                backgroundColor: ['#B8925A', '#5B6472']
            }]
        },
        options: { scales: { y: { min: 0, max: 100, ticks: { callback: v => v + '%' } } }, plugins: { legend: { display: false } } }
    });

    new Chart(document.getElementById('topperChart'), {
        type: 'bar',
        data: {
            labels: ['You', 'Topper'],
            datasets: [{
                data: [${empty myPercentage ? 'null' : myPercentage}, ${empty topperScore ? 'null' : topperScore}],
                backgroundColor: ['#B8925A', '#2F6B52']
            }]
        },
        options: { scales: { y: { min: 0, max: 100, ticks: { callback: v => v + '%' } } }, plugins: { legend: { display: false } } }
    });

    <c:if test="${not empty subjectComparisons}">
    const subjectLabels = [<c:forEach items="${subjectComparisons}" var="sc" varStatus="loop">'<c:out value="${sc.subjectCode}"/>'<c:if test="${!loop.last}">,</c:if></c:forEach>];
    const myScores = [<c:forEach items="${subjectComparisons}" var="sc" varStatus="loop">${empty sc.studentPercentage ? 'null' : sc.studentPercentage}<c:if test="${!loop.last}">,</c:if></c:forEach>];
    const classScores = [<c:forEach items="${subjectComparisons}" var="sc" varStatus="loop">${sc.classAveragePercentage}<c:if test="${!loop.last}">,</c:if></c:forEach>];

    new Chart(document.getElementById('subjectChart'), {
        type: 'bar',
        data: {
            labels: subjectLabels,
            datasets: [
                { label: 'You', data: myScores, backgroundColor: '#B8925A' },
                { label: 'Class Average', data: classScores, backgroundColor: '#5B6472' }
            ]
        },
        options: { scales: { y: { min: 0, max: 100, ticks: { callback: v => v + '%' } } }, plugins: { legend: { position: 'bottom' } } }
    });
    </c:if>
</script>
</c:if>
<%@ include file="/common/fragments/student-foot.jspf" %>
