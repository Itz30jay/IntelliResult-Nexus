<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Grading Rules" scope="request"/>
<c:set var="pageSubtitle" value="Dynamic Grading Engine" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<c:if test="${empty academicYears}">
    <div class="panel">
        <p class="panel-empty">
            No academic years exist yet. Create one under
            <a href="${ctx}/admin/academic-setup">Academic Setup</a> before defining a grading scale.
        </p>
    </div>
</c:if>

<c:if test="${not empty academicYears}">
<div style="display:flex; flex-wrap:wrap; gap:var(--space-2); align-items:center; margin-bottom:var(--space-4);">
    <c:forEach items="${academicYears}" var="year">
        <a href="${ctx}/admin/grading-rules?academicYearId=${year.id}"
           style="padding:0.4rem 0.9rem; border-radius:999px; font-size:0.85rem; text-decoration:none;
                  ${selectedYear.id == year.id ? 'background:var(--color-ink); color:var(--color-paper);' : 'background:var(--color-paper-soft); color:var(--color-ink-soft);'}">
            <c:out value="${year.label}"/><c:if test="${year.current}"> &bull; current</c:if>
        </a>
    </c:forEach>
    <a href="${ctx}/admin/grading-rules/new${not empty selectedYear ? '?academicYearId=' : ''}${selectedYear.id}"
       class="btn-primary" style="width:auto; margin-left:auto; text-decoration:none; display:inline-block; padding:0.55rem 1.1rem; font-size:0.85rem;">+ Add Rule</a>
</div>

<div class="panel">
    <c:choose>
        <c:when test="${empty rules}">
            <p class="panel-empty">
                No grading rules defined for <c:out value="${selectedYear.label}"/> yet.
                A student's percentage can't be turned into a grade until at least one rule exists.
            </p>
        </c:when>
        <c:otherwise>
            <table id="rulesTable" class="display" style="width:100%">
                <thead>
                <tr>
                    <th>Grade</th>
                    <th>Range</th>
                    <th>Grade point</th>
                    <th>Status</th>
                    <th>Actions</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach items="${rules}" var="rule">
                    <tr>
                        <td><strong><c:out value="${rule.grade}"/></strong></td>
                        <td><c:out value="${rule.minPercentage}"/>% &ndash; <c:out value="${rule.maxPercentage}"/>%</td>
                        <td><c:out value="${rule.gradePoint}"/></td>
                        <td>
                            <c:choose>
                                <c:when test="${rule.active}"><span class="badge badge-priority-low">Active</span></c:when>
                                <c:otherwise><span class="badge badge-priority-normal">Inactive</span></c:otherwise>
                            </c:choose>
                        </td>
                        <td style="white-space:nowrap;">
                            <a href="${ctx}/admin/grading-rules/edit?id=${rule.id}" style="margin-right:0.75rem;">Edit</a>
                            <c:choose>
                                <c:when test="${rule.active}">
                                    <a href="#" onclick="toggleRule(${rule.id}, 'deactivate', '<c:out value="${rule.grade}"/>'); return false;" style="color:var(--color-danger);">Deactivate</a>
                                </c:when>
                                <c:otherwise>
                                    <a href="#" onclick="toggleRule(${rule.id}, 'activate', '<c:out value="${rule.grade}"/>'); return false;" style="color:var(--color-verified);">Activate</a>
                                </c:otherwise>
                            </c:choose>
                        </td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </c:otherwise>
    </c:choose>
</div>
</c:if>

<form id="toggleForm" method="post" style="display:none;">
    <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
    <input type="hidden" name="id" id="toggleFormId">
</form>

<script>
    $(document).ready(function() {
        if ($('#rulesTable tr').length) { $('#rulesTable').DataTable({ order: [[1, 'asc']], paging: false, searching: false, info: false }); }
    });

    // Deactivating never conflicts with anything (see GradingRuleService.deactivate),
    // so it skips confirmation; reactivating can fail server-side if another
    // rule grew into this range while it was off (GradingRuleService.activate
    // re-checks), which is exactly the kind of outcome worth a heads-up for.
    function toggleRule(id, action, grade) {
        const proceed = () => {
            document.getElementById('toggleFormId').value = id;
            document.getElementById('toggleForm').action = '${ctx}/admin/grading-rules/' + action;
            document.getElementById('toggleForm').submit();
        };
        if (action === 'activate') {
            Swal.fire({
                title: 'Reactivate ' + grade + '?', text: 'This checks again for conflicts with rules added while it was off.',
                icon: 'question', showCancelButton: true, confirmButtonText: 'Reactivate', confirmButtonColor: '#3F7A5C'
            }).then((r) => { if (r.isConfirmed) proceed(); });
        } else {
            proceed();
        }
    }
    <c:if test="${not empty errorMessage}">toastr.error('<c:out value="${errorMessage}"/>');</c:if>
    <c:if test="${not empty successMessage}">toastr.success('<c:out value="${successMessage}"/>');</c:if>
</script>
<%@ include file="/common/fragments/admin-foot.jspf" %>
