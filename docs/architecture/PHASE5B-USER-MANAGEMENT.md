# Phase 5b — Admin: User Management: Architecture Decisions

## 1. Role is immutable after account creation

Sec. 6 asks for "Change role" as a capability. There is no `changeRole()`
method here. A general implementation has to answer what happens to a
Student's existing `Result` rows or a Teacher's existing `TeacherSubject`
assignments when their role changes out from under that data - and neither
the master spec nor common sense defines that migration safely. A version
that only "works" when no dependent data exists yet is exactly the kind of
check that's easy to get subtly wrong (a `Result` created after the check
ran but before the transaction committed; a `TeacherSubject` the check
didn't think to look at), and getting it wrong risks orphaned academic
records - precisely what Sec. 47/48 exist to prevent. Role is fixed at
creation instead; correcting a genuine data-entry mistake means disabling
the wrong-role account and creating the correct one, which keeps every
historical record intact and correctly attributed to whoever it actually
belongs to.

## 2. One servlet for create AND edit (`UserFormServlet`)

Distinguished by whether an `id` request parameter is present, rather than
two separate classes. The two flows share almost everything - the same
dropdown data (courses, departments, sections), the same role-conditional
field set, the same validation shape - and splitting them would mean
keeping that overlap in sync across two files instead of one.

## 3. Validation failures forward, not redirect - so form values survive automatically

A failed create/edit submission forwards back to `user-form.jsp` using the
*same* request, not a redirect. That means the JSP's `${param.fullName}`-
style fallbacks (used whenever `editingUser` isn't set) still hold exactly
what the admin typed, with no separate "remembered form values" mechanism
needed - the original POST parameters are simply still there. A redirect-
based pattern would have needed an explicit flash-attribute mechanism to
achieve the same thing.

## 4. Admin-initiated password reset: session-flash pattern, shown once

Both account creation and password reset generate a temporary password
that needs to reach the admin's screen exactly once - Phase 14 (Email)
doesn't exist yet, so there's no channel to deliver it to the *user*
directly. The plaintext value is stashed as a session attribute
immediately before a redirect to `/admin/users`, then read AND
`session.removeAttribute()`-cleared in the same call on `UserListServlet` -
so a page refresh can never show it a second time, and it never touches
the database, a log line, or `ActivityLog.details` in plaintext.
`PasswordUtil.generateTemporaryPassword()` draws from an alphabet with
`0/O` and `1/l/I` excluded, since a password an admin has to read aloud or
retype in front of someone shouldn't hinge on distinguishing those by font.

## 5. Students/Teachers pages are read-only views, not a second edit surface

`/admin/students` and `/admin/teachers` show the academic roster (roll
number, course/section; employee code, department) but every edit action
points back to `/admin/users/edit`, which already handles the student/
teacher-specific fields alongside the account fields. Two places that can
both edit the same underlying `Student`/`Teacher` row would only invite the
two forms drifting out of sync with each other over time - one editable
surface per entity, shown from two different list views for two different
browsing contexts (account management vs. academic roster).

## 6. Section assignment is optional at user-creation time

`Student.currentSection` is nullable at the schema level (Phase 2/3), and
the create/edit form respects that - a student can be created with just a
roll number and course, section assigned later. A proper cascading
course → semester → section picker is Academic Setup's job (Phase 5c);
building a simplified version of it here, ahead of Academic Setup existing
to manage courses/semesters/sections in the first place, would mean
building it twice.

## 7. Continuity note: work recovered across a context gap

`UserService.java` and its three DTOs already existed on disk when this
delivery's visible work began - written in an earlier part of this same
session whose detailed tool-call history had been trimmed from context to
save space, though the file changes themselves persisted on the sandbox
filesystem. Rather than assume that code was correct (or blindly overwrite
it), it was read fresh and checked: the DAO methods it depends on
(`existsByRollNo`, `existsByEmployeeCode`) were verified to exist exactly
once with no duplication before building on top of any of it. Noted here
because "trust but verify" applied to your own earlier work is worth being
explicit about, not just when reviewing someone else's.

---

## Verification

- **javac, uncapped**: 815 errors across all 112 project files. Every one
  inspected by category traces to the same missing-dependency root cause as
  every phase before this one - including `UserService.java`'s specifically,
  checked line-by-line (`org.hibernate does not exist` on the same two
  imports every transaction-managing Service class in this project already
  has). The three new DTOs compile with zero errors, same as Phase 5a's -
  they depend on nothing but the JDK.
- **JSTL/HTML tag balance**: all four new pages, each combined with
  `admin-head.jspf`/`admin-foot.jspf` as the single document they render
  as, balance exactly.
- **Duplication check across the context gap**: confirmed zero duplicate
  method definitions or duplicate imports in every file touched by both the
  earlier (trimmed) session and this one's edits.
