# Phase 5e — Exam Management + Grading Rules

Continues Phase 5 (Admin) from where Phase 5d left off. Entities and DAOs for
`Exam` and `GradingRule` already existed from Phase 3 - this phase adds the
service, controller, and view layers that turn them into working admin
screens, plus one real bug fix found while getting there.

## Starting point (verified before writing anything)

Read in full before any new code: `pom.xml`; `schema.sql`'s `exams`,
`grading_rules`, `semesters` tables; `Exam.java`, `GradingRule.java`,
`ExamStatus.java`, `ExamType.java`; `ExamDAO(Impl)`, `GradingRuleDAO(Impl)`;
`SubjectService.java` and `TeacherAssignmentService.java` (transaction
pattern); `AcademicSetupService.java` (the `inTransaction` helper variant);
`SectionServlet.java`, `TeacherAssignmentServlet.java` (controller shape);
`web.xml`, `AuthorizationFilter.java`, `CsrfFilter.java`; `NavigationUtil`,
`AppConstants`, `ValidationUtil`; `admin-head.jspf` / `admin-foot.jspf`;
`tokens.css` / `admin-shell.css` / `auth.css`; `section-form.jsp`,
`sections.jsp`, `course-form.jsp`, `semester-form.jsp`, `department-form.jsp`.
`admin-head.jspf` already links `/admin/exams` and `/admin/grading-rules` in
the sidebar - both 404'd before this phase, exactly as `NavigationUtil`'s own
comment describes for not-yet-built routes.

## A real bug found and fixed

`department-form.jsp`, `course-form.jsp`, `section-form.jsp`, and 12 other
Phase 5a-5d admin JSPs reference the CSS classes `.btn-primary`, `.field`,
`.field label`, `.field input`, and `.auth-error`. All of them are defined
in `auth.css` - which `admin-head.jspf` never links; it only links
`tokens.css` and `admin-shell.css`. Every admin form's submit button and
every text input has been rendering with no styling at all: browser-default
button chrome, no border-radius, no padding, none of the design system.

Confirmed with `grep -rl "btn-primary" src/main/webapp/admin/` (15 files)
before touching anything, then fixed at the root rather than patched
per-file: `.field`, `.field input`, `.btn-primary`, `.auth-error`, and
`.auth-success` moved from `auth.css` into `tokens.css` - the one stylesheet
every page (auth and admin alike) already loads. This is a relocation, not a
rename: every class name is byte-for-byte identical, so none of the 15
existing JSPs needed to change. `auth.css` keeps a comment at the old
location pointing to the new one rather than silently going quiet.

Two genuine additions rode along with the fix, since Phase 5e's own forms
need them and the login form never did: `.field select` / `.field textarea`
(login has no dropdowns or textareas) and `.btn-secondary` (login never
needed two buttons of comparable weight side by side; the exam edit page's
"Save changes" + "Advance to X" does).

## Decisions

**1. `Exam.academicYear` is set from the chosen semester, never asked for
separately.** `exams.academic_year_id` is a real column (query convenience,
per `PHASE2-DATABASE.md`), but `Semester` already carries its own
`academic_year_id`. `ExamService.createExam` takes only a `semesterId` and
sets `academicYear = semester.getAcademicYear()` - the same reasoning
`AcademicSetupService` applies when creating a `Section` from a `Semester`
alone: never let an admin pick two things that must always agree.

**2. Exam identity (`semester`, `examType`) is fixed at creation;
`updateExam` takes name/dates/defaultMaxMarks only - a narrower signature,
not the same DTO with fields ignored.** This mirrors
`AcademicSetupService.updateSemester(id, startDate, endDate)` rather than
`SubjectService.updateSubject(id, SubjectRequest)`, which does reuse one DTO
and silently ignores its semester/department fields on update. Both
precedents exist in the codebase; the narrower-signature one was chosen here
specifically so the edit form's read-only Semester/Exam Type display can
never be mistaken for something that round-trips through the form. The edit
JSP shows them as plain text, not disabled `<select>` elements, for the same
reason `semester-form.jsp` shows Course & Academic Year as text when editing.

