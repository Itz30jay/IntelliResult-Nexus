<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
    /student/marksheet - Sec. 69's Marksheet nav item. Reuses .panel/.list-row
    from app-shell.css and the exact same pass/fail inline-style convention
    /student/results.jsp already established, rather than reusing the
    badge-status-* family, which names workflow stages (PUBLISHED/LOCKED),
    not an academic outcome - stretching it to also mean pass/fail would
    make two different things share one visual vocabulary by coincidence.
--%>
<c:set var="pageTitle" value="Marksheet" scope="request"/>
<c:set var="pageSubtitle" value="Download an official, QR-verifiable PDF of any fully published result" scope="request"/>
<%@ include file="/common/fragments/student-head.jspf" %>

<div class="panel">
    <c:choose>
        <c:when test="${empty eligibleSummaries}">
            <p class="panel-empty">No marksheet is available yet. A marksheet becomes downloadable once every subject in an examination has been published.</p>
        </c:when>
        <c:otherwise>
            <c:forEach items="${eligibleSummaries}" var="summary">
                <div class="list-row">
                    <div>
                        <div style="font-weight:600;"><c:out value="${summary.exam.name}"/></div>
                        <div style="color:var(--color-ink-soft); font-size:0.8rem; margin-top:2px;">
                            <c:out value="${summary.exam.academicYear.label}"/>
                            &mdash; <c:out value="${summary.overallPercentage}"/>%
                            &mdash; Grade <c:out value="${summary.overallGrade}"/>
                            &mdash;
                            <span style="${summary.pass ? '' : 'color:var(--color-danger); font-weight:600;'}">
                                <c:out value="${summary.pass ? 'Pass' : 'Fail'}"/>
                            </span>
                        </div>
                    </div>
                    <a class="btn-secondary" style="width:auto; padding:0.55rem 1.1rem; text-decoration:none; white-space:nowrap;"
                       href="${ctx}/student/marksheet/download?examId=${summary.exam.id}">Download PDF</a>
                </div>
            </c:forEach>
        </c:otherwise>
    </c:choose>
</div>

<%@ include file="/common/fragments/student-foot.jspf" %>
