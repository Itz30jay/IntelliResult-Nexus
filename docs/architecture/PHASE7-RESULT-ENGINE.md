# Phase 7 — Result Calculation Engine

Sec. 11's "centralized `ResultCalculationService` (never duplicate calculation
logic)": the engine that turns raw marks (theory/practical/internal, sitting
on `Result` since Phase 6) into everything Sec. 11 says the system must
automatically calculate - total marks, percentage, grade, grade point, SGPA,
CGPA, pass/fail, class rank, overall rank, and improvement vs. the previous
exam. Every field this phase touches was left `NULL` on purpose by every
phase before it, waiting for exactly this.

## Starting point (verified before writing anything)

Per Sec. 73/74's discipline of understanding the existing architecture before
generating code, and rule 74's "do not silently rename classes or change
database columns" - the whole codebase was read before any Phase 7 file was:
`README.md`, `PHASE2-DATABASE.md`, `PHASE6B-MARKS-ENTRY.md`, `schema.sql`,
every entity Phase 7 touches (`Result`, `Subject`, `Exam`, `Student`,
`Semester`, `GradingRule`, `AcademicYear`), the full DAO layer
(`GenericDAO`/`AbstractDAO`/`AbstractSoftDeletableDAO`, `ResultDAO`,
`GradingRuleDAO`, `SubjectDAO`, `ExamDAO`, `StudentDAO`), the exception
hierarchy, `GradingRuleService`/`ExamService`/`MarksEntryService`,
`MarksEntryServlet`, `hibernate.cfg.xml`, `log4j2.xml`,
`ApplicationStartupListener`, `HibernateUtil`, and `seed-data.sql`.

Three findings from that read shaped this phase more than anything decided
fresh:

1. **`GradeUtil` and `ResultCalculationService` were already named**, in
   other phases' own Javadoc, before either file existed.
   `GradingRuleDAO.findActiveByAcademicYear`'s Javadoc: *"the one method...
   iterating this list and calling each `GradingRule.covers(pct)` -
   deliberately not a `findGradeForPercentage(...)` DAO method, since 'which
   rule matches this percentage' is calculation logic that belongs with
   ResultCalculationService (Sec. 11), not baked into a query."*
   `GradingRuleService`'s Javadoc: *"the only query GradeUtil/
   ResultCalculationService will ever run in Phase 7."*
   `ResultProcessingException`'s Javadoc: *"Thrown by ResultCalculationService
   (Phase 7) when marks cannot be turned into a percentage/grade/GPA/rank...
   e.g. no active GradingRule covers the computed percentage, or a subject's
   component marks... don't reconcile with its configured maximum."* All
   three were written, and matched exactly, without modification.

2. **Whether rank gets a dedicated table was explicitly left for this
   phase to decide**, not guessed at earlier. `Result`'s own class Javadoc:
   *"rank is an aggregate across every subject a student took in one exam,
   not a per-subject-result fact, so it doesn't belong here."* `schema.sql`'s
   header comment on `results` goes further: *"whether it gets cached in a
   dedicated table is a decision Phase 7 is better placed to make once the
   calculation engine's real query patterns are known, not a schema guess
   made speculatively here."* See Decision 1 below for what was decided and
   why.

3. **The seeded Unit Test 1 data disagreed with the current architecture**,
   and the live database was the thing that caught it, not a re-read of the
   Java source alone. `seed-data.sql`'s Unit Test 1 rows had `total_marks`/
   `percentage` populated while `grade`/`grade_point`/`is_pass` stayed `NULL`
   - a Phase 2-era reading of "auto total calculation happens as marks are
   entered." `Result.applyCalculatedScore` (Phase 3) sets all five calculated
   fields together, as one call, and `MarksEntryService` (Phase 6b,
   confirmed by reading its current source) touches none of them - there is
   no intermediate state where two of five are populated. See Decision 5.

## Decisions

