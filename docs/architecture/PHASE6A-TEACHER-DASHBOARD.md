# Phase 6a — Teacher Dashboard + Assigned Classes/Subjects/Exams

Begins Phase 6 (Teacher). Splits the phase the same way Phase 5 (Admin)
split into 5a-5f: this delivery is the read-only scaffolding - shell,
dashboard, awareness views. Phase 6b is the actual workflow - Marks Entry,
Draft/Submitted Results, My Activity - the more complex, higher-stakes
piece, deliberately not rushed alongside the infrastructure it depends on.

## Starting point (verified before writing anything)

Confirmed `src/main/webapp/teacher/` held only a placeholder before this
phase - no fragments, no CSS, no JSPs. Read in full: `Result.java`
(critical finding, see decision 1), `ResultStatus.java`, `Subject.java`
(per-component max/passing marks), `Student.java`, `Teacher.java`,
`TeacherDAO`, `TeacherSubject.java` + `TeacherSubjectDAO`, `ResultDAO` +
`ResultDAOImpl`, `StudentDAO`, `NoticeDAO`, `ActivityLogDAO` +
`ActivityLogDAOImpl` (confirmed `findByUser` is pre-ordered
`createAt DESC`, so capping to "recent N" in Java is safe), `AppConstants`,
`AuthorizationFilter` (confirmed `/teacher` already requires
`UserRole.TEACHER`), `admin-head.jspf` / `admin-foot.jspf` /
`admin-shell.css` (to build their Teacher counterparts), `admin/dashboard.jsp`
and `DashboardService`/`DashboardStats` (the pattern this phase's dashboard
mirrors).

## Decisions

**1. Phase 6 does not populate `Result.totalMarks`/`percentage`/`grade`/
`gradePoint`/`pass` - confirmed by the entity itself, not inferred.**
`Result.applyCalculatedScore()` is documented, in code the prior phase
wrote, as "called exclusively by ResultCalculationService (Phase 7)" and
bundles all five fields into one atomic call - there is no way to set just
`totalMarks` without also deciding percentage/grade/pass at the same time,
which is exactly the "one seam where scoring enters the entity" the
Javadoc describes. Sec. 29's "Auto total calculation" is satisfied
client-side instead: Phase 6b's marks-entry grid will show a live-updating
JavaScript total as a teacher types theory/practical/internal marks, purely
for immediate visual feedback, never written to `total_marks`. A DRAFT or
even SUBMITTED result can and will have `totalMarks = null` until Phase 7
runs - that's the correct state, not a gap, given what this entity's own
design already committed to.

