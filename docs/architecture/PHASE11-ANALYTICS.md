# Phase 11 — Comparative Analytics

Sec. 17's Student Performance Dashboard, Sec. 15's Comparative Performance
Analytics, and Sec. 16's Subject Strength &amp; Weakness Analysis. The
student area Phase 10 deliberately left bare gets filled in - not rebuilt.

## Starting point (verified before writing anything)

Nine of `ResultSummary`'s columns already held exactly what Sec. 17 asks
a dashboard to display - overall percentage, SGPA, CGPA, grade, pass/fail,
class rank, overall rank, previous-exam comparison - all computed by Phase
7, all sitting unused on the student side until now. This phase's real
work was never "calculate these numbers"; it was building the aggregate
*comparison* queries (class average, topper, per-subject average) that
never existed, and the cross-exam *trend* analysis (Sec. 16) that requires
walking a student's own history rather than reading one already-stored
column.

`StudentDashboardServlet`'s Phase 10 form was re-read before being
rewritten - its own class Javadoc said plainly "Sec. 17's full dashboard is
explicitly Phase 11's," which is what made rewriting rather than extending
it the obvious choice, not a judgment call made fresh here.

`app-shell.css` was confirmed to already define `.stat-grid`, `.panel-grid`,
and `.value.ok`/`.value.warn`/`.value.accent` - Chart.js itself was already
linked in `student-foot.jspf` (Phase 10, unused until now, part of the same
CDN roster admin/teacher pages carry). Zero new CSS needed for this entire
phase.

## Decisions

**1. One `StudentAnalyticsService`, not four.** Sec. 15/16/17 read as a
single connected capability - "how am I doing" - once the underlying data
is in hand, not four unrelated features. `performanceTrend`/`classAverage`/
`topperScore`/`subjectComparison`/`subjectStrengthWeakness` all live on one
class, matching how `DashboardService` already covers several related
admin-dashboard aggregates rather than being split per widget.

**2. Comparison pages are consolidated too - `/student/analysis` covers
Sec. 15 and Sec. 16 together, not the up-to-four separate pages Sec. 69's
nav names** ("Performance Analytics", "Subject Analysis", "Comparison").
The two sections' content overlaps enough in practice - both are "how does
my performance break down" - that splitting them into separate pages would
mean re-fetching the same exam context (picker, section) two or three
times for what a user experiences as one question. `/student/results`
(Sec. 18) stays a separate page deliberately - it is a genuinely different
task (browsing raw results) from analyzing them.

**3. Class/topper/subject comparisons are read-only aggregate queries -
`ResultSummaryDAO.classAveragePercentage`/`topperPercentage` and
`ResultDAO.subjectAveragesForExamSection` each return a single number (or
one row per subject), never a per-student list.** This is Sec. 15's "never
expose sensitive individual student information... use aggregate
statistics" enforced structurally, not by a rule `StudentAnalyticsService`
has to remember to follow - there is no code path in any of these three
methods that could return another student's row, because none of them
select student-level rows to begin with.

**4. `SubjectAverageDTO`/`SubjectComparison` split the "class average" and
"my own score" into two separate lookups joined in Java, not one
correlated-subquery HQL statement.** A single query computing both per
subject in one pass was considered; a correlated subquery inside a
`SELECT NEW` constructor expression is exactly the kind of HQL edge case
this project has no way to verify without a live compile (Phase 7 onward's
running constraint), so the safer, more debuggable two-query-then-join
shape was chosen instead - `ResultDAO.subjectAveragesForExamSection`
(one `AVG()` per subject, section-wide) and the student's own already-
existing `findByStudentAndExam`, combined by subject id in
`StudentAnalyticsService.subjectComparison`.

**5. Subject strength/weakness is computed in Java from full `Result` rows,
not a pre-aggregated query - the grouping and trend arithmetic are
themselves the calculation logic Sec. 37 keeps out of the DAO layer.**
`ResultDAO.findCalculatedByStudent` does the one thing a query is suited
for (fetch every calculated result, ordered by subject then exam date);
`StudentAnalyticsService.subjectStrengthWeakness` groups them per subject
and computes, for any subject with 2+ results, a trend (most recent result
vs. the average of every earlier one, via `GradeUtil.percentageChange` -
the same utility Phase 7 built and this phase's own average() helper reuse
rather than re-deriving). A subject with only one result gets a null trend
- Sec. 56's empty state, not a fabricated zero.

**6. The strengths/improvement-areas split (front N / back N of the
sorted list) lives in the controller, not the service - and is written to
never let one subject land in both lists.** Deciding *what the numbers
are* is analytical work (the service's job); deciding *how many to show
as which label* is a display concern. `StudentAnalysisServlet.
applyStrengthWeaknessSplit` clamps `improvementStart` to never fall below
`strengthCount`, specifically because a student with very few subjects
(early in a semester, most plausibly) would otherwise see the same subject
labeled a strength and a weakness at once - verified directly against
CS25001's real five-subject data (Decision-by-decision numbers in
Verification, below) rather than only reasoned through.

