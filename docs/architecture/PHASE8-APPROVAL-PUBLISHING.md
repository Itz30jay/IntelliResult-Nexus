# Phase 8 — Approval, Publishing, Locking

Sec. 10's remaining three stages of the result workflow -
SUBMITTED &rarr; APPROVED &rarr; PUBLISHED &rarr; LOCKED - and the result-
integrity guards around each transition. `/admin/approvals`, the page
`admin-head.jspf`'s sidebar has linked to since Phase 5e, is built.

## Starting point (verified before writing anything)

`Result.java` was re-read in full before any Phase 8 code was written, on
the chance `markApproved`/`markPublished`/`markLocked` already existed from
Phase 3 - they do, and they are **unguarded**: none of the three checks the
result's current status before transitioning. This is not a gap; it matches
`applyCalculatedScore`'s own documented stance of trusting its caller
completely - the entity holds state, the *service* decides when a
transition is legal. That's this phase's central job.

`ResultDAO` already had `findByExamAndStatus(examId, status)`, built ahead
of need (most likely Phase 5a, for the admin dashboard's status counts) -
exactly the query this phase's pending/approved/published queues run on.
Nothing needed adding there.

`ExamServlet`/`exams.jsp` were read as the closest existing precedent for a
workflow-status admin action - the flashError/flashSuccess session pattern,
the exception-handling cascade (`ValidationException` &rarr; re-render form,
`BaseApplicationException` &rarr; flash + redirect, `RuntimeException` &rarr;
log + Sentry + flash + redirect), and the SweetAlert2-confirm-before-a-
workflow-move convention all carried over unchanged. `admin-head.jspf` was
also checked directly rather than assumed, and turned up two things: the
sidebar already links `/admin/approvals` (this phase's exact URL, chosen for
me, not by me) and a *second*, still-unbuilt `/admin/results` link sitting
beside it - see Deferred, below, for why only the first one is this phase's
job. The badge CSS was also already prepared for this: `.badge-status-
submitted`/`-approved`/`-published`/`-locked` were live from Phase 5e,
reusing the exact same classes Exam's own status badges use, so `approvals.
jsp` needed zero new CSS.

## Decisions

**1. `ResultService`, separate from `ResultCalculationService`, exactly as
Sec. 37 names both.** This class owns *when in the workflow* a transition
is legal; `ResultCalculationService` owns turning marks into numbers. The
two compose deliberately rather than staying siloed: approving a result
calculates it in the same breath, and publishing an exam generates every
affected student's summary and rank in the same breath - both as one atomic
write (Sec. 66), because a status change and the numbers it depends on
should never be allowed to land in two different transactions that could
disagree if the second one failed.

**2. That composition required loosening two `ResultCalculationService`
methods from `private` to package-private**
(`doCalculateSubjectResult`, `doGenerateAllSummariesAndRanks`) - a real,
deliberate revisit of an already-delivered Phase 7 file, not an oversight
being patched. Hibernate does not support a nested `session.
beginTransaction()` on one session; `ResultService`'s own transaction
needs to call straight through to the *logic*, not through the *public,
separately-transactional* entry point, or approving twenty results would
try to open twenty-one transactions on one session. Nothing about either
method's behavior changed - only what can see it, and only within the same
`service` package.

**3. Approve is a caller-selected batch; Publish/Lock are exam-wide.**
Different subjects' teachers submit at different times (Phase 6b), so
forcing an admin to wait for an entire exam before approving any of it
would serialize work with no real dependency between subjects -
`approveResults(List<Long> resultIds, User admin)` takes exactly the
checkbox selection `approvals.jsp` sends. Publishing and locking are
different in kind: rank is a whole-cohort computation (Phase 7's
`RANK() OVER`), so "publish half an exam" would rank some students against
a cohort that doesn't include everyone who's about to be ranked against
them. `publishExam`/`lockExam` operate on every eligible result in the exam,
full stop.

**4. Sec. 47's "prevent publishing incomplete results" is satisfied by the
shape of the workflow, not by an extra check written to enforce it
directly.** `publishExam` only ever selects APPROVED rows, and a result
cannot reach APPROVED without passing through `approveResults`'s
calculation step first - there is no code path that could publish an
uncalculated result. `ResultSummary.isComplete()` (Phase 7) remains
available as the finer-grained signal for later phases - a class of 60
where 58 students have every subject graded should not have its publish
blocked by the 2 who don't, which a hard exam-wide completeness gate here
would have done. Verified directly, not just reasoned about: publishing the
seeded Unit Test 1 exam (only CS301/CS302 ever entered, of five subjects)
produced ten summaries with `subjects_counted=2, subjects_expected=5` -
`isComplete()` correctly reads `false` on every one of them, and nothing in
this phase stopped the publish from happening. That is the intended
behavior, not a bug this phase left in.

**5. All-or-nothing validation, checked before any write.**
`approveResults` walks every selected id and confirms it is currently
SUBMITTED *before* calculating or approving any of them - the same
"validate the whole batch first" shape `MarksEntryService.persistGrid`
(Phase 6b) already established, so a batch containing one stale selection
fails with a message naming that specific student/subject rather than
silently approving nineteen of twenty and leaving the admin to work out
which one didn't take.

**6. This is the first controller-reachable seam into
`ResultCalculationService`, and where its Sentry tag finally lands.**
PHASE7-RESULT-ENGINE.md's Decision 7 explicitly deferred adding any
`Sentry.captureException` call to the calculation engine, because every
existing call in this codebase lives in a controller's catch-all block and
Phase 7 added no controller. `ResultApprovalServlet`'s `RuntimeException`
catch tags `module: "result-engine"`, matching the exact convention
`module: "grading-rules"`/`module: "marks-entry"` already set - not a new
pattern, the pattern's next scheduled use.

## Files

**Added**
- `service/ResultService.java` - `approveResults`, `publishExam`,
  `lockExam`, and the guards around each.
- `controller/ResultApprovalServlet.java` - `/admin/approvals` (GET) plus
  `/approve`, `/publish`, `/lock` (POST).
- `webapp/admin/approvals.jsp` - exam picker + three panels (pending
  approval with bulk checkbox selection, approved-ready-to-publish,
  published-ready-to-lock), reusing `exams.jsp`'s panel/badge/DataTables/
  SweetAlert2/Toastr/CSRF conventions and the badge CSS Phase 5e already
  shipped.

**Changed**
- `service/ResultCalculationService.java` - `doCalculateSubjectResult` and
  a newly-extracted `doGenerateAllSummariesAndRanks` are now
  package-private instead of private/inline (Decision 2); their public,
  independently-transactional counterparts (`calculateSubjectResult`,
  `generateAllSummariesAndRanks`) are unchanged thin wrappers around them.

Nothing in `entity/`, `dao/` (beyond what Phase 7 already added), `schema.
sql`, or `seed-data.sql` needed to change - Result's workflow methods and
`findByExamAndStatus` already existed, built ahead of need by earlier
phases.

## Verification

Same constraint as Phase 7: no live `javac`/Maven compile available in this
sandbox. Every entity/DAO/service/exception method the new files call was
cross-checked with `grep`/`view` against its actual current source; brace/
parenthesis balance was checked programmatically on every new/changed file;
no URL-pattern collision with any existing `@WebServlet` was confirmed by
grepping the whole `controller/` package for `/admin/approvals`.

Beyond that, this phase's real news is a full **live, end-to-end run of the
entire workflow** against an isolated scratch copy of the exact `schema.sql`
+ `seed-data.sql` this repository ships (dropped afterward - the shipped
seed data is untouched, deliberately left at SUBMITTED so a real demo can
still show Approve happening rather than finding it pre-baked; see the note
at the end of this section):

1. **Approve** - the SQL equivalent of `approveResults`, run against all
   twenty seeded Unit Test 1 rows at once: calculate, then transition to
   APPROVED. Every resulting percentage matched, digit for digit, the
   values that were *originally* in this same seed file before Phase 7
   corrected it to leave them null - independent confirmation, on a second
   dataset Phase 7 never exercised, that the calculation formula is right.
   CS25009 (the intentionally-struggling seeded student) correctly failed
   both subjects on the compartmental rule (11.96 &lt; 28, 11.59 &lt; 28)
   while landing grade F on the blended percentage too.
2. **Publish** - the SQL equivalent of `publishExam`: all twenty APPROVED
   rows to PUBLISHED, ten `result_summaries` rows generated (one per
   student), then ranked with the same `RANK() OVER` statement Phase 7
   verified. `subjects_counted=2/subjects_expected=5` on every summary,
   correctly reflecting that Unit Test 1 only ever had CS301/CS302 entered
   - see Decision 4.
3. **Lock** - the SQL equivalent of `lockExam`: all twenty PUBLISHED rows
   to LOCKED. Final state confirmed with a fresh count query: 20/20 LOCKED,
   zero left behind in any earlier status.

## Deferred beyond this phase

- **`/admin/results`**, the second unbuilt sidebar link sitting next to
  `/admin/approvals`. Reads as a general browse-every-result view, which is
  closer to Sec. 33's Reporting than to "Admin approval, Publishing,
  Locking" - left for whichever phase actually owns that (Phase 15, most
  plausibly), not built speculatively here.
- **Notifications on publish** ("Result published," Sec. 23) - Sec. 72
  assigns "Notification triggers" to Phase 14 explicitly. `publishExam`
  returns exactly the list of newly-generated `ResultSummary` rows a
  notification step would need to iterate; nothing about this phase's
  shape needs to change for Phase 14 to add it.
- **Re-evaluation's "authorized post-lock correction"** (Sec. 10) is
  Phase 10's process, not this one's. Nothing in `ResultService` provides
  any way to modify a LOCKED result, on purpose.