**3. `advanceStatus` moves exactly one stage forward; there is no
jump-to-any-stage or backward transition.** Sec. 9 defines a strictly
forward lifecycle. `ExamStatus.next()` is the only thing that decides "what's
next," derived from enum declaration order (already documented on the enum
as matching lifecycle order). One generic action ("Advance") rather than six
named ones (`scheduleExam`, `activateExam`, ...) - DRY, and the human-readable
confirmation message is built from `next().name()` rather than duplicated
per transition.

**4. An exam can only be soft-deleted while `status == CREATED`.** Nothing
downstream (marks entry, results) can exist yet at that stage - deletion
past it would be removing something real work may already depend on, the
same "prevent unauthorized modification" concern Sec. 47 raises for results,
applied here to the one destructive action this phase has. Past `CREATED`,
the only way out is to advance the exam through to `LOCKED`.

**5. `PUBLISHED`/`LOCKED` exams render `exam-form.jsp` read-only rather than
blocking the page.** `ExamService.requireEditable()` is the actual
enforcement - a direct POST for a finalized exam is rejected server-side
regardless of what the page shows. Blocking navigation to the page entirely
would have hidden the one thing worth seeing on it once editing is closed:
the lifecycle stepper and, if not yet `LOCKED`, the advance action.

**6. Grading rule uniqueness-by-name, per active academic year, is enforced
even though Sec. 12's literal text only asks for overlap validation.**
Flagged explicitly, the same way `ResultHistory`'s internal-marks columns or
`RevaluationRequest.resolvedBy` were flagged as considered additions in
earlier phases: a grading scale is a partition of 0-100 into distinctly
named bands, and letting "A+" exist twice at two non-overlapping ranges in
one year is a data-quality problem "prevent invalid configurations" clearly
intends even if "overlapping" doesn't literally name it.

**7. Both grading-rule cross-row checks (overlap, duplicate name) compare
only against *other active* rules, and re-run on reactivation, not just on
create/update.** A deactivated rule is out of force - it can't conflict with
anything, since `GradingRuleDAO.findActiveByAcademicYear` is the only query
Phase 7's grading lookup will ever run. But reactivating a rule *can*
reintroduce a conflict if something else was created while it sat off, so
`GradingRuleService.activate()` re-checks before flipping the flag rather
than trusting whatever was true when it was deactivated.

