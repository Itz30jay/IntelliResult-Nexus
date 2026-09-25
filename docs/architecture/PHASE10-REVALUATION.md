# Phase 10 — Re-evaluation

Sec. 14's full loop: a student selects an eligible result, gives a reason,
and submits; an administrator reviews it and resolves it, optionally
correcting marks. The first phase with any student-facing page at all -
Phases 5-9 were entirely Admin/Teacher - so this phase also bootstraps the
minimum a student needs to reach `/student/revaluation` in the first place.

## Starting point (verified before writing anything)

`RevaluationRequest`/`RevaluationDAO` were checked directly: both already
exist (Phase 3), with `findByStudent`/`findByStatus` and a `resolve(outcome,
adminRemark, resolvedBy)` method already on the entity. Unused until now,
same as `ResultHistory`/`ResultHistoryDAO` were for Phase 9.

`seed-data.sql` turned out to already contain a PENDING request - CS25009
against their CS301 Mid Semester result, reason text about a specific
question - which nothing before this phase ever mentioned or used. Read
together with `revaluation_requests`' own presence in the schema since
Phase 2, this is the same "seeded ahead of the phase that consumes it"
pattern as the Mid Semester Examination's own full result set was for
Phase 7/8: a demo scenario waiting, not a coincidence.

`webapp/student/` was confirmed to contain nothing but a `.gitkeep` -
genuinely empty, not "unused but present" the way `/admin/results` or
`/teacher/revaluations` turned out to be in earlier phases.
`NavigationUtil.dashboardPathFor(STUDENT)` already resolves to
`/student/dashboard` (Phase 1) and `AuthorizationFilter` already restricts
`/student/*` to the STUDENT role (Phase 4) - both correct and both leading
nowhere until this phase.

`admin-head.jspf`'s Re-evaluations link (`/admin/revaluations`) and
`teacher-head.jspf`'s Re-evaluations link (`/teacher/revaluations`) were
both checked. Only the first is built this phase - see Decision 4.

## Decisions

**1. `RevaluationService`, exactly as Sec. 37 names it, composing
Phase 9's correction mechanism rather than reimplementing it.**
`resolveRequest`'s marks-correction path calls straight through to
{@code ResultService.doCorrectResult} - which required the same
private-to-package-private extraction Phase 8 and Phase 9 each already did
once (`ResultCalculationService.doCalculateSubjectResult`/
`doGenerateAllSummariesAndRanks`, `ResultCalculationService.
doGenerateResultSummary`). This is the third time this project has drawn
that exact line, which says less about any one phase and more about the
shape of the codebase at this point: a small number of "real logic" methods
that several higher-level workflows all need to compose atomically, each
with its own separately-transactional public wrapper for standalone use.

**2. "If approved and marks change" (Sec. 14, verbatim) is read literally -
approval alone never touches a result or writes history.** `resolveRequest`
compares each of the admin's proposed theory/practical/internal values
against the result's *current* values (via `BigDecimal.compareTo`, not
`.equals()` - a form re-submitting "45.0" against a stored "45.00" must not
register as a change purely from scale) and only calls
`doCorrectResult` when at least one genuinely differs. An admin can approve
a request while affirming the original marks were correct, and nothing
changes on the `Result` or in `result_history` - verified live: resolving
CS25005's request with every field left equal to the result's current
value produced zero new `result_history` rows, while resolving CS25009's
request with a genuinely different theory value produced exactly one.

**3. The resolution form always shows marks fields, pre-filled with the
result's current values, regardless of outcome - the comparison in
Decision 2 is what makes that safe.** An earlier design considered a
checkbox or separate "approve with correction" vs. "approve without"
buttons to make the admin's intent explicit; rejected in favor of always
showing real, current numbers and letting *changing* one of them be the
signal, which needs no extra UI state and cannot be forgotten or
mis-clicked into the wrong branch.

**4. Only the student and admin sides are built. Sec. 31's teacher
"respond" capability - and the `/teacher/revaluations` link already sitting
in `teacher-head.jspf` since Phase 6a - are left exactly as found.**
Phase 10's own description (Sec. 72) is explicit: "Student request, Admin
review, Approval/rejection, Marks update, Automatic history creation" -
no teacher step. Sec. 31 itself describes something `RevaluationRequest`
cannot currently record at all: "provide academic response, recommend
action" implies a teacher-response field this entity does not have, and
adding one speculatively - a schema change no phase's explicit description
has asked for yet - is a bigger, different decision than this phase's own
scope commits to. `/teacher/revaluations` stays honestly unbuilt for one
more phase, the same "not built yet, not a bug" reading `NavigationUtil`'s
own comment already established for exactly this situation.

**5. Eligibility is PUBLISHED or LOCKED, not PUBLISHED alone - a new
`ResultDAO.findVisibleToStudent`, not a widened `findPublishedByStudent`.**
Sec. 10 frames "authorized correction/re-evaluation" as precisely the
channel a *locked* result's modification has to go through; if locked
results were excluded from what a student can even select, that channel
would have no way to ever be reached for them. `findPublishedByStudent`
(unused since Phase 3, PUBLISHED-only) was left exactly as declared rather
than broadened - its narrower contract may still matter to whatever
eventually calls it (Phase 11, most plausibly), and silently changing what
an unwritten future caller would get isn't this phase's call to make.