**1. New table, `result_summaries` - one row per (student, exam).**
`Result` is one subject; SGPA/overall percentage/pass-fail/rank/CGPA are
whole-exam facts about a student, needing every subject's `Result` at once.
Caching them (rather than recomputing on every dashboard/marksheet read) is
the same call already made for `subjects.total_max_marks` being a stored
generated column instead of a runtime sum (Sec. 49). Fields worth calling
out specifically:
   - `subjects_counted`/`subjects_expected` rather than a single boolean -
     `ResultSummary.isComplete()` derives the yes/no for callers that just
     need it, but Phase 8's "prevent publishing incomplete results" (Sec. 47)
     will want to say *how* incomplete, not just that it is.
   - `total_credits` is stored, not re-derived, specifically so a later
     semester's CGPA computation never re-walks an earlier exam's `Result`
     rows just to learn how many credits that semester was worth.
   - `cgpa` is `NULL` on every summary except a `FINAL_EXAMINATION` one.
     CGPA is cumulative *across semesters*; a Final Examination is the one
     exam type that represents "this semester is done" (Sec. 9's type list).
     Populating it on every Unit Test would make CGPA move with interim
     assessments the way no real transcript does.
   - `class_rank`/`overall_rank` have no Java setter on the entity and no
     row-by-row writer anywhere - see Decision 2.
   - No soft-delete triple. Everything here is derived from `results` rows
     that already carry their own audit trail (Sec. 13/48); there is nothing
     an admin "created by mistake" to restore from a recycle bin, the same
     reasoning `result_history`/`activity_logs` were built without one for.
     A wrong summary is fixed by recomputing it, not undeleting an old one.

**2. Rank is computed with one native `RANK() OVER` statement per exam, not
per-student Java sorting.** MySQL 8+ is this project's mandated minimum, and
window functions are exactly the tool for "rank every student against every
other student sitting the same exam" - a genuinely whole-cohort computation
that N individual `UPDATE`s (or loading N entities into Java to sort) would
serve far worse as the cohort grows (Sec. 49). The query was written and
tested against this project's own live, seeded Mid Semester Examination data
*before* being committed to `ResultSummaryDAOImpl` (see Verification) -
including partitioning by `students.current_section_id` for class rank
alongside a plain `ORDER BY` for overall rank, in the same statement.
Because a native bulk `UPDATE` bypasses Hibernate's persistence context by
design (confirmed against Hibernate ORM's own current documentation via
Context7, not assumed - see Verification), `ResultSummaryDAOImpl.
recalculateRanks` calls `session().clear()` immediately after, and
`ResultCalculationService.generateAllSummariesAndRanks` re-fetches summaries
from the database afterward rather than returning the (now
possibly-stale-on-rank, possibly-detached) objects touched during the
generation loop that preceded it.

**3. Percentage is computed against the sum of *entered* components' own
maximums, not `Subject.totalMaxMarks`.** This is not a simplification
invented here - it is a fact about how this project's own seed data already
behaved, discovered by re-deriving both denominators against the live
database rather than assumed from reading the Java alone: CS301's Unit Test
1 rows carry only `theory_marks`, and their seeded `percentage` matches
`theory_marks / theory_max_marks * 100` exactly (e.g. `59.81/70 = 85.44%`),
**not** `theory_marks / total_max_marks * 100` (`59.81/100 = 59.81%`), even
though `CS301.has_practical = true`. `Subject.hasTheory/hasPractical/
hasInternal` describe what a subject *can* be examined on across its whole
curriculum (a Unit Test and a Practical exam for the same subject
legitimately test different components); which of those components a given
`Result` was actually scored on is a fact about that `Result` row itself
(which of `theoryMarks`/`practicalMarks`/`internalMarks` are non-null), not
about the subject's curriculum-wide definition. `calculateSubjectResult`
reads it that way throughout, and defensively re-validates (`Result
ProcessingException`) that any entered component the subject doesn't
actually have configured, or that exceeds that component's own configured
maximum, is rejected rather than silently miscalculated - `MarksEntryService.
validateComponent` already guards the first case at entry time, but
calculation must not trust that blindly once a future correction path
(Phase 9/10) can write marks through a different route.

**4. Pass/fail is compartmental (theory AND practical AND internal must each
individually clear their own passing marks), computed independently of the
letter grade.** `subjects.theory_passing_marks`/`practical_passing_marks`/
`internal_passing_marks` exist as three separate columns (Sec. 7) - if
pass/fail were simply "is the blended percentage outside the grading
table's F band," those three columns would have no reader anywhere in the
system, which is not a defensible reading of why Phase 2 put them there.
Keeping pass/fail keyed to the subject's own configured thresholds rather
than to the (separately, dynamically admin-configurable per Sec. 12) grading
table also means changing next year's grading scale can never silently
re-fail a student whose actual component-level performance didn't change.
The two computations agree throughout this project's own seed data only
because every seeded subject happens to set every passing threshold at
exactly 40% of that component's maximum, matching the grading table's own
F/D boundary - a coincidence of the demo data, not a rule the engine relies
on anywhere.

**5. `seed-data.sql`'s Unit Test 1 block corrected, not left as-is.** See
Starting-point finding 3 above for what was wrong. Left uncorrected, the
seed data would show a `SUBMITTED`-but-unapproved result with a percentage
the real, current system could never actually produce at that stage - a
direct contradiction of Sec. 57's "realistic development/demo data." All
twenty rows now carry `total_marks`/`percentage`/`grade`/`grade_point`/
`is_pass` all `NULL`, matching every other `SUBMITTED` result already in the
file, and the block's header comment now explains the correction rather than
silently changing the values.

**6. Not wired into `MarksEntryService.submitGrid`.** This was the most
consequential option seriously considered and rejected. The case for wiring
it in: an administrator "reviews and approves" (Sec. 10) a result, which
reads as needing a computed score to review, and the original (superseded)
seed-data comment assumed calculation happened at submission. The case
against, which won: hard-coupling "can a teacher submit the marks they
finished entering" to "has an administrator already finished configuring
this academic year's grading table" would let an entirely unrelated
administrative gap block a teacher's own workflow for reasons outside their
control - and, per Sec. 66, a `calculateSubjectResult` failure partway
through a whole section's submit loop would have to roll back every other
student's submission in the same batch too. The natural trigger is Phase
8's approval action, which both scopes the failure correctly (approving one
result no longer holds an entire section's submission hostage) and matches
the exact "build the capability, wire the trigger in the phase that needs
it" discipline already used for this class itself - Phase 1 skipped
stubbing `GradeUtil`/`PDFUtil` as premature scaffolding, and Phase 6a
explicitly deferred Student Performance / Teacher Re-evaluation Response as
"blocked on Phase 7." Nothing in `MarksEntryService` or `MarksEntryServlet`
was changed this phase.

**7. No Sentry/Log4j2 calls added to `ResultCalculationService`/`GradeUtil`.**
Checked empirically rather than assumed: `grep -rn "Sentry" .` and a
`LogManager`-usage grep across every existing `service/`/`dao/` class both
came back with zero matches outside `controller/` - every `Sentry.
captureException` and `LOGGER.error` call in this codebase today lives in a
Servlet's catch-all block, matching `LoginServlet`'s own comment that manual
capture is "reserved for unexpected exceptions" at the request boundary.
Phase 7 adds no new controller, so there is no new request boundary to
attach one to. This is not a gap - the Sentry Log4j2 appender (`log4j2.xml`)
still auto-captures anything any layer logs at ERROR+, and
`ResultCalculationService` simply has nothing to log yet, because it throws
`ResultProcessingException` (a typed, business-meaning exception) on every
failure path rather than logging-then-rethrowing. The first controller that
calls into this engine - almost certainly Phase 8's approval action - is
where a `module:result-engine`-tagged catch-all belongs, the same way
`module:grading-rules` and `module:marks-entry` already exist for theirs.

## Files

**Added**
- `entity/ResultSummary.java` - the new per-(student, exam) aggregate entity.
- `dao/ResultSummaryDAO.java` / `ResultSummaryDAOImpl.java` - lookups plus
  the native bulk rank-recalculation query.
- `util/GradeUtil.java` - pure percentage/grade/weighted-average arithmetic,
  zero DB access, matching `DateUtil`/`ValidationUtil`'s own shape.
- `service/ResultCalculationService.java` - the orchestrator: subject-level
  `calculateSubjectResult`, exam-level `generateResultSummary`, bulk
  `recalculateRanksForExam` / `generateAllSummariesAndRanks`.
- `src/test/java/.../util/GradeUtilTest.java` - pure unit tests, no
  mocking needed, several asserting against numbers re-derived from this
  project's own live seeded database rather than invented.

**Changed**
- `dao/ResultDAO.java` / `ResultDAOImpl.java` - added
  `findDistinctStudentIdsByExam`, the iteration list
  `generateAllSummariesAndRanks` needs; nothing existing removed or renamed.
- `hibernate.cfg.xml` - one new `<mapping>` line for `ResultSummary`,
  grouped immediately after `Result`.
- `db/schema.sql` - appended `result_summaries` (`CREATE TABLE` + one
  index), after `system_settings`, matching the file's established
  append-in-phase-order convention.
- `db/seed-data.sql` - Unit Test 1's calculated columns corrected (Decision
  5); ten `result_summaries` rows added for the Mid Semester Examination,
  the one exam with enough real, published data to summarize meaningfully.