**8. Status/priority badges stay in the three tint families
`.badge-priority-*` already established** (neutral / brass-seal / verified
green), plus one new heavier treatment - solid ink-on-paper - reserved for
`LOCKED` alone, since Sec. 10 singles out locked as immutable and it's the
only status where a visually stronger signal seemed warranted. `LOCKED`
aside, `SCHEDULED`/`SUBMISSION`/`APPROVAL` intentionally share one tint
("admin attention, brass") and `ACTIVE`/`PUBLISHED` share another ("live and
correct, green") - the stepper carries the exact-stage distinction; the
badge doesn't need to.

**9. The lifecycle stepper avoids two JSTL/EL constructs that are valid EL
3.0+ but less battle-tested than plain property/method access: enum-to-
string `==` comparison, and collection literals (`${['a','b','c']}`) inside
`c:forEach`.** `${exam.status.name() == 'CREATED'}`-style comparisons never
appear anywhere in this phase; every stepper/badge decision instead compares
`.ordinal()` (a plain `int`) or checks `.status.name() != 'X'` against a
String result, never the enum itself. The stepper's 7 stages are 7 explicit
`<div>`s, not a loop over a literal list. This follows directly from the
`fmt:formatDate`-vs-`LocalDate` lesson in `PHASE5C-ACADEMIC-SETUP.md`: prefer
the construct that's unambiguously correct by inspection over the one that's
probably fine, anywhere a live JSP engine isn't available to confirm it.

**10. `flashSuccess` is introduced alongside the existing `flashError`
session pattern, used only by the two new controllers.** Sec. 45 wants
explicit positive confirmation for a workflow action ("Exam advanced to
Scheduled.") rather than a silent redirect the admin has to visually diff.
Earlier Phase 5 controllers aren't retrofitted with it - not a gap being
fixed here, just a lower-stakes surface (renaming a department) where a
re-rendered list already speaks for itself; nothing about the new pattern
requires touching them.

**11. A second real bug, found only by actually compiling: `findDeleted()` was
being called on 5 DAO interfaces that never declared it.** `DepartmentDAO`,
`CourseDAO`, `SectionDAO`, `SubjectDAO`, and this phase's own `ExamDAO` all
extended `GenericDAO<T, Long>` only. `findDeleted()`/`findAllIncludingDeleted()`
exist only on the concrete `AbstractSoftDeletableDAO` class, inherited by
`XyzDAOImpl` but never promised by the `XyzDAO` interface those five
controllers actually declare their fields as. `DepartmentServlet`,
`CourseServlet`, `SectionServlet`, and `SubjectServlet` (all pre-existing,
Phase 5a/5c/5d) would not have compiled - this wasn't introduced by this
phase, this phase's own `ExamServlet` just happened to make the sixth
example and be the one actually run through a real `javac` for the first
time. Fixed with a new `SoftDeletableDAO<T extends SoftDeletableEntity, ID>
extends GenericDAO<T, ID>` interface declaring both methods;
`AbstractSoftDeletableDAO` now formally `implements` it (it already
satisfied it, just without saying so); all 8 soft-deletable entities'
DAO interfaces - the 5 with a caller today plus `UserDAO`, `ResultDAO`,
`NoticeDAO`, which don't yet but would hit the identical wall the moment
Phase 5f/7/8 gave them one - now extend `SoftDeletableDAO` instead of
`GenericDAO` directly. See "Verification" below for exactly how this was
confirmed, not just asserted.

## Files

**Added**
- `service/dto/ExamRequest.java`, `service/dto/GradingRuleRequest.java`
- `service/ExamService.java`, `service/GradingRuleService.java`
- `controller/ExamServlet.java`, `controller/GradingRuleServlet.java`
- `webapp/admin/exams.jsp`, `webapp/admin/exam-form.jsp`
- `webapp/admin/grading-rules.jsp`, `webapp/admin/grading-rule-form.jsp`
- `dao/SoftDeletableDAO.java` - see decision 11
- `docs/architecture/PHASE5E-EXAM-GRADING.md` (this file)

**Changed**
- `entity/enums/ExamStatus.java` - added `next()` and `isOpenForMarksEntry()`.
  Purely additive; no existing member touched. `isOpenForMarksEntry()` has no
  caller yet - Phase 6's marks-entry screen will be the first - but it's a
  pure function of this enum's own meaning, not a guess about Phase 6's
  implementation, so it's placed where the meaning lives.
- `assets/css/tokens.css` - relocated `.field`/`.btn-primary`/`.auth-error`/
  `.auth-success` from `auth.css` (see bug fix above); added `.field select`,
  `.field textarea`, `.field-error`, `.field-hint`, `.btn-secondary`.
- `assets/css/admin-shell.css` - added `.badge-status-*` and `.stepper`.
- `assets/css/auth.css` - removed the five relocated rule blocks, replaced
  with a comment pointing to their new home.
- `db/seed-data.sql` - two more demo exams (`CREATED`, `ACTIVE`) alongside
  the two Phase 2 already seeded (`PUBLISHED`, `SUBMISSION`), so the new
  screen has something at every stage to demonstrate.
- `dao/AbstractSoftDeletableDAO.java` - now declares `implements
  SoftDeletableDAO<T, ID>`; method bodies unchanged (it already did exactly
  this, silently).
- `dao/DepartmentDAO.java`, `dao/CourseDAO.java`, `dao/SectionDAO.java`,
  `dao/SubjectDAO.java`, `dao/ExamDAO.java`, `dao/UserDAO.java`,
  `dao/ResultDAO.java`, `dao/NoticeDAO.java` - one-line signature change
  each, `extends GenericDAO<T, Long>` to `extends SoftDeletableDAO<T,
  Long>`. No other line touched; nothing about how any of these are
  implemented changes, only what the interface now promises.
- `controller/DepartmentServlet.java`, `controller/CourseServlet.java`,
  `controller/SectionServlet.java`, `controller/SubjectServlet.java` - not
  edited at all. They already called `.findDeleted()`; the fix was entirely
  on the interface side, which is the point of decision 11.

No changes to `web.xml` (annotation-based `@WebServlet` routing, same as
every other Phase 5 controller) or `admin-head.jspf` (the `/admin/exams` and
`/admin/grading-rules` links already existed, pointing at routes that simply
didn't resolve until now).

## Verification

This sandbox has no route to Maven Central (see the project README's Setup
section, point 3 - `mvn clean compile` itself still hasn't been run against
a live repository anywhere in this project's history, and that caveat is
unchanged by this phase). It does, however, allow `archive.ubuntu.com`, so a
JDK - `javac`, specifically - was installed from Ubuntu's own package
repository, which this sandbox otherwise doesn't provide.

That made a real, scoped compilation possible: the actual source of every
file this phase touches or depends on (66 real `.java` files - all 8
soft-deletable entities, their DAOs, `ExamService`/`GradingRuleService`/
`AcademicSetupService`/`SubjectService`, and all 6 admin controllers that
call `.findDeleted()` anywhere in the codebase - `DepartmentServlet`,
`CourseServlet`, `SectionServlet`, `SubjectServlet` plus this phase's own
`ExamServlet`/`GradingRuleServlet`) compiled together against 35 hand-written
stub classes standing in for the third-party APIs Maven Central would
otherwise supply (`org.hibernate.Session`/`Transaction`/`Query` and
friends, `jakarta.persistence.*` annotations, `jakarta.servlet.*`,
`io.sentry.Sentry`, `org.apache.logging.log4j`). Each stub's method
signatures were written to match the exact calls the real code makes -
checked by grep against the actual usage, not assumed from memory - so a
mismatched method name or argument type in the real code would fail exactly
as it would against the genuine library.

That first run failed with 6 errors, all `cannot find symbol:
findDeleted()` on `ExamDAO` - which is decision 11, not a stub problem.
After the `SoftDeletableDAO` fix, a second run caught a genuine *stub* gap
(`Query.uniqueResultOptional()`, a real Hibernate 6/7-native method the
project's `AcademicYearDAO`/`CourseDAO`/`DepartmentDAO`/`SectionDAO`/
`SubjectDAO` all legitimately call, missing from the first draft of the
stub). With that added, the full 66-file set - all 6 controllers, all 8
soft-deletable DAO interfaces, every entity and service in the dependency
chain - compiles clean: exit code 0, zero errors, 104 `.class` files
produced.

**What this does and doesn't confirm.** It confirms real cross-file
consistency: this phase's code calling the real, actual method signatures
on `ExamDAO`, `GradingRuleDAO`, `Semester`, `AcademicYear`, and every other
class it touches, and - just as importantly - the fix restoring that same
consistency for four controllers this phase didn't otherwise touch. It does
not confirm the stub classes are faithful to the real library APIs in every
respect Maven Central's actual jars would enforce (an argument-order
mistake in a method neither this phase nor its dependency chain happens to
call would slip through unnoticed), and it says nothing about the JSPs -
there is no JSP compiler in this sandbox, only `javac` for `.java` sources,
so a live deploy against Tomcat is still the first real test the four new
`.jsp` files get, same caveat every earlier phase's JSPs carry.

## Deferred to later phases (not gaps in this one)

- Wiring `ExamStatus.isOpenForMarksEntry()` into an actual gate on marks
  entry - that screen doesn't exist until Phase 6.
- Anything that reads `results` to decide whether an exam or grading rule is
  "in use" - Phase 7 (Result Engine) is what populates that table for real;
  checking it now would be guessing at a shape that doesn't exist yet.
- A completeness check that grading rules for a year cover all of 0-100 with
  no gaps - relevant once `GradeUtil` (Phase 7) has to handle "no rule
  matched this percentage," not before.
