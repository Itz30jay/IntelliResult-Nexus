<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%--
    Sec. 42/43's "print layouts" - a standalone page, no sidebar, reached
    by both /admin/reports/view and /teacher/reports/view (Sec. 33's
    reports are role-scoped by which servlet forwards here, not by this
    JSP, which only ever renders whatever ReportData it was handed).
    Download links are derived from this same request's own path and
    query string (swapping "/view" for "/pdf"/"/excel") rather than the
    servlet computing them - admin and teacher live under different URL
    prefixes, and this avoids either servlet needing to know the other's
    path shape.
--%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><c:out value="${report.title}"/> - IntelliResult Nexus</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/tokens.css">
    <style>
        body { font-family: 'Inter', sans-serif; background: var(--color-paper); margin: 0; padding: var(--space-6); color: var(--color-ink); }
        .report-toolbar { display: flex; justify-content: space-between; align-items: center; margin: 0 auto var(--space-5); max-width: 1100px; }
        .report-toolbar-actions { display: flex; gap: var(--space-2); }
        .report-toolbar a, .report-toolbar button { text-decoration: none; border: 1px solid var(--color-rule); background: #fff; color: var(--color-ink); padding: 0.5rem 1rem; border-radius: var(--radius-sm); font-size: 0.85rem; cursor: pointer; }
        .report-sheet { background: #fff; max-width: 1100px; margin: 0 auto; padding: var(--space-6); border: 1px solid var(--color-rule); border-radius: var(--radius-md); }
        .institution-header { display: flex; align-items: center; gap: var(--space-4); padding-bottom: var(--space-4); border-bottom: 1px solid var(--color-rule); margin-bottom: var(--space-4); }
        .institution-header img { height: 44px; width: 44px; object-fit: contain; }
        .institution-header .name { font-family: var(--font-display); font-size: 1.1rem; font-weight: 600; }
        .institution-header .address { font-size: 0.78rem; color: var(--color-ink-soft); }
        .report-header { text-align: center; margin-bottom: var(--space-5); border-bottom: 2px solid var(--color-ink); padding-bottom: var(--space-4); }
        .report-header h1 { font-family: var(--font-display); font-size: 1.4rem; margin: var(--space-2) 0 0; }
        .report-header .subtitle { color: var(--color-ink-soft); margin-top: 4px; font-size: 0.9rem; }
        table.report-table { width: 100%; border-collapse: collapse; margin-top: var(--space-4); font-size: 0.85rem; }
        table.report-table th { background: var(--color-ink); color: #fff; padding: 8px; text-align: left; }
        table.report-table td { padding: 7px 8px; border-bottom: 1px solid var(--color-rule); }
        table.report-table tr:nth-child(even) td { background: var(--color-paper-soft); }
        .report-summary { margin-top: var(--space-5); border-top: 2px solid var(--color-ink); padding-top: var(--space-3); font-size: 0.9rem; }
        .report-summary div { margin-bottom: 4px; }
        .report-empty { text-align: center; color: var(--color-ink-soft); padding: var(--space-6) 0; }
        @media print {
            .report-toolbar { display: none; }
            body { padding: 0; background: #fff; }
            .report-sheet { border: none; border-radius: 0; max-width: none; padding: 0; }
        }
    </style>
</head>
<body>
<div class="report-toolbar">
    <a href="javascript:history.back()">&larr; Back</a>
    <div class="report-toolbar-actions">
        <button onclick="window.print()">Print</button>
        <a href="${pageContext.request.contextPath}${fn:replace(pageContext.request.servletPath, '/view', '/pdf')}?${pageContext.request.queryString}">Download PDF</a>
        <a href="${pageContext.request.contextPath}${fn:replace(pageContext.request.servletPath, '/view', '/excel')}?${pageContext.request.queryString}">Download Excel</a>
    </div>
</div>

<div class="report-sheet">
    <div class="institution-header">
        <c:if test="${not empty settings.institutionLogoPath}">
            <img src="<c:out value='${settings.institutionLogoPath}'/>" alt="" onerror="this.style.display='none';">
        </c:if>
        <div>
            <div class="name"><c:out value="${settings.institutionName}"/></div>
            <c:if test="${not empty settings.institutionAddress}">
                <div class="address"><c:out value="${settings.institutionAddress}"/></div>
            </c:if>
        </div>
    </div>

    <div class="report-header">
        <h1><c:out value="${report.title}"/></h1>
        <c:if test="${not empty report.subtitle}">
            <div class="subtitle"><c:out value="${report.subtitle}"/></div>
        </c:if>
        <div class="subtitle">Generated <c:out value="${generatedAtDisplay}"/></div>
    </div>

    <c:choose>
        <c:when test="${empty report.rows}">
            <p class="report-empty">No data available for this report.</p>
        </c:when>
        <c:otherwise>
            <div class="table-scroll">
            <table class="report-table">
                <thead>
                <tr>
                    <c:forEach items="${report.columnHeaders}" var="header"><th><c:out value="${header}"/></th></c:forEach>
                </tr>
                </thead>
                <tbody>
                <c:forEach items="${report.rows}" var="row">
                    <tr>
                        <c:forEach items="${row}" var="cell"><td><c:out value="${cell}" default="—"/></td></c:forEach>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
            </div>
        </c:otherwise>
    </c:choose>

    <c:if test="${not empty report.summaryLines}">
        <div class="report-summary">
            <c:forEach items="${report.summaryLines}" var="line">
                <div><c:out value="${line}"/></div>
            </c:forEach>
        </div>
    </c:if>
</div>
</body>
</html>
