<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Dashboard" scope="request"/>
<c:set var="pageSubtitle" value="Academic year overview" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<c:if test="${stats.needsSetup()}">
<%--
    Upgrade: shown only when no Course exists yet - the cheapest reliable
    signal that this is a freshly-installed system nobody has configured.
    Previously a brand-new admin landed on seven separate "no data yet"
    panels with no indication of what order to address them in; this
    replaces that scavenger hunt with the actual dependency order Academic
    Setup requires (Course -> Academic Year -> Semester -> Section/Subject).
--%>
<div class="panel" style="margin-bottom:var(--space-6); border-top: 3px solid var(--color-seal); background: linear-gradient(180deg, #FFFDF9 0%, #fff 60%);">
    <h2 style="border-bottom:none; padding-bottom:0;">Welcome &mdash; let's set up your institution</h2>
    <p style="color:var(--color-ink-soft); margin-top:0; margin-bottom:var(--space-6); max-width:60ch;">
        This is a clean installation with no sample data. Set up your real academic structure in this order,
        then invite real Students and Teachers to register.
    </p>
    <ol class="onboarding-steps">
        <li><a href="${ctx}/admin/academic-setup/departments">Add your department(s)</a><span>e.g. Computer Science, Mechanical Engineering</span></li>
        <li><a href="${ctx}/admin/academic-setup/courses">Add your course(s)</a><span>e.g. B.Tech in Computer Science, with its total semester count</span></li>
        <li><a href="${ctx}/admin/academic-setup/academic-years">Create the current academic year</a><span>e.g. 2026-2027</span></li>
        <li><a href="${ctx}/admin/academic-setup/semesters">Create semesters, sections &amp; subjects</a><span>for each course, under the academic year you just created</span></li>
        <li><a href="${ctx}/admin/grading-rules">Set your grading scale</a><span>the percentage bands your institution uses for grades</span></li>
        <li><a href="${ctx}/admin/registrations">Verify real Students &amp; Teachers</a><span>once people register at <code>/register</code>, approve them here</span></li>
        <li><a href="${ctx}/admin/settings">Set your institution name &amp; details</a><span>shown on marksheets and official documents</span></li>
    </ol>
</div>
</c:if>

<div class="stat-grid">
    <div class="stat-card">
        <div class="label">Total Students</div>
        <div class="value"><fmt:formatNumber value="${stats.totalStudents}"/></div>
    </div>
    <div class="stat-card">
        <div class="label">Total Teachers</div>
        <div class="value"><fmt:formatNumber value="${stats.totalTeachers}"/></div>
    </div>
    <div class="stat-card">
        <div class="label">Total Subjects</div>
        <div class="value"><fmt:formatNumber value="${stats.totalSubjects}"/></div>
    </div>
    <div class="stat-card">
        <div class="label">Active Exams</div>
        <div class="value accent"><fmt:formatNumber value="${stats.activeExams}"/></div>
    </div>
    <div class="stat-card">
        <div class="label">Published Results</div>
        <div class="value ok"><fmt:formatNumber value="${stats.publishedResults}"/></div>
    </div>
    <div class="stat-card">
        <div class="label">Pending Approvals</div>
        <div class="value ${stats.pendingApprovals > 0 ? 'warn' : ''}"><fmt:formatNumber value="${stats.pendingApprovals}"/></div>
    </div>
    <div class="stat-card">
        <div class="label">Pending Re-evaluations</div>
        <div class="value ${stats.pendingRevaluations > 0 ? 'warn' : ''}"><fmt:formatNumber value="${stats.pendingRevaluations}"/></div>
    </div>
</div>

<div class="panel-grid" style="margin-bottom: var(--space-6);">
    <div class="panel">
        <h2>Pass / Fail</h2>
        <c:choose>
            <c:when test="${stats.hasPublishedResults()}">
                <div class="chart-container"><canvas id="passFailChart"></canvas></div>
                <p style="text-align:center; font-size:0.8rem; color:var(--color-ink-soft); margin-top:var(--space-2);">
                    <fmt:formatNumber value="${stats.passRatePercent()}" maxFractionDigits="1"/>% pass rate across <fmt:formatNumber value="${stats.passCount + stats.failCount}"/> published results
                </p>
            </c:when>
            <c:otherwise>
                <div class="panel-empty">No published results available yet. This chart appears once at least one exam has been published.</div>
            </c:otherwise>
        </c:choose>
    </div>

    <div class="panel">
        <h2>Grade Distribution</h2>
        <c:choose>
            <c:when test="${stats.hasPublishedResults()}">
                <div class="chart-container"><canvas id="gradeChart"></canvas></div>
            </c:when>
            <c:otherwise>
                <div class="panel-empty">No published results available yet.</div>
            </c:otherwise>
        </c:choose>
    </div>

    <div class="panel">
        <h2>Subject-wise Average</h2>
        <c:choose>
            <c:when test="${stats.hasPublishedResults()}">
                <div class="chart-container"><canvas id="subjectChart"></canvas></div>
            </c:when>
            <c:otherwise>
                <div class="panel-empty">No published results available yet.</div>
            </c:otherwise>
        </c:choose>
    </div>