**6. A crafted `resultId` for someone else's result reads as "not found,"
not "forbidden."** `submitRequest` checks result ownership after the
initial lookup and throws the identical `ResourceNotFoundException` either
way - the same reasoning `ResourceNotFoundException`'s own Javadoc already
established for a soft-deleted row: a result belonging to a different
student must behave as not-found to this student, not confirm to a
probing request that some other student's result with that id exists at
all.

**7. The student area is deliberately minimal - a dashboard with two
counts, one combined re-evaluation page - not a preview of Phase 11's
dashboard or Sec. 18's full result browser.** `StudentDashboardServlet`
reads its two counts directly from the DAOs it already needs, rather than
through a new `StudentDashboardService` that would have no real job beyond
these two numbers today and would likely be half-rewritten the moment
Phase 11 actually owns this page. `student-head.jspf`'s sidebar links only
what this phase actually builds (Dashboard, Re-evaluation) - a deliberate
departure from how `admin-head.jspf`/`teacher-head.jspf` each pre-wired
their *entire* eventual nav in one early phase (5a, 6a). That choice made
sense there: Phase 5's and Phase 6a's own sibling phases (5b-5f, 6b) were
weeks of the same build away and already scoped when those links were
written. A first-ever student page has no such near-term sibling to
promise a link to yet, and a nav with two working links out of two reads
very differently from one with two out of fifteen.

## Files

**Added**
- `service/RevaluationService.java` - `submitRequest`, `resolveRequest`.
- `common/fragments/student-head.jspf` / `student-foot.jspf` - this
  project's first student-facing fragment pair, reusing `app-shell.css`
  with zero new CSS (confirmed still role-agnostic, not assumed).
- `controller/StudentDashboardServlet.java` + `webapp/student/dashboard.jsp`
  - `/student/dashboard`, minimal.
- `controller/StudentRevaluationServlet.java` +
  `webapp/student/revaluation.jsp` - `/student/revaluation`: eligible-result
  selection, request form, and the student's own request history in one
  page.
- `controller/AdminRevaluationServlet.java` +
  `webapp/admin/revaluations.jsp` (filterable list, defaults to PENDING) +
  `webapp/admin/revaluation-resolve.jsp` (one request's context + the
  resolution form) - `/admin/revaluations` and `/admin/revaluations/resolve`.

**Changed**
- `service/ResultService.java` - `correctResult`'s logic extracted into a
  package-private `doCorrectResult` (validation moved inside it so both
  callers get it uniformly), matching the pattern in Decision 1.
- `dao/ResultDAO.java` / `ResultDAOImpl.java` - added `findVisibleToStudent`
  (see Decision 5).

Nothing in `entity/`, `schema.sql`, or `seed-data.sql` needed to change -
`RevaluationRequest`/`RevaluationDAO` and the seeded PENDING request all
already existed, built ahead of need by Phase 2/3.

## Verification

Same sandbox constraint as every phase since 7 - no live compile; every
symbol referenced was cross-checked against real source (including
`RevaluationRequest.resolve`'s exact signature and every getter the new
JSPs use), and brace/tag balance checked programmatically across every new
file. No URL-pattern collision with any existing `@WebServlet` confirmed by
grep.

Beyond that, a full live run against an isolated scratch copy of this
repo's `schema.sql` + `seed-data.sql` (dropped after), deliberately built
around the distinction Decision 2 makes rather than a single happy path:

1. **Submitted** a request for a fresh, previously-untouched result
   (CS25005/CS303) and confirmed the duplicate-pending check would
   correctly fire on a second attempt (one matching PENDING row found for
   the same student+result).
2. **Resolved the seed data's own pre-existing PENDING request**
   (CS25009/CS301) as APPROVED with a genuine correction (theory 27.71
   &rarr; 45.00, the same scenario Phase 9's own verification exercised
   independently) - produced exactly one `result_history` row and updated
   the result to 55.05%/C, matching Phase 9's own result for the identical
   correction and confirming the two authorized-correction paths (direct
   admin action, re-evaluation resolution) agree.
3. **Resolved CS25005's request as APPROVED with every submitted value
   equal to the result's current value** - zero `result_history` rows
   produced, result 23 completely unchanged. This is Decision 2's central
   claim, exercised directly rather than only reasoned about.
4. **Submitted and rejected a third request** (CS25003/CS301) and
   confirmed zero `result_history` rows exist for that result - a REJECTED
   outcome never reaches the correction path regardless of what the form
   happened to submit.

## Deferred beyond this phase

- **Sec. 31's teacher-response capability** and `/teacher/revaluations` -
  see Decision 4.
- **Sec. 18's full "My Results" experience** (subject-by-subject detail,
  own SGPA/rank display) - the student area built here shows only what
  `/student/revaluation`'s own selection list needs (exam, subject,
  percentage, grade); a proper results browser is left to whichever phase
  builds the fuller student dashboard (Phase 11, most plausibly, the same
  guess PHASE9-VERSIONING.md made and got right about `/admin/results`).
- **Notifications on resolution** ("Re-evaluation status changed," Sec. 23)
  - Phase 14's job explicitly (Sec. 72). `resolveRequest` returns exactly
  the resolved `RevaluationRequest` a notification step would need.
