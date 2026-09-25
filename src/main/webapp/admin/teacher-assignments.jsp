<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Teacher Assignments" scope="request"/>
<c:set var="pageSubtitle" value="Which teacher covers which subject, for which section" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<%--
    Upgrade: field order is now Teacher -> Semester (1-8) -> Department ->
    Subject -> Section -> Start/End time. Semester and Department are pure
    client-side filters over the full Subject list embedded below as JSON
    (subjectOptionsJson) - nothing is submitted to the server until
    "Assign" is pressed, so picking either one never reloads the page.
    Section is not one of the fields requested but the database requires
    one (teacher_subjects.section_id is NOT NULL, since a class has to
    apply to some group of students) - it appears once a Subject is chosen,
    filtered to sections in that Subject's own semester
    (sectionOptionsJson), so the section can never end up mismatched to a
    different Semester instance than the subject.
--%>
<div class="panel" style="margin-bottom:var(--space-6);">
    <h2>Assign a teacher</h2>
    <c:if test="${not empty errorMessage}">
        <div class="auth-error" role="alert" style="margin-bottom:var(--space-4);"><c:out value="${errorMessage}"/></div>
    </c:if>
    <form method="post" action="${ctx}/admin/teacher-assignments/assign" id="assignForm" onsubmit="return validateAssignForm();" style="display:flex; gap:var(--space-3); align-items:flex-end; flex-wrap:wrap;">
        <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">

        <div class="field" style="margin-bottom:0; flex:1; min-width:170px;">
            <label for="teacherId">1. Teacher</label>
            <select id="teacherId" name="teacherId" required>
                <option value="">Select&hellip;</option>
                <c:forEach items="${teachers}" var="t"><option value="${t.id}"><c:out value="${t.user.fullName}"/></option></c:forEach>
            </select>
        </div>

        <div class="field" style="margin-bottom:0; min-width:140px;">
            <label for="semesterNumber">2. Semester</label>
            <select id="semesterNumber" onchange="refreshSubjectOptions();" required>
                <option value="">Select&hellip;</option>
                <c:forEach begin="1" end="8" var="n"><option value="${n}">Semester ${n}</option></c:forEach>
            </select>
        </div>

        <div class="field" style="margin-bottom:0; min-width:170px;">
            <label for="departmentId">3. Department</label>
            <select id="departmentId" onchange="refreshSubjectOptions();" required>
                <option value="">Select&hellip;</option>
                <c:forEach items="${departments}" var="d"><option value="${d.id}"><c:out value="${d.name}"/></option></c:forEach>
            </select>
        </div>

        <div class="field" style="margin-bottom:0; flex:1; min-width:220px;">
            <label for="subjectId">4. Subject</label>
            <select id="subjectId" name="subjectId" onchange="refreshSectionOptions();" required disabled>
                <option value="">Select semester &amp; department first&hellip;</option>
            </select>
        </div>

        <div class="field" style="margin-bottom:0; min-width:170px;">
            <label for="sectionId">5. Section</label>
            <select id="sectionId" name="sectionId" required disabled>
                <option value="">Select a subject first&hellip;</option>
            </select>
        </div>

        <div class="field" style="margin-bottom:0; min-width:130px;">
            <label for="classStartTime">6. Start time</label>
            <input type="time" id="classStartTime" name="classStartTime" required>
        </div>
        <div class="field" style="margin-bottom:0; min-width:130px;">
            <label for="classEndTime">7. End time</label>
            <input type="time" id="classEndTime" name="classEndTime" required>
        </div>

        <button type="submit" class="btn-primary" style="width:auto; padding:0.65rem 1.5rem;">Assign</button>
    </form>
    <p class="field-hint" style="margin-top:var(--space-3);">
        A teacher cannot be assigned two classes with overlapping times - if the new time overlaps one of their existing assignments, the assignment is rejected with the conflicting class named.
    </p>
</div>