</div>

<div class="panel-grid">
    <div class="panel">
        <h2>Top Performers</h2>
        <c:choose>
            <c:when test="${empty stats.topPerformers}">
                <div class="panel-empty">No performance history available yet.</div>
            </c:when>
            <c:otherwise>
                <c:forEach items="${stats.topPerformers}" var="p" varStatus="loop">
                    <div class="list-row">
                        <span><span class="rank">#${loop.index + 1}</span><c:out value="${p.fullName}"/> &middot; <c:out value="${p.rollNo}"/></span>
                        <span class="metric"><fmt:formatNumber value="${p.averagePercentage}" maxFractionDigits="1"/>%</span>
                    </div>
                </c:forEach>
            </c:otherwise>
        </c:choose>
    </div>

    <div class="panel">
        <div style="display:flex; justify-content:space-between; align-items:baseline;">
            <h2 style="border-bottom:none; padding-bottom:0; margin-bottom:var(--space-4);">Recent Activity</h2>
            <a href="${ctx}/admin/activity-logs" style="font-size:0.85rem;">View all &rarr;</a>
        </div>
        <c:choose>
            <c:when test="${empty stats.recentActivity}">
                <div class="panel-empty">No activity recorded yet.</div>
            </c:when>
            <c:otherwise>
                <c:forEach items="${stats.recentActivity}" var="log">
                    <div class="timeline-item">
                        <div>
                            <div class="timeline-action"><c:out value="${log.action}"/></div>
                            <div class="timeline-meta"><c:out value="${log.details}"/></div>
                        </div>
                    </div>
                </c:forEach>
            </c:otherwise>
        </c:choose>
    </div>

    <div class="panel">
        <h2>Recent Notices</h2>
        <c:choose>
            <c:when test="${empty stats.recentNotices}">
                <div class="panel-empty">No notices found.</div>
            </c:when>
            <c:otherwise>
                <c:forEach items="${stats.recentNotices}" var="notice">
                    <div class="list-row">
                        <span><c:out value="${notice.title}"/></span>
                        <span class="badge badge-priority-${notice.priority.toString().toLowerCase()}"><c:out value="${notice.priority}"/></span>
                    </div>
                </c:forEach>
            </c:otherwise>
        </c:choose>
    </div>
</div>


<c:if test="${stats.hasPublishedResults()}">
<script>
(function() {
    const inkSoft = '#5B6472';
    Chart.defaults.font.family = "'Inter', sans-serif";
    Chart.defaults.color = inkSoft;

    new Chart(document.getElementById('passFailChart'), {
        type: 'doughnut',
        data: {
            labels: ['Pass', 'Fail'],
            datasets: [{
                data: [${stats.passCount}, ${stats.failCount}],
                backgroundColor: ['#2F6B52', '#A3384A'],
                borderWidth: 0
            }]
        },
        options: { plugins: { legend: { position: 'bottom' } }, maintainAspectRatio: false }
    });

    const gradeData = ${gradeDistributionJson};
    new Chart(document.getElementById('gradeChart'), {
        type: 'bar',
        data: {
            labels: gradeData.map(g => g.grade),
            datasets: [{ data: gradeData.map(g => g.count), backgroundColor: '#B8925A', borderRadius: 4 }]
        },
        options: { plugins: { legend: { display: false } }, maintainAspectRatio: false, scales: { y: { beginAtZero: true, ticks: { precision: 0 } } } }
    });

    const subjectData = ${subjectPerformanceJson};
    new Chart(document.getElementById('subjectChart'), {
        type: 'bar',
        data: {
            labels: subjectData.map(s => s.subjectCode),
            datasets: [{ data: subjectData.map(s => s.averagePercentage.toFixed(1)), backgroundColor: '#10192B', borderRadius: 4 }]
        },
        options: { plugins: { legend: { display: false } }, maintainAspectRatio: false, scales: { y: { beginAtZero: true, max: 100 } } }
    });
})();
</script>
</c:if>
<%@ include file="/common/fragments/admin-foot.jspf" %>
