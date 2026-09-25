<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="isEdit" value="${not empty editingNotice}" scope="request"/>
<c:set var="pageTitle" value="${isEdit ? 'Edit Notice' : 'Add Notice'}" scope="request"/>
<c:set var="pageSubtitle" value="Notice Board" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<div class="panel" style="max-width:620px;">
    <c:if test="${not empty errorMessage}">
        <div class="auth-error" role="alert"><c:out value="${errorMessage}"/></div>
    </c:if>

    <c:if test="${isEdit}">
        <p class="field-hint" style="margin-bottom:var(--space-4);">
            <c:choose>
                <c:when test="${editingNotice.published}">Published <c:out value="${editingNotice.publishedDate.toLocalDate()}"/>.</c:when>
                <c:otherwise>Not yet published.</c:otherwise>
            </c:choose>
            Editing here never requires unpublishing first - see PHASE5F decisions doc for why notices don't lock the way exams do.
        </p>
    </c:if>

    <form method="post" action="${ctx}/admin/notices">
        <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
        <c:if test="${isEdit}"><input type="hidden" name="id" value="${editingNotice.id}"></c:if>

        <div class="field">
            <label for="title">Title</label>
            <input type="text" id="title" name="title" required maxlength="200"
                   value="<c:out value='${isEdit ? editingNotice.title : param.title}'/>">
            <c:if test="${not empty fieldErrors.title}"><div class="field-error"><c:out value="${fieldErrors.title}"/></div></c:if>
        </div>

        <div class="field">
            <label for="content">Content</label>
            <textarea id="content" name="content" required rows="5"><c:out value="${isEdit ? editingNotice.content : param.content}"/></textarea>
            <c:if test="${not empty fieldErrors.content}"><div class="field-error"><c:out value="${fieldErrors.content}"/></div></c:if>
        </div>

        <div class="field">
            <label>Audience</label>
            <div style="display:flex; gap:var(--space-4); padding:0.4rem 0;">
                <label style="display:flex; align-items:center; gap:0.4rem; font-weight:400;">
                    <input type="checkbox" name="audience" value="ADMIN" ${audienceHasAdmin ? 'checked' : ''}> Admin
                </label>
                <label style="display:flex; align-items:center; gap:0.4rem; font-weight:400;">
                    <input type="checkbox" name="audience" value="TEACHER" ${audienceHasTeacher ? 'checked' : ''}> Teacher
                </label>
                <label style="display:flex; align-items:center; gap:0.4rem; font-weight:400;">
                    <input type="checkbox" name="audience" value="STUDENT" ${audienceHasStudent ? 'checked' : ''}> Student
                </label>
            </div>
            <c:if test="${not empty fieldErrors.audience}"><div class="field-error"><c:out value="${fieldErrors.audience}"/></div></c:if>
        </div>

        <div class="field">
            <label for="priority">Priority</label>
            <select id="priority" name="priority" required>
                <c:forEach items="${priorities}" var="type">
                    <option value="${type}"
                        <c:choose>
                            <c:when test="${isEdit}">${editingNotice.priority == type ? 'selected' : ''}</c:when>
                            <c:otherwise>${param.priority == type.toString() ? 'selected' : ''}</c:otherwise>
                        </c:choose>
                    ><c:out value="${type}"/></option>
                </c:forEach>
            </select>
            <c:if test="${not empty fieldErrors.priority}"><div class="field-error"><c:out value="${fieldErrors.priority}"/></div></c:if>
        </div>

        <div class="field">
            <label for="expiryDate">Expiry date <span style="font-weight:400; color:var(--color-ink-soft);">(optional)</span></label>
            <input type="date" id="expiryDate" name="expiryDate"
                   value="<c:out value='${isEdit ? expiryDateValue : param.expiryDate}'/>">
            <div class="field-hint">The notice stays visible through the end of the chosen day. Leave blank for a notice with no expiry.</div>
            <c:if test="${not empty fieldErrors.expiryDate}"><div class="field-error"><c:out value="${fieldErrors.expiryDate}"/></div></c:if>
        </div>

        <div style="display:flex; gap:var(--space-3); margin-top:var(--space-6);">
            <button type="submit" class="btn-primary" style="width:auto; padding:0.65rem 1.5rem;">${isEdit ? 'Save changes' : 'Create notice'}</button>
            <a href="${ctx}/admin/notices" style="align-self:center; color:var(--color-ink-soft); text-decoration:none; font-size:0.9rem;">Cancel</a>
        </div>
    </form>

    <c:if test="${isEdit}">
        <form method="post" action="${ctx}/admin/notices/${editingNotice.published ? 'unpublish' : 'publish'}"
              style="margin-top:var(--space-4); padding-top:var(--space-4); border-top:1px solid var(--color-rule);">
            <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
            <input type="hidden" name="id" value="${editingNotice.id}">
            <button type="submit" class="btn-secondary" style="width:auto; padding:0.65rem 1.5rem;">${editingNotice.published ? 'Unpublish' : 'Publish'} this notice</button>
        </form>
    </c:if>
</div>

<%@ include file="/common/fragments/admin-foot.jspf" %>