<div class="panel" style="margin-bottom:var(--space-6);">
    <h2>Active Assignments</h2>
    <c:choose>
        <c:when test="${empty activeAssignments}">
            <div class="panel-empty">No teacher assignments yet.</div>
        </c:when>
        <c:otherwise>
            <table id="activeTable" class="display" style="width:100%">
                <thead><tr><th>Teacher</th><th>Subject</th><th>Section</th><th>Timing</th><th>Assigned</th><th></th></tr></thead>
                <tbody>
                <c:forEach items="${activeAssignments}" var="a">
                    <tr>
                        <td><c:out value="${a.teacher.user.fullName}"/></td>
                        <td><c:out value="${a.subject.subjectCode}"/></td>
                        <td><c:out value="${a.section.name}"/> (Sem <c:out value="${a.section.semester.semesterNumber}"/> <c:out value="${a.section.semester.course.code}"/>)</td>
                        <td>
                            <c:choose>
                                <c:when test="${not empty a.classStartTime}"><c:out value="${a.classStartTime}"/>&ndash;<c:out value="${a.classEndTime}"/></c:when>
                                <c:otherwise><span style="color:var(--color-ink-soft);">&mdash;</span></c:otherwise>
                            </c:choose>
                            <a href="#" onclick="editTiming(${a.id}, '<c:out value="${a.classStartTime}"/>', '<c:out value="${a.classEndTime}"/>'); return false;" style="margin-left:0.5rem; font-size:0.8rem;">Edit</a>
                        </td>
                        <td><c:out value="${a.assignedAt}"/></td>
                        <td><a href="#" onclick="confirmUnassign(${a.id}); return false;" style="color:var(--color-danger);">Remove</a></td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </c:otherwise>
    </c:choose>
</div>

<c:if test="${not empty endedAssignments}">
<div class="panel">
    <h2>Assignment History</h2>
    <c:forEach items="${endedAssignments}" var="a">
        <div class="list-row">
            <span><c:out value="${a.teacher.user.fullName}"/> &mdash; <c:out value="${a.subject.subjectCode}"/> (<c:out value="${a.section.name}"/>)</span>
            <span style="color:var(--color-ink-soft); font-size:0.8rem;"><c:out value="${a.assignedAt}"/> to <c:out value="${a.unassignedAt}"/></span>
        </div>
    </c:forEach>
</div>
</c:if>

<form id="unassignForm" method="post" action="${ctx}/admin/teacher-assignments/unassign" style="display:none;">
    <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
    <input type="hidden" name="id" id="unassignFormId">
</form>
<form id="timingForm" method="post" action="${ctx}/admin/teacher-assignments/update-timing" style="display:none;">
    <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
    <input type="hidden" name="id" id="timingFormId">
    <input type="hidden" name="classStartTime" id="timingFormStart">
    <input type="hidden" name="classEndTime" id="timingFormEnd">
</form>

