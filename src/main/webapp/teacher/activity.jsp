<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="My Activity" scope="request"/>
<c:set var="pageSubtitle" value="Your recent actions in the system" scope="request"/>
<%@ include file="/common/fragments/teacher-head.jspf" %>

<div class="panel">
    <c:choose>
        <c:when test="${empty logs}">
            <p class="panel-empty">No activity recorded yet.</p>
        </c:when>
        <c:otherwise>
            <table id="activityTable" class="display" style="width:100%">
                <thead>
                <tr>
                    <th>When</th>
                    <th>Action</th>
                    <th>Details</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach items="${logs}" var="log">
                    <tr>
                        <td><c:out value="${log.createdAt}"/></td>
                        <td><c:out value="${log.action}"/></td>
                        <td><c:out value="${log.details}"/></td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </c:otherwise>
    </c:choose>
</div>

<script>
    $(document).ready(function() {
        if ($('#activityTable tr').length) { $('#activityTable').DataTable({ order: [[0, 'desc']] }); }
    });
</script>
<%@ include file="/common/fragments/teacher-foot.jspf" %>
