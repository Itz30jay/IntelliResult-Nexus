<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Academic Years" scope="request"/>
<c:set var="pageSubtitle" value="Academic Setup" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<div style="display:flex; justify-content:flex-end; margin-bottom: var(--space-4);">
    <a href="${ctx}/admin/academic-setup/academic-years/new" class="btn-primary" style="width:auto; text-decoration:none; display:inline-block; padding:0.6rem 1.2rem;">+ Add Academic Year</a>
</div>

<div class="panel">
    <table id="yearsTable" class="display" style="width:100%">
        <thead><tr><th>Label</th><th>Start Date</th><th>End Date</th><th>Status</th><th>Actions</th></tr></thead>
        <tbody>
        <c:forEach items="${academicYears}" var="y">
            <tr>
                <td><c:out value="${y.label}"/></td>
                <td><c:out value="${y.startDate}"/></td>
                <td><c:out value="${y.endDate}"/></td>
                <td>
                    <c:choose>
                        <c:when test="${y.current}"><span class="badge" style="background:#EAF3EE;color:var(--color-verified);">Current</span></c:when>
                        <c:otherwise><span style="color:var(--color-ink-soft); font-size:0.85rem;">&mdash;</span></c:otherwise>
                    </c:choose>
                </td>
                <td>
                    <a href="${ctx}/admin/academic-setup/academic-years/edit?id=${y.id}" style="margin-right:0.75rem;">Edit</a>
                    <c:if test="${not y.current}">
                        <a href="#" onclick="confirmSetCurrent(${y.id}, '<c:out value="${y.label}"/>'); return false;">Set as current</a>
                    </c:if>
                </td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
</div>

<form id="setCurrentForm" method="post" action="${ctx}/admin/academic-setup/academic-years/set-current" style="display:none;">
    <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
    <input type="hidden" name="id" id="setCurrentFormId">
</form>

<script>
    $(document).ready(function() { $('#yearsTable').DataTable({ order: [[1, 'desc']] }); });
    function confirmSetCurrent(id, label) {
        Swal.fire({
            title: 'Set ' + label + ' as the current academic year?',
            text: 'Whichever year is currently marked current will be un-marked automatically.',
            icon: 'question', showCancelButton: true, confirmButtonText: 'Set as current', confirmButtonColor: '#B8925A'
        }).then((r) => { if (r.isConfirmed) { document.getElementById('setCurrentFormId').value = id; document.getElementById('setCurrentForm').submit(); } });
    }
    <c:if test="${not empty errorMessage}">toastr.error('<c:out value="${errorMessage}"/>');</c:if>
</script>
<%@ include file="/common/fragments/admin-foot.jspf" %>
