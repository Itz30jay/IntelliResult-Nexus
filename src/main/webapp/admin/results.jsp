<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Results" scope="request"/>
<c:set var="pageSubtitle" value="Ranked by percentage, with full mark detail" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<%--
    Upgrade: rebuilt from a flat per-subject Result list for one exam into a
    ranked, filterable, exportable per-STUDENT view - ResultSummary.
    overallRank (already computed by a RANK()...ORDER BY overall_percentage
    DESC native query every time an exam is published) is what "ranking by
    percentage, high to low" actually means here; this page just never
    surfaced it before. History/Correct (Sec. 13/47's audit trail) are
    unchanged - reached from each row's expanded subject breakdown below.
--%>
<c:if test="${not empty errorMessage}">
    <div class="auth-error" role="alert" style="margin-bottom:var(--space-6);"><c:out value="${errorMessage}"/></div>
</c:if>
<c:if test="${not empty successMessage}">
    <div class="auth-success" role="status" style="margin-bottom:var(--space-6);"><c:out value="${successMessage}"/></div>
</c:if>

<c:choose>
<c:when test="${empty exams}">
    <div class="panel panel-empty"><p>No exams exist yet. Create one under Exams first.</p></div>
</c:when>
<c:otherwise>

<div class="panel" style="margin-bottom:var(--space-6);">
    <form method="get" action="${ctx}/admin/results" id="filterForm" style="display:flex; gap:var(--space-3); align-items:flex-end; flex-wrap:wrap;">
        <div class="field" style="margin-bottom:0; min-width:170px;">
            <label for="semesterFilter">Semester <span style="font-weight:400; color:var(--color-ink-soft);">(narrows Exam)</span></label>
            <select id="semesterFilter" onchange="filterExamsBySemester()">
                <option value="">All semesters</option>
                <c:forEach items="${semesters}" var="sem">
                    <option value="${sem.id}">Sem <c:out value="${sem.semesterNumber}"/> &mdash; <c:out value="${sem.course.code}"/> (<c:out value="${sem.academicYear.label}"/>)</option>
                </c:forEach>
            </select>
        </div>
        <div class="field" style="margin-bottom:0; min-width:200px;">
            <label for="examId">Exam</label>
            <select id="examId" name="examId" onchange="document.getElementById('filterForm').submit();">
                <c:forEach items="${exams}" var="e">
                    <option value="${e.id}" data-semester="${e.semester.id}" ${e.id == selectedExamId ? 'selected' : ''}>
                        <c:out value="${e.name}"/> (Sem <c:out value="${e.semester.semesterNumber}"/> <c:out value="${e.semester.course.code}"/>)
                    </option>
                </c:forEach>
            </select>
        </div>
        <div class="field" style="margin-bottom:0; min-width:150px;">
            <label for="sectionId">Section</label>
            <select id="sectionId" name="sectionId">
                <option value="">All sections</option>
                <c:forEach items="${sections}" var="sec">
                    <option value="${sec.id}" ${sec.id == filters.sectionId ? 'selected' : ''}><c:out value="${sec.name}"/> &mdash; Sem <c:out value="${sec.semester.semesterNumber}"/> <c:out value="${sec.semester.course.code}"/></option>
                </c:forEach>
            </select>
        </div>
        <div class="field" style="margin-bottom:0; min-width:80px;">
            <label for="rankFrom">Rank from</label>
            <input type="number" id="rankFrom" name="rankFrom" min="1" value="${filters.rankFrom}">
        </div>
        <div class="field" style="margin-bottom:0; min-width:80px;">
            <label for="rankTo">to</label>
            <input type="number" id="rankTo" name="rankTo" min="1" value="${filters.rankTo}">
        </div>
        <div class="field" style="margin-bottom:0; min-width:120px;">
            <label for="passOnly">Result</label>
            <select id="passOnly" name="passOnly">
                <option value="">All</option>
                <option value="true" ${filters.passOnly == true ? 'selected' : ''}>Pass only</option>
                <option value="false" ${filters.passOnly == false ? 'selected' : ''}>Fail only</option>
            </select>
        </div>
        <div class="field" style="margin-bottom:0; flex:1; min-width:160px;">
            <label for="search">Search name / roll no</label>
            <input type="text" id="search" name="search" value="<c:out value='${filters.search}'/>" placeholder="e.g. roll number or student name">
        </div>
        <button type="submit" class="btn-primary" style="width:auto; padding:0.65rem 1.25rem;">Apply filters</button>
        <c:if test="${not empty selectedExamId}">
            <a href="${ctx}/admin/results/export?examId=${selectedExamId}&sectionId=${filters.sectionId}&rankFrom=${filters.rankFrom}&rankTo=${filters.rankTo}&passOnly=${filters.passOnly}&search=${filters.search}"
               class="btn-secondary" style="width:auto; padding:0.65rem 1.25rem; text-decoration:none; display:inline-block;">&#8681; Export Excel</a>
        </c:if>
    </form>
</div>

<div class="panel">
    <c:choose>
        <c:when test="${empty rankedSummaries}">
            <div class="panel-empty">
                <p>No published results match these filters yet. Results appear here once an exam is published for at least one student.</p>
            </div>
        </c:when>
        <c:otherwise>
            <div class="table-scroll">
            <table id="resultsTable" class="display" style="width:100%">
                <thead>
                <tr>
                    <th>Rank</th><th>Roll No</th><th>Name</th><th>Section</th>
                    <th>Marks</th><th>%</th><th>Grade</th><th>SGPA</th><th>CGPA</th><th>Result</th><th></th>
                </tr>
                </thead>
                <tbody>
                <c:forEach items="${rankedSummaries}" var="s">
                    <tr>
                        <td class="rank"><c:out value="${s.overallRank}"/></td>
                        <td><c:out value="${s.student.rollNo}"/></td>
                        <td><c:out value="${s.student.user.fullName}"/></td>
                        <td><c:out value="${not empty s.student.currentSection ? s.student.currentSection.name : '-'}"/></td>
                        <td><c:out value="${s.totalObtainedMarks}"/> / <c:out value="${s.totalMaxMarks}"/></td>
                        <td><c:out value="${s.overallPercentage}"/>%</td>
                        <td><c:out value="${s.overallGrade}"/></td>
                        <td><c:out value="${not empty s.sgpa ? s.sgpa : '-'}"/></td>
                        <td><c:out value="${not empty s.cgpa ? s.cgpa : '-'}"/></td>
                        <td>
                            <c:choose>
                                <c:when test="${s.pass}"><span class="badge" style="background:#EAF3EE;color:var(--color-verified);">PASS</span></c:when>
                                <c:otherwise><span class="badge" style="background:#FBEEEF;color:var(--color-danger);">FAIL</span></c:otherwise>
                            </c:choose>
                        </td>
                        <td><a href="#" onclick="toggleDetail('detail-${s.student.id}'); return false;">Details</a></td>
                    </tr>
                    <tr id="detail-${s.student.id}" style="display:none; background:var(--color-paper-soft);">
                        <td colspan="11" style="padding:var(--space-3) var(--space-4);">
                            <strong style="font-size:0.85rem;">Full mark details</strong>
                            <table style="width:100%; margin-top:var(--space-2); font-size:0.9rem;">
                                <thead><tr><th style="text-align:left;">Subject</th><th style="text-align:left;">Theory</th><th style="text-align:left;">Practical</th><th style="text-align:left;">Internal</th><th style="text-align:left;">Total</th><th style="text-align:left;">Grade</th><th></th></tr></thead>
                                <tbody>
                                <c:forEach items="${breakdownByStudent[s.student.id]}" var="r">
                                    <tr>
                                        <td><c:out value="${r.subject.subjectCode}"/> &mdash; <c:out value="${r.subject.subjectName}"/></td>
                                        <td><c:out value="${not empty r.theoryMarks ? r.theoryMarks : '-'}"/></td>
                                        <td><c:out value="${not empty r.practicalMarks ? r.practicalMarks : '-'}"/></td>
                                        <td><c:out value="${not empty r.internalMarks ? r.internalMarks : '-'}"/></td>
                                        <td><c:out value="${r.totalMarks}"/></td>
                                        <td><c:out value="${r.grade}"/></td>
                                        <td><a href="${ctx}/admin/results/history?resultId=${r.id}">History</a></td>
                                    </tr>
                                </c:forEach>
                                </tbody>
                            </table>
                        </td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
            </div>
        </c:otherwise>
    </c:choose>
</div>

</c:otherwise>
</c:choose>

<script>
    $(document).ready(function() {
        if ($('#resultsTable tr').length) {
            $('#resultsTable').DataTable({ order: [], paging: true, pageLength: 25, searching: false });
        }
    });
    function toggleDetail(id) {
        var row = document.getElementById(id);
        row.style.display = (row.style.display === 'none') ? 'table-row' : 'none';
    }
    // Semester is a client-side pre-filter over the Exam dropdown's own
    // options (each carries data-semester) - a ResultSummary belongs to
    // exactly one Exam already, so Semester never needs to reach the server
    // as its own filter, only to make finding the right Exam faster when
    // there are many spread across 8 semesters.
    function filterExamsBySemester() {
        var semId = document.getElementById('semesterFilter').value;
        var examSelect = document.getElementById('examId');
        var options = examSelect.querySelectorAll('option');
        var firstVisible = null;
        options.forEach(function(opt) {
            var show = !semId || opt.getAttribute('data-semester') === semId;
            opt.style.display = show ? '' : 'none';
            if (show && !firstVisible) { firstVisible = opt; }
        });
        if (firstVisible && examSelect.options[examSelect.selectedIndex].style.display === 'none') {
            firstVisible.selected = true;
        }
    }
</script>
<%@ include file="/common/fragments/admin-foot.jspf" %>