Nothing in `controller/`, `webapp/`, or any other previously-delivered
service was touched.

## Verification

No live Maven build was possible in this sandbox this phase either (no
network path to Maven Central, and the individual dependency jars this
project needs - Hibernate, Jakarta Persistence, etc. - are not published as
direct GitHub release assets, checked directly rather than assumed). In
place of that, three independent, narrower checks were run:

1. **A real MySQL 8.0.46 instance, installed in this sandbox for exactly
   this purpose** (matching Phase 2's own precedent). The final `schema.sql`
   and `seed-data.sql`, byte-for-byte as they ship, were loaded into a fresh
   database and confirmed to load cleanly end to end. Every number in this
   phase's design - the percentage-denominator question (Decision 3), the
   SGPA credit-weighting formula, the compartmental pass/fail rule
   (Decision 4), and the `RANK() OVER` rank query (Decision 2) - was worked
   out *against this live data first*, then written into Java, then the
   seeded `result_summaries` rows were cross-checked by re-running the exact
   query `ResultSummaryDAOImpl.recalculateRanks` issues from a clean slate
   and confirming zero mismatches. The intentionally-struggling seeded
   student (CS25009, 4 of 5 subjects failed) lands last on both class and
   overall rank, matching the exact finding `PHASE2-DATABASE.md` recorded
   when it ran a `RANK() OVER` query against the same student for a
   different purpose.
2. **Context7**, queried against Hibernate ORM's current documentation
   before `ResultSummaryDAOImpl.recalculateRanks` was written, for how a
   native bulk `UPDATE` interacts with the Hibernate persistence context -
   confirming the exact "the application is responsible for maintaining
   synchronization... `session.clear()`" guidance Decision 2 relies on,
   rather than assuming it from general JPA knowledge.
3. **Every external symbol Phase 7's Java files reference** - every entity
   getter, every DAO method signature, every exception constructor - was
   cross-checked with `grep`/`view` directly against the real, current
   source of the file that declares it (not against memory of having read
   it earlier in this session), after one transcription error earlier in
   this phase's own research (a large `bash cat` dump visually mis-rendering
   `Result` as `r` in one file) was caught by re-reading the same file with
   line-numbered `view` output. Brace/parenthesis balance was additionally
   checked programmatically across every new/changed file.

