# Phase 9 — Result Versioning & Audit

Sec. 13's change-tracking mechanism and Sec. 47's "authorized correction"
path a locked (or any) result's modification must go through, plus the
"audit interface" Sec. 13 asks for. `/admin/results` - `admin-head.jspf`'s
second still-unbuilt sidebar link, sitting beside Phase 8's `/admin/approvals`
- is built.

## Starting point (verified before writing anything)

`ResultHistory`, `ResultHistoryDAO`, and `RevaluationRequest` were all
checked directly rather than assumed. All three already exist, built ahead
of need in Phase 3 exactly like `Result.markApproved`/`markPublished`/
`markLocked` were for Phase 8: `ResultHistoryDAO` already had
`findByResult(resultId)`, and `ResultHistory`'s own class Javadoc already
explains its append-only shape (`extends BaseEntity` directly, no
`updatedAt`, no setters beyond the constructor) and that its internal-marks
columns are a deliberate addition beyond the spec's literal field list.
Nothing needed adding to either. `RevaluationRequest` (Phase 10's entity,
not touched this phase) was checked only to confirm the boundary: it
already has its own `resolve(...)` method, confirming re-evaluation's
*review* workflow is Phase 10's job, while a general, admin-initiated
correction mechanism - which Phase 10 will later call as one trigger among
possibly several - is this phase's.

`Result`'s raw-marks setters (`setTheoryMarks`/`setPracticalMarks`/
`setInternalMarks`) were confirmed public, so a correction can apply new
values directly without needing a new entity method.

## Decisions

**1. `/admin/results` is this phase's, not Phase 15's - a revision of
PHASE8-APPROVAL-PUBLISHING.md's own guess.** That doc's Deferred section
read the still-unbuilt link as "closer to Sec. 33's Reporting" and pointed
to Phase 15. With Phase 9's actual scope in hand - "Result history...
Audit interface" - a results browser whose main job is exposing each row's
version history is a more precise fit here than in a reporting/export
phase. Left uncorrected in PHASE8-APPROVAL-PUBLISHING.md itself (that
document is a record of what Phase 8 knew at the time, the same way
Phase 7 corrected `seed-data.sql`'s content but not Phase 2's own decision
log); this document is where the revision belongs.

**2. `correctResult` was added to the existing `ResultService`, not a new
class.** Sec. 37 names exactly one `ResultService`; "an authorized
correction changes a result's marks with an audit trail" sits inside the
same "what's allowed to happen to a result and when" boundary
`approveResults`/`publishExam`/`lockExam` already own. Composing it
required the same treatment Phase 8 already gave
`doCalculateSubjectResult`: `doGenerateResultSummary` is now
package-private too (was private), and a new package-private
`refreshSummaryIfPublished` was added specifically for this phase's need -
see Decision 3.

**3. A correction refreshes *this* student's summary and re-ranks the
*whole* exam, but does not regenerate every other student's summary.**
Reusing `doGenerateAllSummariesAndRanks` (Phase 7/8) outright was
considered and rejected: it throws if *any* student in the exam has zero
calculated results, which would make correcting one student's result
capable of failing on an entirely unrelated student's in-progress,
not-yet-fully-entered data - a correction to A's marks has no business
being blocked by B's incomplete submission. `refreshSummaryIfPublished` is
narrower on purpose: regenerate only the corrected student's own summary
(guaranteed safe - they just got a fresh calculated `Result`, so they can
never be the "zero calculated results" case), then re-rank the whole
cohort, since rank is inherently comparative and the correction may have
moved this student past or behind others. It is also a deliberate no-op,
not an error, when no summary exists yet for this student/exam - correcting
a result before its exam has ever been published has nothing published to
keep consistent.

**4. Every correction records a complete three-component snapshot, not a
diff.** `ResultHistory`'s constructor (Phase 3) takes old/new for all of
theory, practical, and internal on every call, regardless of which
component actually changed. A correction that only touches practical marks
still writes theory's old value equal to its new value, rather than one of
six columns being null to mean "this component wasn't part of this
correction" - a fact a reader would otherwise have to infer rather than
see directly in the row. Verified against real, deliberately partial data:
correcting only CS25009's CS301 theory marks (practical left unchanged)
produced a history row reading old/new practical as identical values, not
null/null.

**5. `correctResult` works on a result in any status, not gated to
LOCKED specifically.** Sec. 47 frames "authorized correction" as the
answer to *locked* results, but nothing about the mechanism itself needs a
locked precondition - an admin correcting a DRAFT or SUBMITTED result
after, say, a student complaint arriving early still benefits from the
same audit trail a later-stage correction would get, and restricting the
method artificially would only mean re-implementing the same logic again
the day someone needs it for an unlocked result. The method changes
*what happened*, never *what stage a result is at* - `status` is untouched
by every code path in this method, verified directly (the corrected
result's `status` stayed `PUBLISHED` throughout the live test in
Verification, below).