## Files

**Added**
- `service/StudentAnalyticsService.java` - the five analytics methods
  above, entirely read-only (no `inTransaction`, matching
  `DashboardService`'s own precedent for a pure-read service).
- `service/dto/SubjectInsight.java`, `service/dto/SubjectComparison.java`,
  `dao/dto/SubjectAverageDTO.java` - the three new projection/computation
  carriers Decisions 4/5 describe.
- `controller/StudentResultServlet.java` + `webapp/student/results.jsp` -
  `/student/results`, Sec. 18's full result module.
- `controller/StudentAnalysisServlet.java` + `webapp/student/analysis.jsp`
  - `/student/analysis`, Sec. 15 + Sec. 16 combined (Decision 2).

**Changed**
- `controller/StudentDashboardServlet.java` + `webapp/student/dashboard.jsp`
  - rewritten in full per Decision (starting point) above: current
  percentage/SGPA/CGPA/rank, best subject, improvement area, a trend line
  chart, and a grade-distribution doughnut chart for the latest exam.
  Class/topper/subject comparison charts deliberately stay off this page -
  `/student/analysis` is where those live, not duplicated here.
- `dao/ResultSummaryDAO.java`/`Impl` - added `findAllForStudent`,
  `classAveragePercentage`, `topperPercentage`.
- `dao/ResultDAO.java`/`Impl` - added `subjectAveragesForExamSection`,
  `findCalculatedByStudent`.
- `common/fragments/student-head.jspf` - added My Results/Performance
  Analysis links; the Phase 10 comment explaining the "link only what's
  built" restraint was updated to note this phase following that same
  principle, not reconsidering it.

Nothing in `entity/` or `schema.sql`/`seed-data.sql` needed to change -
every number this phase displays was already a stored `ResultSummary`/
`Result` column from Phase 7.

## Verification

Same sandbox constraint as every phase since 7 - no live compile; every
symbol was cross-checked against real source, including confirming
`BaseEntity.hashCode()`'s documented constant-per-class behavior (not
id-based) doesn't break using `Exam` as a `Map`/`Set` key in
`StudentResultServlet`/`StudentAnalysisServlet` - `equals()` still compares
by id correctly, so grouping is correct even though every `Exam` shares one
hash bucket, which is a non-issue at the scale of one student's own exam
list.

Beyond that, a full live run against an isolated scratch copy of this
repo's `schema.sql` + `seed-data.sql` (dropped after), extended with the
same second-calculated-exam-per-subject scenario Phase 8/9's own
verification used (Unit Test 1 approved and published for CS301/CS302),
specifically to exercise the trend arithmetic Decision 5 depends on:

- **Hand-computed CS25001's expected `subjectStrengthWeakness` output
  before querying**, then confirmed the live data matched exactly:
  CS302 (91.91% avg, declining -2.41 from 93.11 to 90.70) ranked highest,
  then CS305 (87.23%, one result), CS303 (86.85%, one result), CS301
  (86.78% avg, *improving* +2.68 from 85.44 to 88.12 despite ranking
  below CS303/CS305 on raw average), then CS304 (84.31%, one result)
  lowest.
- **Confirmed the strengths/improvement split never overlaps**: with five
  subjects, strengths = {CS302, CS305}, improvement areas = {CS301,
  CS304}, CS303 in neither - and confirmed CS301 correctly shows *no*
  "declining" qualifier in the improvement-areas text despite appearing
  there, since its trend is actually positive; the JSP's `s.trend < 0`
  guard is what a lower relative average alone, without an actual decline,
  should read as.
- **Confirmed the class/topper comparison against CS25001's own section**:
  class average 77.08%, topper 87.44% - which is CS25001's own score,
  exercising the "you are the top scorer" branch directly rather than only
  the more common case.

## Deferred beyond this phase

- **Sec. 18's Marksheet** and the remaining Sec. 69 student nav items
  (Notifications, Notices, Profile) - later phases' explicit jobs (Phase
  12, Phase 14, and general polish respectively).
- **CGPA remains unpopulated in this seed data** for the same structural
  reason PHASE7-RESULT-ENGINE.md already recorded: it only appears on a
  FINAL_EXAMINATION summary, and this project's seed data covers one
  semester with no Final Examination published yet. `dashboard.jsp`
  already renders "&mdash;" for a null CGPA rather than a misleading zero,
  which is the correct behavior for that case whenever it does arrive, not
  something this phase needs to revisit once real Final Examination data
  exists.