What this does *not* include: an actual `javac`/`mvn` compile, or a live
Tomcat deployment exercising these classes through a real HTTP request -
both remain, honestly, the first real test, exactly as `PHASE6B-MARKS-ENTRY.md`
already said about the code that came before this.

## Deferred beyond this phase

- **Wiring a trigger.** `calculateSubjectResult`/`generateResultSummary`/
  `generateAllSummariesAndRanks` are complete and independently callable,
  but nothing calls them yet - see Decision 6. Phase 8 is where an
  administrator's approve/publish action calls into this engine.
- **`ResultSummary.isComplete()` gating a workflow rule.** This phase
  reports completeness; Phase 8 is where "prevent publishing incomplete
  results" (Sec. 47) reads that report and acts on it.
- **CGPA has no seed data to exercise it against.** This project's seed data
  covers exactly one semester, and CGPA only ever populates on a
  `FINAL_EXAMINATION` summary with at least one earlier semester to be
  cumulative *over* - by design (Decision 1), not oversight. The formula
  was still written and reasoned through carefully (credit-weighted across
  the most recent `FINAL_EXAMINATION` summary per semester, guarding
  against a retake producing two rows for the same semester), and covered
  indirectly by `GradeUtilTest`'s `weightedAverage` tests, but a true
  multi-semester exercise of `applyCgpaIfFinalExamination` waits for seed
  data spanning more than one semester.
- **Narrative insight text** ("Performance improved by X% compared to the
  previous examination," Sec. 55) is deliberately not generated here.
  `ResultSummary` stores the numbers (`previousExam`/`previousPercentage`/
  `percentageChange`) an insight needs; turning numbers into a displayed
  sentence is a presentation concern belonging to whichever JSP/dashboard
  Phase 11 builds, not to a service that returns data.
- **The `SystemSetting` entity is not mapped in `hibernate.cfg.xml`** -
  noticed while adding `ResultSummary`'s own mapping line, not investigated
  further. Unrelated to this phase's scope (a Phase 5f concern) and no
  compile-time consequence either way (annotations resolve independently of
  the mapping list), so left exactly as found rather than fixed in passing.
