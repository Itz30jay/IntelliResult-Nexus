# Phase 6b — Marks Entry, Draft/Submit Workflow, Draft/Submitted Results, My Activity

Completes Phase 6 (Teacher), continuing from 6a's shell and awareness
views. This is the centerpiece of the Teacher module: the actual
marks-entry workflow Sec. 29 describes, plus the two result-status views
and activity log Sec. 69 lists alongside it.

## Starting point (verified before writing anything)

Re-read in full, with fresh eyes rather than relying on 6a's now-stale
context: `Result.java` (confirming `applyCalculatedScore()`'s "exclusively
Phase 7" boundary still holds - see decision 1), `results` table DDL
(confirming `chk_results_marks_nonnegative` only checks `>= 0`, never an
upper bound - a subject-specific fact no single-table CHECK constraint can
express, which is exactly why Sec. 29's "prevent marks above maximum" has
to be a Service-layer job), `result_history` table DDL (`change_reason
NOT NULL` - see decision 2), `Subject.java` (exact `isHasTheory()` /
`getTheoryMaxMarks()` triads for all three components), `Student.java`
(`getCurrentSection()`), `AuthorizationException.java`'s class Javadoc
(explicitly requires audit-trail logging - see decision 5),
`ActivityLog.java` + `ActivityLogDAO` (confirmed pre-ordered
`createdAt DESC`).

## Decisions

**1. Confirmed again, not just carried over from 6a's notes:
`Result.totalMarks`/`percentage`/`grade`/`gradePoint`/`pass` stay
untouched.** `applyCalculatedScore()` still reads "called exclusively by
ResultCalculationService (Phase 7)" in the entity's own Javadoc. Sec. 29's
"auto total calculation" is a live JavaScript running total in the grid
(`updateTotal()` in `marks-entry-grid.jsp`) - genuinely helpful on-screen
feedback, never sent to the server as a value anything trusts.

**2. Neither `saveDraft` nor `submitGrid` writes to `result_history`.**
That table's `change_reason` column is `NOT NULL` (schema.sql) - an
initial DRAFT entry, or a correction made before the teacher has ever
submitted, has no "reason" in the sense Sec. 13 means; the record doesn't
really exist in a committed sense yet. History becomes relevant once a
value that was already SUBMITTED or later needs correcting - Phase 9's
authorized-correction process, not ordinary draft editing. Multiple "Save
Draft" clicks on the same student before first submission simply overwrite
the previous draft values, no history row created.

**3. "Save Draft" and "Submit" are one form, one POST target, distinguished
by a hidden `action` field - not two separate steps requiring Save before
Submit is possible.** `submitGrid()` saves whatever was just entered, then
transitions every currently-DRAFT result among the section's students -
not just the ones touched in this specific call - to SUBMITTED, in the
same transaction. A forced two-step "you must save before you can submit"
flow invites exactly the mistake of clicking Submit having forgotten to
save moments earlier.

**4. Validation is all-or-nothing across the whole grid, checked before any
write.** Every row's theory/practical/internal values are validated first;
if anything fails (negative, over the subject's specific max, or a value
where the subject has no such component), the entire save is rejected with
a flat, human-readable error list - not a partial save that silently skips
the rows with problems, which would leave a teacher unsure what actually
persisted. Errors are keyed by student name + roll number + component
label (e.g. "Rahul Kumar (2024CS001) - Theory"), not per-field form
names, deliberately: it avoids needing a dynamic
`fieldErrors['theoryMarks_' + id]`-style bracket lookup in the JSP for
every one of up to three inputs per row (see decision 7 for the same
reasoning applied to redisplaying values). Per-cell inline error
highlighting is a reasonable Phase 16 polish item, not attempted here.

**5. `AuthorizationException` (a teacher attempting marks entry for a
subject/section they're not assigned to) is explicitly written to the
activity log, not just the Log4j2 application log.** The exception's own
class Javadoc (Phase 4) requires this: "always logged to the audit trail
... a security-relevant event worth recording." Every catch site for it in
`MarksEntryServlet` (both GET and POST) calls the same
`logUnauthorizedAttempt()` helper, which does both.

**6. Marks Entry is re-validated (assignment + exam status) on every single
request, GET and POST alike - never trusted just because the picker only
linked to combinations the teacher could see.** A URL is just a URL; a
teacher editing `sectionId` in the address bar must hit the same
`AuthorizationException` a stale or manipulated link would. This is the
actual Sec. 8 authorization ("teachers can only enter marks for subjects
assigned to them"), enforced at the point of use, not implied by what the
UI happens to link to.

**7. On `ValidationException`, the grid is re-*forwarded* to, not
redirected.** The still-live request's own POST parameters are what let
`${not empty param[theoryKey] ? param[theoryKey] : row.existingResult.
theoryMarks}` redisplay exactly what the teacher typed instead of losing
it; a redirect would issue a fresh GET with none of that. The per-student
dynamic keys (`theoryMarks_<id>`) are built via `<c:set var="theoryKey"
value="theoryMarks_${sid}"/>` - ordinary JSP attribute-value concatenation,
not EL's `+` (which is arithmetic-only and would not concatenate strings)
and not a `.concat()` method call whose argument-coercion behavior wasn't
worth staking correctness on when this simpler construct is unambiguous.

**8. A genuine bug caught and fixed while writing the JSPs, before it ever
reached `javac`: `${notice...}`-style styling aside, `\u00b7` (a Java
*source* escape) was used inside `<c:set value="...">` JSP template text,
where it is not valid syntax at all and would have rendered as six literal
characters.** JSP/EL has no unicode-escape syntax of its own for template
text; the fix was a plain ASCII hyphen, avoiding the question entirely
rather than reaching for an HTML entity instead - `&middot;` would have
been silently double-escaped by the later `<c:out>` that renders
`pageSubtitle`, appearing as literal `&middot;` text on the page. Neither
of these was caught by any compiler; both were caught by tracing through
what each layer (JSP template, EL, HTML escaping) actually does with the
value at each step, the same discipline applied to every EL-safety
decision back through Phase 5c.

**9. A second, more architecturally significant issue found while
designing "Submitted Results": `MarksEntryService.requireOpenExam()` alone
would have made that screen unreachable for its own primary purpose.**
Once an exam progresses past ACTIVE/SUBMISSION into APPROVAL or beyond,
`requireOpenExam()` correctly rejects new marks entry - but Submitted
Results needs to link *into* the grid to let a teacher review what they
already submitted, for exams at any later stage too. A single gate
couldn't serve both the write path's stricter requirement and the read
path's more permissive one. Resolved with a new `ExamStatus.
hasStartedMarksEntry()` (true from ACTIVE onward, false at CREATED/
SCHEDULED) backing a new `MarksEntryService.requireViewableExam()`, used
only by the GET/view path; `requireOpenExam()` is unchanged and still
guards every write. The grid JSP receives an explicit `examOpenForEntry`
boolean and hides the Save Draft/Submit actions entirely - and disables
every input - when it's false, rather than leaving a form visible that
would just fail if used.

**10. "Enter Marks" on the read-only Exams awareness view (6a) links to
the picker, not a specific grid.** A single exam can correspond to more
than one of a teacher's assignments (two different subjects in the same
semester, for instance) - there's no single correct grid to deep-link to
from an exam-centric list. The picker already disambiguates this cleanly
by showing every assignment x exam combination separately; re-solving that
disambiguation a second time on the exams page would be duplicated logic
for no real benefit.

**11. Draft Results, Submitted Results, and the Marks Entry picker all
share one computation - `MarksEntryService.listGroupsForTeacher()` -
filtered three different ways, rather than three separate queries.** The
picker filters to `exam.status.isOpenForMarksEntry()` (only what's
actionable right now); Draft Results filters to `draftCount > 0`;
Submitted Results filters to `submittedOrLaterCount > 0` (deliberately
*not* also filtered to open exams - see decision 9, this is a history
view). The shared method itself applies no filter and skips no empty
combinations, so each of the three screens controls its own definition of
"relevant" independently.

## Files

**Added**
- `service/dto/MarksEntryRow.java`, `service/dto/StudentMarksInput.java`, `service/dto/MarksEntryGroup.java`
- `service/MarksEntryService.java`
- `controller/MarksEntryServlet.java`, `controller/TeacherResultsServlet.java`, `controller/TeacherActivityServlet.java`
- `webapp/teacher/marks-entry-select.jsp`, `webapp/teacher/marks-entry-grid.jsp`
- `webapp/teacher/draft-results.jsp`, `webapp/teacher/submitted-results.jsp`
- `webapp/teacher/activity.jsp`
- `docs/architecture/PHASE6B-MARKS-ENTRY.md` (this file)

**Changed**
- `entity/enums/ExamStatus.java` - added `hasStartedMarksEntry()` (decision 9); purely additive, `isOpenForMarksEntry()` and `next()` from Phase 5e/6a untouched.
- `assets/css/app-shell.css` - `.badge-status-draft`/`.badge-status-submitted`/`.badge-status-approved` added to the existing three tint families (`ResultStatus` and `ExamStatus` are different enums that happen to share only two status name strings - `PUBLISHED`/`LOCKED` - so the Phase 5e badge classes alone didn't cover DRAFT/SUBMITTED/APPROVED); `.marks-input`/`.marks-row-readonly`/`.live-total` added for the grid.
- `webapp/teacher/exams.jsp` - the "Enter Marks" link promised in `PHASE6A-TEACHER-DASHBOARD.md`'s deferred-items list (decision 10).

No changes to `web.xml`, `AuthorizationFilter.java`, or any Admin file.

## Verification

Same `javac` methodology, extended further: staged every real file this
phase depends on, including the full `Result`/`Subject`/`Student`/
`TeacherSubject`/`Exam` dependency chain already established in 6a, plus
this phase's own new services/controllers. Full file/stub counts and any
gaps found are in this phase's commit message rather than duplicated here,
matching the practice `PHASE6A-TEACHER-DASHBOARD.md` established for the
same reason - the two shouldn't be able to drift out of sync with each
other.

Same limits as every earlier phase: confirms real cross-file Java
consistency, not that the stub classes are faithful to the genuine
third-party libraries in every respect, and says nothing about the six new
JSPs - no JSP compiler exists in this sandbox, so a live Tomcat deploy
remains the first real test of the grid's actual rendering, its
`oninput` live-total wiring, and every `<c:set>`-built dynamic key. `mvn
clean compile` itself still hasn't been run against a live Maven Central
anywhere in this project's history.

## Deferred beyond Phase 6

- Student Performance (Sec. 30) and Teacher Re-evaluation Response
  (Sec. 31) - unchanged from 6a's decision 2; still blocked on Phase 7
  (calculated data) and Phase 10 (RevaluationRequest workflow)
  respectively.
- Per-cell inline validation error highlighting on the grid (decision 4).
- Any wiring of `Result.revertToDraft()` - it exists on the entity since
  Phase 3 with no caller yet, same as `isOpenForMarksEntry()` had none
  until this phase. Nothing in Sec. 29's Save Draft/Submit flow needs it;
  whatever un-submit or admin-bounce-back scenario it was built for is a
  later phase's to define, not guessed at here.
