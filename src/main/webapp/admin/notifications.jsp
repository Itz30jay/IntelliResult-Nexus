<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Notifications" scope="request"/>
<c:set var="pageSubtitle" value="Result publications, re-evaluation decisions, and notices addressed to you" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<div style="display:flex; justify-content:flex-end; margin-bottom:var(--space-4);">
    <form method="post" action="${ctx}/admin/notifications/read-all">
        <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
        <button type="submit" class="btn-secondary" style="width:auto; padding:0.5rem 1.1rem;">Mark all as read</button>
    </form>
</div>

<div class="panel">
    <c:choose>
        <c:when test="${empty notifications}">
            <p class="panel-empty">No notifications yet.</p>
        </c:when>
        <c:otherwise>
            <c:forEach items="${notifications}" var="n">
                <div class="list-row" style="align-items:flex-start; ${n.read ? '' : 'background:rgba(184,146,90,0.06);'}">
                    <div>
                        <div style="font-weight:600;">
                            <c:if test="${!n.read}"><span class="dot" style="display:inline-block; width:6px; height:6px; border-radius:50%; background:var(--color-seal); margin-right:6px;"></span></c:if>
                            <c:out value="${n.title}"/>
                        </div>
                        <div style="color:var(--color-ink-soft); font-size:0.88rem; margin-top:2px;"><c:out value="${n.message}"/></div>
                        <div style="color:var(--color-ink-soft); font-size:0.75rem; margin-top:4px;"><c:out value="${n.createdAt}"/></div>
                    </div>
                    <c:if test="${!n.read}">
                        <form method="post" action="${ctx}/admin/notifications/read">
                            <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
                            <input type="hidden" name="notificationId" value="${n.id}">
                            <button type="submit" class="btn-secondary" style="width:auto; padding:0.4rem 0.9rem; font-size:0.8rem;">Mark read</button>
                        </form>
                    </c:if>
                </div>
            </c:forEach>
        </c:otherwise>
    </c:choose>
</div>

<%@ include file="/common/fragments/admin-foot.jspf" %>
