<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="My Classes" scope="request"/>
<c:set var="pageSubtitle" value="Active section assignments" scope="request"/>
<%@ include file="/common/fragments/teacher-head.jspf" %>

<div class="panel">
    <c:choose>
        <c:when test="${empty assignments}">
            <p class="panel-empty">No active assignments yet. An administrator assigns you to a subject and section under Teacher Assignments.</p>
        </c:when>
        <c:otherwise>
            <%-- Flat and sortable rather than grouped by section: at the
                 assignment counts a real teacher has, DataTables' own
                 column sort already gives the same "see everything for one
                 class together" view a nested group-by structure would,
                 without JSTL having to track "did the section change since
                 the last row" across forEach iterations - not something
                 core JSTL does cleanly without a scriptlet.
                 Upgrade: Class Name (the subject, from a teacher's seat
                 "my class" IS the subject+section pairing) and Timing are
                 new columns - class_start_time/class_end_time
                 live on the assignment itself (teacher_subjects), not on
                 the section, since two different subjects taught to the
                 same section legitimately meet at different times. A
                 one-time section gets a small badge next to its name so a
                 teacher can tell an ad-hoc batch apart from their regular
                 ongoing sections at a glance. --%>
            <table id="classesTable" class="display" style="width:100%">
                <thead>
                <tr>
                    <th>Class Name</th>
                    <th>Section</th>
                    <th>Timing</th>
                    <th>Credits</th>
                    <th>Assigned since</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach items="${assignments}" var="a">
                    <tr>
                        <td>
                            <c:out value="${a.subject.subjectCode}"/> &mdash; <c:out value="${a.subject.subjectName}"/>
                            <div class="field-hint">Sem <c:out value="${a.subject.semester.semesterNumber}"/> &mdash; <c:out value="${a.subject.semester.course.code}"/></div>
                        </td>
                        <td>
                            Sec <c:out value="${a.section.name}"/>
                            <c:if test="${a.section.sectionType == 'ONE_TIME'}"><br><span class="badge" style="background:#F6EFE3;color:var(--color-seal);">One-time</span></c:if>
                        </td>
                        <td>
                            <c:choose>
                                <c:when test="${not empty a.classStartTime and not empty a.classEndTime}">
                                    <c:out value="${a.classStartTime}"/> &ndash; <c:out value="${a.classEndTime}"/>
                                </c:when>
                                <c:otherwise><span style="color:var(--color-ink-soft);">Not scheduled</span></c:otherwise>
                            </c:choose>
                        </td>
                        <td><c:out value="${a.subject.credits}"/></td>
                        <td><c:out value="${a.assignedAt.toLocalDate()}"/></td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </c:otherwise>
    </c:choose>
</div>

<script>
    $(document).ready(function() {
        if ($('#classesTable tr').length) { $('#classesTable').DataTable({ order: [[0, 'asc']] }); }
    });
</script>
<%@ include file="/common/fragments/teacher-foot.jspf" %>