**6. Non-negative marks are validated explicitly in `ResultService`,
not left to the database's own CHECK constraint to catch.**
`doCalculateSubjectResult`'s existing defense-in-depth check (Phase 7) only
catches a component *exceeding* its maximum; nothing before this phase
checked for a negative value below the calculation layer. Letting a
negative value reach `results.chk_results_marks_nonnegative` would still
correctly reject it, but as a raw constraint-violation exception rather
than the clear, specific message Sec. 45 asks every failure to carry -
validated explicitly here for exactly that reason.

## Files

**Added**
- `webapp/admin/results.jsp` - exam picker + every result for that exam,
  any status, with a History link per row.
- `webapp/admin/result-history.jsp` - one result's current state, its
  complete `ResultHistory` trail, and the correction form (theory/
  practical/internal inputs rendered conditionally on
  `subject.hasTheory`/`hasPractical`/`hasInternal`, matching
  `marks-entry-grid.jsp`'s own established convention exactly).
- `controller/ResultServlet.java` - `/admin/results` (browse),
  `/admin/results/history` (detail + audit trail), `/admin/results/correct`
  (the correction action). `module:result-engine`-tagged Sentry catch-all,
  matching `ResultApprovalServlet`'s own (Phase 8).

**Changed**
- `service/ResultService.java` - added `correctResult`.
- `service/ResultCalculationService.java` - `doGenerateResultSummary` is
  now package-private (was private); added `refreshSummaryIfPublished`.
- `dao/ResultDAO.java` / `ResultDAOImpl.java` - added `findByExam`
  (every result for an exam, any status - no existing method covered this;
  `findByExamAndStatus` is status-specific by design).

Nothing in `entity/`, `schema.sql`, or `seed-data.sql` needed to change -
`ResultHistory`, `ResultHistoryDAO`, and `Result`'s raw-marks setters all
already existed, built ahead of need by Phase 3.

## Verification

Same sandbox constraint as Phases 7/8 - no live compile; every symbol the
new files reference was cross-checked against real source, and brace/tag
balance checked programmatically on every new/changed file.

Beyond that, a full live run against an isolated scratch copy of this
repo's own `schema.sql` + `seed-data.sql` (dropped after), deliberately
choosing an edge case rather than a clean "everything improves" scenario:
correcting the intentionally-struggling seeded student's (CS25009) CS301
theory marks from 27.71 to 45.00 while leaving practical unchanged at
10.05, on a result that was already PUBLISHED with an existing
`result_summaries` row.

- **Compartmental pass/fail held correctly through the correction**:
  theory alone now clears CS301's passing mark (45.00 &ge; 28.00), and the
  blended grade moved from F to C - but practical (10.05) is still below
  its own passing mark (12.00), so `is_pass` correctly stayed `false`. This
  is exactly Phase 7 Decision 4's claim - pass/fail tracks each component's
  own threshold independently of the letter grade - being exercised by a
  real correction rather than just original entry, and it held.
- **The history row correctly recorded old=new for the unchanged
  component** (practical 10.05 &rarr; 10.05), not a null standing in for
  "nothing happened here" - confirming Decision 4 above against real data,
  not just the Java source.
- **`refreshSummaryIfPublished`'s cascade worked end to end**: CS25009's
  overall percentage moved from 36.44% to 39.90% (still F-band overall -
  only one of five subjects improved), overall `is_pass` correctly stayed
  `false` (still failing at least one other subject), and re-ranking the
  whole exam left every *other* student's rank exactly where it was
  (nobody else's data changed) while confirming the query re-derives every
  rank from scratch rather than patching one entry - CS25009 stayed 10th,
  correctly, since 39.90% still wasn't enough to pass CS25010's 58.95%.
- `result.status` was confirmed unchanged (`PUBLISHED`, before and after)
  - the correction changed what happened, not what stage the result is at,
  exactly as Decision 5 claims.

## Deferred beyond this phase

- **Re-evaluation's own request/review workflow** (Sec. 14) - Phase 10.
  `RevaluationRequest`/`RevaluationDAO` were confirmed to already exist
  (Phase 3) but were not touched. When Phase 10 resolves an approved
  request by changing marks, it calls this phase's `ResultService.
  correctResult` rather than reimplementing "apply new marks + write
  history" itself - one mechanism, two triggers (a direct admin action
  here, an approved student request there).
- **Filtering the audit interface** beyond "one exam at a time" (e.g. by
  student, by date range, by who made the change) - Sec. 67's own filter
  list doesn't name result-history specifically, and nothing in this
  phase's seed data yet has enough correction volume to make the case for
  building it now rather than when a later phase's reporting needs it.
