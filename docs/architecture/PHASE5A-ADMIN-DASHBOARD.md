# Phase 5a — Admin Shell + Dashboard: Architecture Decisions

## 0. Why Phase 5 is being delivered in sub-parts

The master spec's Phase 5 covers Admin Dashboard, User Management, Academic
Setup (6 entities), Teacher Assignments, Exam Management, Grading Rules,
Notices, and System Settings - each needing its own Service/Controller/JSP
layer. Building all of it in one pass would mean materially shallower work
on each piece than Phases 1-4 got: less real design thought per page, and
weaker verification (the EL/record resolver issue caught below is exactly
the kind of thing that gets missed at higher volume with less scrutiny per
file). This delivery is the shell and dashboard - the two things every
other Phase 5 page depends on. User management, academic setup, exams,
grading rules, notices, and settings follow as subsequent deliveries.

## 1. A verification catch worth calling out: EL record property access

`DashboardStats` is a Java 17 `record`. Record accessors are named exactly
like the component (`totalStudents()`), not `getTotalStudents()` - which
means classic JSP EL property resolution (`${stats.totalStudents}`, which
traditionally looks for a JavaBean-style getter) had a real chance of
silently resolving to nothing across the entire dashboard. Checked rather
than assumed: Jakarta EL 6.0 specifically added a `RecordELResolver` for
this exact case, and Tomcat 11 (chosen in Phase 1 for "longest runway,"
not for this reason) implements EL 6.0 - confirmed against Tomcat's own
version-support documentation, not inferred. Had Phase 1 chosen Tomcat 10.1
(EL 5.0, no record resolver) instead, every stat on this page would have
quietly rendered blank. Recording this because it's a real example of a
technology choice made for one reason turning out to be load-bearing for an
unrelated later feature - exactly the kind of thing worth verifying rather
than assuming stays true across the stack.

## 2. Static `<%@ include %>` fragments, not `<jsp:include>`

`admin-head.jspf` / `admin-foot.jspf` are combined into each admin page at
*compile* time (`<%@ include %>`), not included dynamically per-request
(`<jsp:include>`). The shell needs to read the including page's own request
attributes (`pageTitle`, `pageSubtitle`) and reference `currentPath` for
nav highlighting - a static include shares the same `PageContext` directly;
a dynamic include would sandbox the fragment into its own request scope,
making that handoff awkward. The trade-off is the classic one for static
includes (each including page's compiled servlet is slightly larger since
the fragment is copied in, not referenced) - irrelevant at this page count.

## 3. Dashboard chart data: server-rendered JSON, not an AJAX endpoint

Chart.js needs its datasets as JSON. Rather than standing up a JSON API
endpoint for two chart datasets, `AdminDashboardServlet` serializes them
with Jackson (already a `pom.xml` dependency since Phase 1) directly into
request attributes, which the JSP drops into an inline `<script>` tag. A
real JSON API layer is worth building once more than one page needs
AJAX-style data (DataTables server-side processing, likely, once user
lists grow large) - not speculatively for two arrays on one page.

## 4. Aggregate queries live on the DAOs that own the data, not a new "reporting" DAO

`ResultDAO` gained `countByStatus`, `countPublishedByPass`,
`subjectPerformance()`, `gradeDistribution()`, `topPerformers()` rather than
routing dashboard queries through a separate reporting/analytics DAO. Each
of these is fundamentally a query *about* Result rows - GROUP BY,
aggregate functions, filtered counts - so it belongs with the DAO that
already owns `Result` HQL, not duplicated access logic in a new class. The
three read-model DTOs (`SubjectPerformanceDTO`, `GradeDistributionDTO`,
`StudentPerformanceDTO`) live in a new `dao.dto` package since they're
projections, not entities - built directly by HQL `SELECT NEW` constructor
expressions so a dashboard "average per subject" query never has to load
full `Result`/`Subject` entity graphs just to compute a number (Sec. 49).

## 5. `count()` added to the generic DAO layer, not one-off per entity

Rather than adding a bespoke count method to just the entities the
dashboard needs today, `GenericDAO.count()` was added once, with
`AbstractSoftDeletableDAO` overriding it to exclude soft-deleted rows -
matching how `findAll()` already works there. This is infrastructure every
future phase's "how many X" question benefits from, not just this
dashboard's stat cards.

## 6. Sidebar shows the full Sec. 69 nav now; unbuilt pages 404 honestly

Every nav item from the spec's admin navigation list is a real link today,
grouped into six sections (People / Academic Setup / Examinations / Reports
/ Communication / System) rather than a flat 18-item list. Only Dashboard
resolves right now; everything else 404s via the existing Phase 1 error
page - the same honest "not built yet, not broken" pattern already used for
the post-login role-based redirect and the access-denied page's "go to my
dashboard" link, rather than disabled-looking nav items that would need
active bookkeeping to re-enable across every future phase.

## 7. Empty states for exam-comparison and student-trend charts: not built yet, on purpose

The spec's dashboard section also asks for "Exam-wise performance" and
"Student performance trends" comparisons. With one exam fully published in
the seed data, a comparison chart would have exactly one data point -
either misleading or requiring fabricated data to look populated. Neither
is acceptable (Sec. 54: "no fake intelligence," Sec. 56: meaningful empty
states instead of broken charts). These two are deferred until Phase 6+
seeds a second published exam, at which point they render real
comparisons instead of a chart with nothing to compare.

---

## Verification

- **javac, uncapped**: 713 errors across all 102 project files, all still
  tracing to the same missing-dependency root cause - including one new
  import prefix this phase surfaced (`com.fasterxml.jackson.databind`,
  from `AdminDashboardServlet`'s Jackson usage), confirmed as the same
  cascading symptom as every `jakarta`/`org`/`io` case before it. The three
  new DTOs and `DashboardService` itself compile with **zero** errors of
  any kind - they depend on nothing outside the project's own source plus
  the JDK, so there was nothing for a missing classpath to break.
- **EL record-resolver compatibility**: verified against Tomcat's own
  documentation rather than assumed (detailed above).
- **JSTL/HTML tag balance**: a purpose-built script checking every tracked
  tag's open/close count. First pass reported 11 "issues," all of which
  were false positives from the script's own limitations (self-closing
  tags mis-detected, and `admin-head.jspf`/`admin-foot.jspf` checked in
  isolation when they're designed to only balance once combined). Fixed
  the script and re-checked the fragments as the single combined document
  they actually render as: every tag balances exactly.
