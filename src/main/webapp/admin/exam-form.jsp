<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="isEdit" value="${not empty editingExam}" scope="request"/>
<%-- PUBLISHED/LOCKED exams render this same page read-only rather than
     blocking access to it - ExamService.requireEditable() is the real
     enforcement (a direct POST here for such an exam is rejected server
     side regardless of what this flag says); this is only about what the
     page shows, matching Sec. 42's "empty/error states are informative,
     not just absent." --%>
<c:set var="isEditable" value="${!isEdit || (editingExam.status.name() != 'PUBLISHED' && editingExam.status.name() != 'LOCKED')}" scope="request"/>
<c:set var="pageTitle" value="${!isEdit ? 'Add Exam' : (isEditable ? 'Edit Exam' : 'View Exam')}" scope="request"/>
<c:set var="pageSubtitle" value="Examinations" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<div class="panel" style="max-width:620px;">
    <c:if test="${not empty errorMessage}">
        <div class="auth-error" role="alert"><c:out value="${errorMessage}"/></div>
    </c:if>

    <c:if test="${isEdit}">
        <%-- Sec. 9's lifecycle, drawn once here rather than in the list -
             a full 7-stage stepper needs more width than a table cell can
             spare, so the compact badge covers the list and this detail
             page carries the rich version. Ordinal comparisons only (never
             an enum-to-string EL comparison) - see PHASE5E decisions doc
             for why that's the deliberately safer choice here. --%>
        <div class="stepper">
            <div class="stepper-step ${editingExam.status.ordinal() > 0 ? 'done' : (editingExam.status.ordinal() == 0 ? 'current' : '')}"><span class="stepper-dot"></span>Created</div>
            <div class="stepper-step ${editingExam.status.ordinal() > 1 ? 'done' : (editingExam.status.ordinal() == 1 ? 'current' : '')}"><span class="stepper-dot"></span>Scheduled</div>
            <div class="stepper-step ${editingExam.status.ordinal() > 2 ? 'done' : (editingExam.status.ordinal() == 2 ? 'current' : '')}"><span class="stepper-dot"></span>Active</div>
            <div class="stepper-step ${editingExam.status.ordinal() > 3 ? 'done' : (editingExam.status.ordinal() == 3 ? 'current' : '')}"><span class="stepper-dot"></span>Submission</div>
            <div class="stepper-step ${editingExam.status.ordinal() > 4 ? 'done' : (editingExam.status.ordinal() == 4 ? 'current' : '')}"><span class="stepper-dot"></span>Approval</div>
            <div class="stepper-step ${editingExam.status.ordinal() > 5 ? 'done' : (editingExam.status.ordinal() == 5 ? 'current' : '')}"><span class="stepper-dot"></span>Published</div>
            <div class="stepper-step ${editingExam.status.ordinal() == 6 ? 'current' : ''}"><span class="stepper-dot"></span>Locked</div>
        </div>
        <c:if test="${!isEditable}">
            <p class="field-hint" style="margin-bottom:var(--space-6);">
                This exam is <c:out value="${fn:toLowerCase(editingExam.status)}"/> and its details can no longer be edited.
                <c:if test="${editingExam.status.name() != 'LOCKED'}">You can still advance it to the next stage below.</c:if>
            </p>
        </c:if>
    </c:if>

    <c:choose>
        <c:when test="${!isEdit && empty semesters}">
            <p class="panel-empty">
                No semesters exist yet. Create at least one course and semester under
                <a href="${ctx}/admin/academic-setup">Academic Setup</a> before scheduling an exam.
            </p>
        </c:when>
        <c:otherwise>
            <c:if test="${!isEdit || isEditable}">
            <form method="post" action="${ctx}/admin/exams">
                <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
                <c:if test="${isEdit}"><input type="hidden" name="id" value="${editingExam.id}"></c:if>

                <c:choose>
                    <c:when test="${isEdit}">
                        <div class="field">
                            <label>Semester</label>
                            <div class="field-hint" style="padding:0.4rem 0;">
                                Sem <c:out value="${editingExam.semester.semesterNumber}"/> &mdash; <c:out value="${editingExam.semester.course.code}"/> (<c:out value="${editingExam.semester.academicYear.label}"/>)
                                <br>Fixed once created - see PHASE5E decisions doc for why.
                            </div>
                        </div>
                        <div class="field">
                            <label>Exam type</label>
                            <div class="field-hint" style="padding:0.4rem 0;"><c:out value="${fn:replace(editingExam.examType, '_', ' ')}"/></div>
                        </div>
                    </c:when>
                    <c:otherwise>
                        <div class="field">
                            <label for="semesterId">Semester</label>
                            <select id="semesterId" name="semesterId" required>
                                <option value="">Select a semester&hellip;</option>
                                <c:forEach items="${semesters}" var="sem">
                                    <option value="${sem.id}" ${param.semesterId == sem.id.toString() ? 'selected' : ''}>
                                        Sem <c:out value="${sem.semesterNumber}"/> &mdash; <c:out value="${sem.course.code}"/> (<c:out value="${sem.academicYear.label}"/>)
                                    </option>
                                </c:forEach>
                            </select>
                            <c:if test="${not empty fieldErrors.semesterId}"><div class="field-error"><c:out value="${fieldErrors.semesterId}"/></div></c:if>
                        </div>
                        <div class="field">
                            <label for="examType">Exam type</label>
                            <select id="examType" name="examType" required>
                                <option value="">Select a type&hellip;</option>
                                <c:forEach items="${examTypes}" var="type">
                                    <option value="${type}" ${param.examType == type.toString() ? 'selected' : ''}><c:out value="${fn:replace(type, '_', ' ')}"/></option>
                                </c:forEach>
                            </select>
                            <c:if test="${not empty fieldErrors.examType}"><div class="field-error"><c:out value="${fieldErrors.examType}"/></div></c:if>
                        </div>
                    </c:otherwise>
                </c:choose>

                <div class="field">
                    <label for="name">Exam name</label>
                    <input type="text" id="name" name="name" required value="<c:out value='${isEdit ? editingExam.name : param.name}'/>">
                    <c:if test="${not empty fieldErrors.name}"><div class="field-error"><c:out value="${fieldErrors.name}"/></div></c:if>
                </div>
                <div class="field">
                    <label for="startTime">Start time</label>
                    <input type="datetime-local" id="startTime" name="startTime" required value="<c:out value='${isEdit ? editingExam.startTime : param.startTime}'/>">
                    <c:if test="${not empty fieldErrors.startTime}"><div class="field-error"><c:out value="${fieldErrors.startTime}"/></div></c:if>
                </div>
                <div class="field">
                    <label for="endTime">End time</label>
                    <input type="datetime-local" id="endTime" name="endTime" required value="<c:out value='${isEdit ? editingExam.endTime : param.endTime}'/>">
                    <c:if test="${not empty fieldErrors.endTime}"><div class="field-error"><c:out value="${fieldErrors.endTime}"/></div></c:if>
                </div>
                <div class="field">
                    <label for="attemptLimit">Attempt limit</label>
                    <input type="number" id="attemptLimit" name="attemptLimit" min="1" step="1" required
                           value="<c:out value='${isEdit ? editingExam.attemptLimit : (empty param.attemptLimit ? 1 : param.attemptLimit)}'/>">
                    <div class="field-hint">How many times this exam may be conducted (e.g. 2 for a main sitting plus one supplementary/makeup).</div>
                    <c:if test="${not empty fieldErrors.attemptLimit}"><div class="field-error"><c:out value="${fieldErrors.attemptLimit}"/></div></c:if>
                </div>
                <div class="field">
                    <label for="defaultMaxMarks">Reference maximum marks <span style="font-weight:400; color:var(--color-ink-soft);">(optional)</span></label>
                    <input type="number" id="defaultMaxMarks" name="defaultMaxMarks" min="0.01" step="0.01" value="<c:out value='${isEdit ? editingExam.defaultMaxMarks : param.defaultMaxMarks}'/>">
                    <div class="field-hint">A typical maximum for this exam type (e.g. 100 for a Final Examination) - each subject's own maximum, set under Subjects, is what actually governs its marks.</div>
                    <c:if test="${not empty fieldErrors.defaultMaxMarks}"><div class="field-error"><c:out value="${fieldErrors.defaultMaxMarks}"/></div></c:if>
                </div>

                <div style="display:flex; gap:var(--space-3); margin-top:var(--space-6);">
                    <button type="submit" class="btn-primary" style="width:auto; padding:0.65rem 1.5rem;">${isEdit ? 'Save changes' : 'Create exam'}</button>
                    <a href="${ctx}/admin/exams" style="align-self:center; color:var(--color-ink-soft); text-decoration:none; font-size:0.9rem;">Cancel</a>
                </div>
            </form>
            </c:if>

            <c:if test="${isEdit && !isEditable}">
                <div class="field"><label>Exam name</label><div class="field-hint" style="padding:0.4rem 0;"><c:out value="${editingExam.name}"/></div></div>
                <div class="field"><label>Window</label><div class="field-hint" style="padding:0.4rem 0;"><c:out value="${editingExam.startTime}"/> &rarr; <c:out value="${editingExam.endTime}"/></div></div>
                <div class="field"><label>Attempt limit</label><div class="field-hint" style="padding:0.4rem 0;"><c:out value="${editingExam.attemptLimit}"/></div></div>
                <a href="${ctx}/admin/exams" style="color:var(--color-ink-soft); text-decoration:none; font-size:0.9rem;">&larr; Back to Exams</a>
            </c:if>

            <c:if test="${isEdit && editingExam.status.name() != 'LOCKED'}">
                <form method="post" action="${ctx}/admin/exams/advance-status" id="advanceForm" style="margin-top:var(--space-4); padding-top:var(--space-4); border-top:1px solid var(--color-rule);">
                    <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
                    <input type="hidden" name="id" value="${editingExam.id}">
                    <button type="button" onclick="confirmAdvance();" class="btn-secondary" style="width:auto; padding:0.65rem 1.5rem;">Advance to <c:out value="${editingExam.status.next()}"/></button>
                </form>
            </c:if>
        </c:otherwise>
    </c:choose>
</div>

<script>
    function confirmAdvance() {
        Swal.fire({
            title: 'Advance this exam?',
            text: 'Moves it to the next stage of its lifecycle.',
            icon: 'question', showCancelButton: true, confirmButtonText: 'Advance', confirmButtonColor: '#B8925A'
        }).then((r) => { if (r.isConfirmed) { document.getElementById('advanceForm').submit(); } });
    }
</script>
<%@ include file="/common/fragments/admin-foot.jspf" %>
