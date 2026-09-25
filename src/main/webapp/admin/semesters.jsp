<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Semesters" scope="request"/>
<c:set var="pageSubtitle" value="Academic Setup" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<div style="display:flex; justify-content:flex-end; margin-bottom: var(--space-4);">
    <a href="${ctx}/admin/academic-setup/semesters/new" class="btn-primary" style="width:auto; text-decoration:none; display:inline-block; padding:0.6rem 1.2rem;">+ Add Semester</a>
</div>

<c:forEach items="${semestersByCourse}" var="entry">
    <div class="panel" style="margin-bottom:var(--space-4);">
        <h2><c:out value="${entry.key.name}"/> (<c:out value="${entry.key.code}"/>)</h2>
        <c:choose>
            <c:when test="${empty entry.value}">
                <div class="panel-empty">No semesters set up yet for this course.</div>
            </c:when>
            <c:otherwise>
                <c:forEach items="${entry.value}" var="sem">
                    <div class="list-row" style="align-items:flex-start; flex-direction:column; gap:var(--space-2);">
                        <div style="display:flex; justify-content:space-between; width:100%; align-items:baseline;">
                            <span>Semester <c:out value="${sem.semesterNumber}"/> &mdash; <c:out value="${sem.academicYear.label}"/>
                                <c:if test="${not empty sem.startDate}"> (<c:out value="${sem.startDate}"/> to <c:out value="${sem.endDate}"/>)</c:if>
                            </span>
                            <a href="${ctx}/admin/academic-setup/semesters/edit?id=${sem.id}">Edit</a>
                        </div>
                        <%--
                            Upgrade: sections for this exact semester, shown
                            right here, with an Add Section link that
                            pre-fills semesterId - section-form.jsp already
                            read that query param, nothing previously linked
                            to it with one set.
                        --%>
                        <div style="width:100%; padding-left:var(--space-4); border-left:2px solid var(--color-rule);">
                            <c:choose>
                                <c:when test="${empty sectionsBySemesterId[sem.id]}">
                                    <span style="color:var(--color-ink-soft); font-size:0.85rem;">No sections yet.</span>
                                </c:when>
                                <c:otherwise>
                                    <c:forEach items="${sectionsBySemesterId[sem.id]}" var="sec">
                                        <span class="badge" style="background:var(--color-paper-soft); color:var(--color-ink); margin:0 0.3rem 0.3rem 0;">
                                            Sec <c:out value="${sec.name}"/><c:if test="${sec.sectionType == 'ONE_TIME'}"> &middot; one-time</c:if>
                                        </span>
                                    </c:forEach>
                                </c:otherwise>
                            </c:choose>
                            <a href="${ctx}/admin/academic-setup/sections/new?semesterId=${sem.id}" style="font-size:0.85rem; margin-left:0.5rem;">+ Add Section</a>
                        </div>
                    </div>
                </c:forEach>
            </c:otherwise>
        </c:choose>
    </div>
</c:forEach>

<script>
    <c:if test="${not empty errorMessage}">toastr.error('<c:out value="${errorMessage}"/>');</c:if>
</script>
<%@ include file="/common/fragments/admin-foot.jspf" %>
