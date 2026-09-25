<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Dashboard" scope="request"/>
<c:set var="pageSubtitle" value="Your teaching overview" scope="request"/>
<%@ include file="/common/fragments/teacher-head.jspf" %>

<div class="stat-grid">
    <div class="stat-card">
        <div class="label">Active Assignments</div>
        <div class="value"><fmt:formatNumber value="${stats.activeAssignmentCount()}"/></div>
    </div>
    <div class="stat-card">
        <div class="label">Exams Open for Entry</div>
        <div class="value accent"><fmt:formatNumber value="${stats.openForMarksEntryCount()}"/></div>
    </div>
    <div class="stat-card">
        <div class="label">Submitted, Awaiting Approval</div>
        <div class="value ${stats.submittedAwaitingApproval > 0 ? 'warn' : 'ok'}"><fmt:formatNumber value="${stats.submittedAwaitingApproval}"/></div>
    </div>
</div>

<c:if test="${!stats.hasAssignments()}">
    <div class="panel" style="margin-bottom:var(--space-6);">
        <p class="panel-empty">No active subject assignments yet. Once an administrator assigns you to a subject and section under Teacher Assignments, they'll appear here.</p>
    </div>
</c:if>

<div class="panel-grid" style="margin-bottom: var(--space-6);">
    <div class="panel">
        <h2>My Active Assignments</h2>
        <c:choose>
            <c:when test="${empty stats.activeAssignments}">
                <div class="panel-empty">Nothing assigned yet.</div>
            </c:when>
            <c:otherwise>
                <c:forEach items="${stats.activeAssignments}" var="a">
                    <div class="list-row">
                        <span><c:out value="${a.subject.subjectCode}"/> &middot; <c:out value="${a.subject.subjectName}"/></span>
                        <span class="metric">Sec <c:out value="${a.section.name}"/></span>
                    </div>
                </c:forEach>
                <p style="margin-top:var(--space-3);"><a href="${ctx}/teacher/my-classes" style="font-size:0.85rem;">View all my classes &rarr;</a></p>
            </c:otherwise>
        </c:choose>
    </div>

    <div class="panel">
        <h2>Exams Open for Marks Entry</h2>
        <c:choose>
            <c:when test="${empty stats.openForMarksEntry}">
                <div class="panel-empty">No exams are currently open for marks entry.</div>
            </c:when>
            <c:otherwise>
                <c:forEach items="${stats.openForMarksEntry}" var="exam">
                    <div class="list-row">
                        <span><c:out value="${exam.name}"/></span>
                        <span class="badge badge-status badge-status-${exam.status.toString().toLowerCase()}"><c:out value="${exam.status}"/></span>
                    </div>
                </c:forEach>
                <p style="margin-top:var(--space-3);"><a href="${ctx}/teacher/exams" style="font-size:0.85rem;">View all exams &rarr;</a></p>
            </c:otherwise>
        </c:choose>
    </div>
</div>

<div class="panel-grid">
    <div class="panel">
        <h2>Recent Activity</h2>
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
        <h2>Notices</h2>
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

<%@ include file="/common/fragments/teacher-foot.jspf" %>
