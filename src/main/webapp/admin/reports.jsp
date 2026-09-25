<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Reports" scope="request"/>
<c:set var="pageSubtitle" value="Class, subject, topper, improvement, exam, and academic-year reports - PDF, Excel, or print" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<div class="panel">
    <form method="GET" action="${ctx}/admin/reports/view" target="_blank" id="reportForm">
        <div style="max-width:480px;">
            <label for="reportType">Report Type</label><br/>
            <select id="reportType" name="type" onchange="updateReportFields()" style="width:100%; margin-bottom:var(--space-4);">
                <option value="CLASS_RESULT">Class Result Report</option>
                <option value="SUBJECT_ANALYSIS">Subject Analysis</option>
                <option value="TOPPER">Topper Report</option>
                <option value="IMPROVEMENT">Improvement Report</option>
                <option value="EXAM_SUMMARY">Exam Report</option>
                <option value="ACADEMIC_YEAR">Academic Year Report</option>
            </select>

            <div id="examField" class="report-field" style="margin-bottom:var(--space-4);">
                <label for="examId">Examination</label><br/>
                <select id="examId" name="examId" style="width:100%;">
                    <option value="">Select an examination&hellip;</option>
                    <c:forEach items="${exams}" var="e">
                        <option value="${e.id}"><c:out value="${e.name}"/></option>
                    </c:forEach>
                </select>
            </div>

            <div id="sectionField" class="report-field" style="margin-bottom:var(--space-4);">
                <label for="sectionId">Section</label><br/>
                <select id="sectionId" name="sectionId" style="width:100%;">
                    <option value="">All sections</option>
                    <c:forEach items="${sections}" var="s">
                        <option value="${s.id}"><c:out value="${s.name}"/></option>
                    </c:forEach>
                </select>
            </div>

            <div id="subjectField" class="report-field" style="display:none; margin-bottom:var(--space-4);">
                <label for="subjectId">Subject</label><br/>
                <select id="subjectId" name="subjectId" style="width:100%;">
                    <option value="">Select a subject&hellip;</option>
                    <c:forEach items="${subjects}" var="s">
                        <option value="${s.id}"><c:out value="${s.subjectCode}"/> &mdash; <c:out value="${s.subjectName}"/></option>
                    </c:forEach>
                </select>
            </div>

            <div id="academicYearField" class="report-field" style="display:none; margin-bottom:var(--space-4);">
                <label for="academicYearId">Academic Year</label><br/>
                <select id="academicYearId" name="academicYearId" style="width:100%;">
                    <option value="">Select an academic year&hellip;</option>
                    <c:forEach items="${academicYears}" var="y">
                        <option value="${y.id}"><c:out value="${y.label}"/></option>
                    </c:forEach>
                </select>
            </div>

            <button type="submit" class="btn-primary" style="width:auto; padding:0.6rem 1.4rem;">Generate Report</button>
            <p style="color:var(--color-ink-soft); font-size:0.8rem; margin-top:var(--space-3);">
                Opens in a new tab, with Print, PDF, and Excel options.
            </p>
        </div>
    </form>
</div>

<script>
    function updateReportFields() {
        var type = document.getElementById('reportType').value;
        var showExam = type !== 'ACADEMIC_YEAR';
        var showSection = type === 'CLASS_RESULT' || type === 'TOPPER' || type === 'IMPROVEMENT';
        var showSubject = type === 'SUBJECT_ANALYSIS';
        var showYear = type === 'ACADEMIC_YEAR';

        document.getElementById('examField').style.display = showExam ? 'block' : 'none';
        document.getElementById('sectionField').style.display = showSection ? 'block' : 'none';
        document.getElementById('subjectField').style.display = showSubject ? 'block' : 'none';
        document.getElementById('academicYearField').style.display = showYear ? 'block' : 'none';
    }
    updateReportFields();
</script>

<%@ include file="/common/fragments/admin-foot.jspf" %>
