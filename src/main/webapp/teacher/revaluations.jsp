<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="Re-evaluations" scope="request"/>
<c:set var="pageSubtitle" value="Requests an admin has assigned to you for evaluation" scope="request"/>
<%@ include file="/common/fragments/teacher-head.jspf" %>

<%--
    New page (upgrade pass): the nav link to /teacher/revaluations already
    existed but had no servlet or JSP behind it - see
    TeacherRevaluationServlet's own class Javadoc for why this whole
    delegation step (Sec. "Admin ... assigns it to any teacher, Teacher
    evaluates and submits") didn't exist until now.
--%>
<c:if test="${not empty errorMessage}"><div class="auth-error" role="alert" style="margin-bottom:var(--space-6);"><c:out value="${errorMessage}"/></div></c:if>
<c:if test="${not empty successMessage}"><div class="auth-success" role="status" style="margin-bottom:var(--space-6);"><c:out value="${successMessage}"/></div></c:if>

<div class="panel">
    <h2 style="margin:0 0 var(--space-4); font-family:var(--font-display);">Awaiting your evaluation</h2>
    <c:choose>
        <c:when test="${empty assignedRequests}">
            <div class="panel-empty"><p>Nothing assigned to you right now.</p></div>
        </c:when>
        <c:otherwise>
            <table class="display" style="width:100%">
                <thead><tr><th>Student</th><th>Exam</th><th>Subject</th><th>Reason</th><th>Assigned</th><th></th></tr></thead>
                <tbody>
                <c:forEach items="${assignedRequests}" var="req">
                    <tr>
                        <td><c:out value="${req.student.user.fullName}"/> (<c:out value="${req.student.rollNo}"/>)</td>
                        <td><c:out value="${req.result.exam.name}"/></td>
                        <td><c:out value="${req.result.subject.subjectCode}"/></td>
                        <td><c:out value="${req.reason}"/></td>
                        <td><c:out value="${req.assignedAt}"/></td>
                        <td><a href="${ctx}/teacher/revaluations/resolve?requestId=${req.id}" class="btn-primary" style="width:auto; padding:0.4rem 1rem; text-decoration:none; display:inline-block; font-size:0.85rem;">Evaluate</a></td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </c:otherwise>
    </c:choose>
</div>

<div class="panel" style="margin-top:var(--space-6);">
    <h2 style="margin:0 0 var(--space-4); font-family:var(--font-display);">Your evaluation history</h2>
    <c:choose>
        <c:when test="${empty resolvedRequests}">
            <div class="panel-empty"><p>You haven't resolved any re-evaluation requests yet.</p></div>
        </c:when>
        <c:otherwise>
            <table class="display" style="width:100%">
                <thead><tr><th>Student</th><th>Exam</th><th>Subject</th><th>Outcome</th><th>Resolved</th></tr></thead>
                <tbody>
                <c:forEach items="${resolvedRequests}" var="req">
                    <tr>
                        <td><c:out value="${req.student.user.fullName}"/> (<c:out value="${req.student.rollNo}"/>)</td>
                        <td><c:out value="${req.result.exam.name}"/></td>
                        <td><c:out value="${req.result.subject.subjectCode}"/></td>
                        <td><span class="badge badge-status badge-status-${fn:toLowerCase(req.status)}"><c:out value="${req.status}"/></span></td>
                        <td><c:out value="${req.resolvedAt}"/></td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </c:otherwise>
    </c:choose>
</div>

<%@ include file="/common/fragments/teacher-foot.jspf" %>
