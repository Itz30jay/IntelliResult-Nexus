<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Academic Setup" scope="request"/>
<c:set var="pageSubtitle" value="Departments, courses, academic years, semesters, and sections" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<div class="stat-grid">
    <a href="${ctx}/admin/academic-setup/departments" class="stat-card" style="text-decoration:none; color:inherit; display:block;">
        <div class="label">Departments</div>
        <div class="value"><fmt:formatNumber value="${departmentCount}"/></div>
    </a>
    <a href="${ctx}/admin/academic-setup/courses" class="stat-card" style="text-decoration:none; color:inherit; display:block;">
        <div class="label">Courses</div>
        <div class="value"><fmt:formatNumber value="${courseCount}"/></div>
    </a>
    <a href="${ctx}/admin/academic-setup/academic-years" class="stat-card" style="text-decoration:none; color:inherit; display:block;">
        <div class="label">Academic Years</div>
        <div class="value"><fmt:formatNumber value="${academicYearCount}"/></div>
    </a>
    <a href="${ctx}/admin/academic-setup/semesters" class="stat-card" style="text-decoration:none; color:inherit; display:block;">
        <div class="label">Semesters</div>
        <div class="value"><fmt:formatNumber value="${semesterCount}"/></div>
    </a>
    <a href="${ctx}/admin/academic-setup/sections" class="stat-card" style="text-decoration:none; color:inherit; display:block;">
        <div class="label">Sections</div>
        <div class="value"><fmt:formatNumber value="${sectionCount}"/></div>
    </a>
</div>

<div class="panel">
    <h2>Setup order</h2>
    <p style="color:var(--color-ink-soft); font-size:0.9rem; line-height:1.7;">
        These build on each other: a <b>Department</b> offers <b>Courses</b>; a Course, run within an <b>Academic Year</b>,
        has <b>Semesters</b>; each Semester divides into <b>Sections</b>. Subjects and Teacher Assignments (Phase 5d)
        attach to a Semester and Section respectively, so setting these up in roughly this order avoids empty dropdowns later.
    </p>
</div>

<%@ include file="/common/fragments/admin-foot.jspf" %>