**2. Sec. 30 (Student Performance) and Sec. 31 (Teacher Re-evaluation
Response) are out of scope for all of Phase 6, not just 6a - deferred to
when their real dependencies exist, not silently dropped.** Sec. 30's
"improvement trends" and "performance" language needs calculated
percentage/grade data (Phase 7) to mean anything; a raw-marks-only version
would either be misleading (implying a comparison that isn't real yet) or
trivial enough to not be worth a dedicated screen. Sec. 31 depends on
`RevaluationRequest` (Phase 10), which itself depends on published results
existing (Phase 7/8) - there's nothing to review yet. Both nav links are
pre-wired in `teacher-head.jspf` anyway (see decision 4).

**3. `admin-shell.css` renamed to `app-shell.css`; `admin-foot.jspf` was
NOT renamed or shared - two different calls, not an inconsistency.**
Checked before touching anything: every selector in the CSS file (`.shell`,
`.sidebar`, `.stat-card`, `.panel`, ...) was already role-agnostic, and
exactly one file (`admin-head.jspf`) referenced the filename - a
one-line, low-risk fix for a name that was always inaccurate once a second
role needed the same file. `admin-foot.jspf` is different: its content is
*also* role-agnostic, but it's `<%@ include %>`d directly, by literal path,
from all 26 existing admin JSPs - renaming or consolidating it would mean
touching every one of them for a pure naming nicety, real risk to
already-verified files for no functional gain. `teacher-foot.jspf` is a
separate file with (today) identical content; either can diverge later
without touching the other.

**4. `teacher-head.jspf`'s sidebar pre-wires all ten of Sec. 69's Teacher
nav items, including two (Student Performance, Re-evaluations) with no
route behind them yet.** Directly follows `admin-head.jspf`'s own
established precedent (its sidebar had `/admin/results`, `/admin/approvals`,
`/admin/revaluations` wired from early in Phase 5, well before Phase
7/8/10 existed to serve them) and `NavigationUtil`'s own documented
reasoning for why that's an honest "not built yet" rather than a bug.

**5. My Classes and My Subjects are one data fetch
(`TeacherSubjectDAO.findActiveByTeacher`), two flat, independently-sorted
DataTables - not a grouped/nested view.** JSTL has no group-by construct;
tracking "did the section change since the previous row" across a
`c:forEach` without a scriptlet isn't something core JSTL does cleanly.
At the assignment counts a real teacher has, DataTables' own column sort
already delivers the same "see everything for one class/subject together"
result a nested structure would, without inventing fragile iteration-state
tracking to get it.

**6. "Exams" (this phase, read-only awareness) intentionally has no
"Enter Marks" action link, even though Phase 6b will add exactly that.**
A sidebar nav item 404ing during active development is an understood,
documented, temporary state (decision 4). A specific in-page action button
that goes nowhere is a different, worse experience within an otherwise
fully working page. The link gets added as a small, additive change to
this already-shipped `exams.jsp` once Phase 6b's target page exists to
point it at.

**7. The "current Teacher" resolution (session `User` &rarr; `Teacher`
profile, or 404 if the row is missing) is duplicated across all three of
this phase's controllers rather than extracted into a shared helper.**
A real design decision, not an oversight: none of the existing DAO/util
patterns are an obvious home for something that needs both
`HttpServletRequest` and a DAO (`util` classes here are deliberately
framework/persistence-free, e.g. `ValidationUtil`/`DateUtil`), and no
existing controller uses a shared abstract-servlet-base pattern to extend
instead. Six near-identical lines, three times, is a smaller cost than
guessing at the wrong shared abstraction before Phase 6b's several more
Teacher controllers reveal what the repetition actually looks like at
real scale - revisited properly then, not forced now.

**8. `ResultDAO.findBySubmittedByAndStatus(userId, status)` is a new,
narrow addition - not a generic "results for teacher" query.** Directly
answers the dashboard's "submitted, awaiting approval" count and will
double as Phase 6b's Submitted Results list, both scoped by
`submittedBy`, a direct field with no attribution ambiguity. Deliberately
does not attempt a "pending/draft" equivalent: a DRAFT result has no
`submittedBy` yet, so "pending marks for me" can only be answered
correctly by cross-referencing `student.currentSection` against a specific
assignment's section - exactly the section-scoped context Phase 6b's
marks-entry screen already has by construction and a cross-assignment
dashboard aggregate doesn't.

**9. `ResultDAO.java` had never actually compiled - a real, pre-existing
Phase 3 bug, found only because this phase was the first to stage `Result`
and its DAO into an actual `javac` run.** The interface declares
`subjectPerformance()`, `gradeDistribution()`, and `topPerformers()`
returning `SubjectPerformanceDTO`/`GradeDistributionDTO`/
`StudentPerformanceDTO` - all three in the `dao.dto` sub-package - but
never imported any of them; Java does not treat a sub-package as visible
just because its name nests under the current one.
`ResultDAOImpl.java` (the implementation) and `DashboardStats.java` (which
uses these same three types as record fields) both import them correctly -
only the interface itself didn't. `DashboardService`, which calls all
three methods, has been shipping since Phase 5a without this ever being
caught, the same way `SectionDAO`/`CourseDAO`/etc.'s missing
`findDeleted()` shipped from Phase 5c through 5d before Phase 5e's
verification run happened to be the first to actually compile that
combination (see `PHASE5E-EXAM-GRADING.md` decision 11 for the closely
parallel story). Fixed with three added imports on `ResultDAO.java` only -
no method signature, no other file, touched.

## Files

**Added**
- `service/dto/TeacherDashboardStats.java`
- `service/TeacherDashboardService.java`
- `controller/TeacherDashboardServlet.java`, `controller/TeacherClassesServlet.java`, `controller/TeacherExamsServlet.java`
- `common/fragments/teacher-head.jspf`, `common/fragments/teacher-foot.jspf`
- `webapp/teacher/dashboard.jsp`, `webapp/teacher/my-classes.jsp`, `webapp/teacher/my-subjects.jsp`, `webapp/teacher/exams.jsp`
- `docs/architecture/PHASE6A-TEACHER-DASHBOARD.md` (this file)

**Changed**
- `assets/css/admin-shell.css` &rarr; renamed `assets/css/app-shell.css` (decision 3)
- `common/fragments/admin-head.jspf` - one line, the renamed stylesheet link
- `dao/ResultDAO.java`, `dao/ResultDAOImpl.java` - new `findBySubmittedByAndStatus` method (decision 8); `ResultDAO.java` additionally gets the three missing DTO imports (decision 9) - two independent fixes that happen to land in the same file.

No changes to `web.xml` (annotation-based routing, unchanged since Phase 4),
`AuthorizationFilter.java` (`/teacher` already required `UserRole.TEACHER`
from Phase 4 onward), or any of the 26 existing admin JSPs (decision 3).

## Verification

Same `javac` methodology as every Phase 5 sub-phase: the real source of
every file this phase touches or depends on, compiled against hand-written
stubs for the third-party APIs Maven Central would otherwise supply (still
unreachable from this sandbox - see the README's Setup section, point 3,
unchanged). New to this run: `Section`, `TeacherSubject`, `Result`,
`Student`, `Teacher`, `ActivityLog` and their DAOs joined the staged set
for the first time - 104 real files now (up from 81), against 39 stub
classes (up from 38: one more, `@JoinColumn.unique()`, a real JPA attribute
`Student.java`/`Teacher.java` both use for their one-to-one `user_id`
column that no earlier phase's staged files happened to need).

This run caught two things, not one - a stub gap and, more significantly,
decision 9's real pre-existing bug:
1. `Query.setMaxResults()` was typed `void` in the stub; the real
   Hibernate API returns `Query<T>` so `.setMaxResults(n).getResultList()`
   chains (used by `ActivityLogDAO.findRecent`, pre-existing since Phase 3)
   compile. Fixed to match the genuine signature.
2. `ResultDAO.java`'s three missing DTO imports (decision 9) - caught on
   the same run, both fixed, then a clean recompile: exit code 0, zero
   errors, 148 `.class` files from 143 total sources (104 real + 39 stub).

Same limits as every earlier phase's verification, stated there and
unchanged here: confirms real cross-file Java consistency, not that the
stubs are faithful to the genuine libraries in every respect, and says
nothing about the eight new JSPs - no JSP compiler exists in this sandbox.
`mvn clean compile` itself still hasn't been run against a live Maven
Central anywhere in this project's history.

## Deferred to Phase 6b

- Marks Entry (Sec. 29): select subject/section/exam &rarr; load section's
  students &rarr; enter theory/practical/internal marks &rarr; client-side
  live total &rarr; Save Draft / Submit.
- Draft Results / Submitted Results (Sec. 69): the teacher's own view of
  their in-progress and submitted work.
- My Activity (Sec. 69): `ActivityLogDAO.findByUser`, already used by this
  phase's dashboard widget, gets its own dedicated, unpaginated-for-now
  list page.
- The "Enter Marks" action link on this phase's `exams.jsp` (decision 6).

## Deferred beyond Phase 6

- Student Performance (Sec. 30) and Teacher Re-evaluation Response
  (Sec. 31) - see decision 2.