<script>
    // Server-rendered once per page load (see TeacherAssignmentServlet) -
    // every Subject/Section in the system, shaped for client-side
    // filtering. Values come from Jackson's ObjectMapper (the same
    // serializer already used for the dashboard's chart data), not
    // hand-built strings, so a subject/section name containing a quote or
    // special character can never break this script block.
    const allSubjects = ${subjectOptionsJson};
    const allSections = ${sectionOptionsJson};

    function refreshSubjectOptions() {
        const semesterNumber = document.getElementById('semesterNumber').value;
        const departmentId = document.getElementById('departmentId').value;
        const subjectSelect = document.getElementById('subjectId');
        const sectionSelect = document.getElementById('sectionId');

        sectionSelect.innerHTML = '<option value="">Select a subject first&hellip;</option>';
        sectionSelect.disabled = true;

        if (!semesterNumber || !departmentId) {
            subjectSelect.innerHTML = '<option value="">Select semester &amp; department first&hellip;</option>';
            subjectSelect.disabled = true;
            return;
        }

        const matches = allSubjects.filter(s =>
            String(s.semesterNumber) === semesterNumber && String(s.departmentId) === departmentId);

        if (matches.length === 0) {
            subjectSelect.innerHTML = '<option value="">No subjects for this semester &amp; department</option>';
            subjectSelect.disabled = true;
            return;
        }

        subjectSelect.innerHTML = '<option value="">Select&hellip;</option>' + matches.map(s =>
            '<option value="' + s.id + '" data-semester-id="' + s.semesterId + '">' +
            escapeHtml(s.code) + ' \u2014 ' + escapeHtml(s.name) + ' (' + escapeHtml(s.contextLabel) + ')</option>'
        ).join('');
        subjectSelect.disabled = false;
    }

    function refreshSectionOptions() {
        const subjectSelect = document.getElementById('subjectId');
        const sectionSelect = document.getElementById('sectionId');
        const selectedOption = subjectSelect.options[subjectSelect.selectedIndex];
        const semesterId = selectedOption ? selectedOption.getAttribute('data-semester-id') : null;

        if (!semesterId) {
            sectionSelect.innerHTML = '<option value="">Select a subject first&hellip;</option>';
            sectionSelect.disabled = true;
            return;
        }

        const matches = allSections.filter(sec => String(sec.semesterId) === semesterId);
        if (matches.length === 0) {
            sectionSelect.innerHTML = '<option value="">No sections in this semester yet</option>';
            sectionSelect.disabled = true;
            return;
        }

        sectionSelect.innerHTML = '<option value="">Select&hellip;</option>' + matches.map(sec =>
            '<option value="' + sec.id + '">' + escapeHtml(sec.name) + '</option>'
        ).join('');
        sectionSelect.disabled = false;
    }

    function escapeHtml(value) {
        const div = document.createElement('div');
        div.textContent = value == null ? '' : String(value);
        return div.innerHTML;
    }

    /** Disabled fields are excluded from native required-field validation and from the submitted form data alike - so the one case native validation can't catch on its own is "Subject/Section are still disabled because nothing matched the chosen Semester+Department," which would otherwise submit with no subjectId/sectionId at all. */
    function validateAssignForm() {
        const subjectSelect = document.getElementById('subjectId');
        const sectionSelect = document.getElementById('sectionId');
        if (subjectSelect.disabled || !subjectSelect.value) {
            toastr.error('Choose a Semester and Department with at least one Subject before assigning.');
            return false;
        }
        if (sectionSelect.disabled || !sectionSelect.value) {
            toastr.error('No section is available for that Subject yet - add one under Academic Setup first.');
            return false;
        }
        return true;
    }

    $(document).ready(function() { if ($('#activeTable').length) { $('#activeTable').DataTable({ order: [[4, 'desc']] }); } });

    function confirmUnassign(id) {
        Swal.fire({
            title: 'Remove this assignment?', text: 'The teacher will no longer be able to enter marks for this subject/section. This is recorded in assignment history, not deleted.',
            icon: 'warning', showCancelButton: true, confirmButtonText: 'Remove', confirmButtonColor: '#A3384A'
        }).then((r) => { if (r.isConfirmed) { document.getElementById('unassignFormId').value = id; document.getElementById('unassignForm').submit(); } });
    }

    function editTiming(id, start, end) {
        Swal.fire({
            title: 'Set class timing',
            html:
                '<div style="text-align:left;">' +
                '<label style="font-size:0.85rem;">Start time</label>' +
                '<input id="swalStart" type="time" class="swal2-input" value="' + (start === 'null' ? '' : start) + '">' +
                '<label style="font-size:0.85rem;">End time</label>' +
                '<input id="swalEnd" type="time" class="swal2-input" value="' + (end === 'null' ? '' : end) + '">' +
                '</div>',
            showCancelButton: true, confirmButtonText: 'Save', confirmButtonColor: '#B8925A',
            preConfirm: () => ({
                start: document.getElementById('swalStart').value,
                end: document.getElementById('swalEnd').value
            })
        }).then((r) => {
            if (r.isConfirmed) {
                document.getElementById('timingFormId').value = id;
                document.getElementById('timingFormStart').value = r.value.start;
                document.getElementById('timingFormEnd').value = r.value.end;
                document.getElementById('timingForm').submit();
            }
        });
    }
</script>
<%@ include file="/common/fragments/admin-foot.jspf" %>
