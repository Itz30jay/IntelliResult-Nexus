# Phase 15 — Reporting

## Starting point (verified before writing anything)

- `ResultSummaryDAO.findByExam`'s own Javadoc (Phase 7) already named
  "Sec. 33's Exam Report / Topper Report source data once Phase 15
  exists"; `ResultDAO.findByExamAndSubject`'s own Javadoc (Phase 3/6b)
  already named "Subject Analysis (Sec. 33)". Both were exactly the
  queries this phase needed, planted well in advance and unused until now
  - the same dormant-infrastructure pattern every phase from 12 onward
  has found something of.
- `admin-head.jspf` already had a `/admin/reports` sidebar link, pre-wired
  since Phase 5a alongside every other admin nav item and unbuilt until
  now - the identical situation `/admin/notifications` was in before
  Phase 14. No admin navigation change was needed.
- `ExamDAO.findByAcademicYear` already existed, giving the Academic Year
  Report its exam list with no new query.
- Both PHASE12-MARKSHEET.md and PHASE13-BULK-PROCESSING.md explicitly
  deferred work here: an admin-facing "generate any student's marksheet"
  entry point (Phase 12) and "bulk export of formatted, presentation-
  oriented reports" (Phase 13). The former is superseded by this phase's
  Class Result Report, which already gives an admin a full section's
  results in one document without needing a separate per-student flow;
  the latter is exactly what this phase is.

## Decisions

**One generic `ReportData` shape for all six report types, not six
bespoke pipelines.** Every Sec. 33 report is, underneath, a title, a
context line, a table, and a few summary statistics. `ReportService`
maps every report type into the identical `ReportData` record, which
then feeds `PDFUtil.buildTabularReport`, `ExcelUtil.writeXlsx` (already
generic over exactly this shape since Phase 13), and
`report-view.jsp`'s print layout with zero per-report-type branching in
any of the three renderers. Only `ReportService` itself knows what each
report type's columns mean.

**Grouping and deduplication throughout `ReportService` are keyed by
entity id, never by an entity's own `equals()`/`hashCode()`.** This
codebase's entities, matching typical Hibernate practice, do not appear
to override either for value semantics, and relying on that (e.g.
`Collectors.groupingBy(Result::getSubject)`) would be a real risk this
phase has no way to verify safe without a running JVM. `Map<Long,
Subject>` keyed by `getId()` is correct regardless.

**Reports render in landscape, marksheets stay portrait.** The Class
Result Report alone can have one column per subject a section sat, on
top of the summary columns - a wide table that a rotated A4 page has
room for and a portrait one does not. This only affects the new
`buildTabularReport` path; `buildMarksheet` (Phase 12) is untouched.

**No watermark on reports, unlike the marksheet's diagonal one.** A
report is an internal administrative document an admin or teacher
generates for their own use, not an official record handed to a third
party the way a marksheet is - the two are deliberately styled
differently for that reason, sharing only the institution-header
building blocks (`loadLogo`, the same font/color constants) via same-
class reuse, with `buildMarksheet`'s own call chain completely
unmodified.

**"Fail/Improvement Report" is titled "Improvement Report"
throughout.** Sec. 33's own "neutral terminology" instruction, matching
Sec. 16's identical precedent from Phase 11's subject strength/weakness
language. The privacy control Sec. 33 also asks for is enforced by this
report only ever being reachable by Admin or a Teacher scoped to their
own section (never a student), not by anonymizing names within an
internal report, which would defeat its purpose.

**Teacher access is Class Result + Subject Analysis only, scoped by the
exact same guard `MarksImportServlet` (Phase 13) already uses.**
Sec. 68's "Reports: Teacher Limited" - Topper, Improvement, Exam, and
Academic Year are cross-subject or cross-exam admin views a single
teacher's assignment doesn't naturally scope to. `TeacherReportServlet`
calls `marksEntryService.requireAssignment` before anything else, the
identical authorization this codebase already established for bulk
marks import, applied here to a different action rather than
reinvented.

**The print view derives its own PDF/Excel sibling URLs, rather than
either servlet computing them.** `report-view.jsp` is reached by both
`/admin/reports/view` and `/teacher/reports/view`, two different URL
prefixes. Swapping `/view` for `/pdf`/`/excel` in the current request's
own `servletPath` (via `fn:replace`) and reusing its own query string
gets the right sibling URL regardless of which prefix served the page,
without either servlet needing to know the other's path shape.

**One more copy-paste bug avoided before it happened.** While writing
`report-view.jsp`'s empty-cell placeholder, `<c:out value="${cell}"
default="&mdash;"/>` was the first instinct - but `<c:out>`'s own HTML
escaping would double-escape that entity's ampersand into literal
"&amp;mdash;" text, the exact bug already present in this codebase's
`student/results.jsp` (Phase 11, noted but out of scope to fix during
Phase 12's exploration). Caught here by working through what `<c:out>`
escaping actually does to its `default` value, not assumed - fixed by
using the literal Unicode em-dash character instead, which contains
nothing the escaper touches.

## Files

**New:**
- `service/ReportService.java`, `service/ReportType.java`,
  `service/dto/ReportData.java`
- `controller/ReportServlet.java` (`/admin/reports` + `/view` + `/pdf` +
  `/excel`), `controller/TeacherReportServlet.java` (`/teacher/reports/view`
  + `/pdf` + `/excel`)
- `webapp/admin/reports.jsp`, `webapp/common/report-view.jsp` (shared by
  both servlets)

**Modified (small, targeted edits only):**
- `dao/ResultSummaryDAO(Impl).java` - one new `findByExamAndSection` method
- `util/PDFUtil.java` - `buildTabularReport` and its private helpers
  appended after the existing `MarksheetPageEvents` class; nothing above
  that line touched
- `webapp/teacher/marks-entry-select.jsp` - two new report links per
  picker row, beside the existing Enter Marks / Import Excel links

**Untouched:** `pom.xml`, `web.xml`, `schema.sql`, `hibernate.cfg.xml`,
`admin-head.jspf` (the `/admin/reports` link already existed),
`ResultCalculationService`, `ResultDAO`, `ExamDAO` (all called, none
modified - every query this phase needed already existed). **Zero new
database tables, zero schema changes.**

## Verification

Manually walked (no live MySQL/Tomcat in this sandbox - the same
constraint every prior phase's Verification section has noted):

1. Admin opens `/admin/reports` (already linked from the sidebar since
   Phase 5a), picks Class Result Report + an exam + a section → the form
   opens `/admin/reports/view` in a new tab → a table with one column per
   subject that section sat, plus total/%/grade/rank/PASS-or-FAIL,
   summary line with the section's pass rate.
2. From that same view, Print opens the browser's print dialog with the
   toolbar hidden (`@media print`); Download PDF and Download Excel reuse
   the identical `ReportData` already computed for the view, not a second
   independent calculation.
3. Subject Analysis for one subject shows average/highest/lowest/pass
   rate/grade distribution, all computed directly from
   `ResultDAO.findByExamAndSubject` with nothing recalculated that
   `ResultCalculationService` already owns.
4. Topper Report (top 10, passing only) and Improvement Report (below
   passing, ascending by percentage) both read correctly off the same
   `ResultSummaryDAO.findByExam`/`findByExamAndSection` data Class Result
   already uses.
5. Exam Report rolls up every subject sat in one exam; Academic Year
   Report rolls up every exam in a year that has at least one calculated
   result, silently omitting exams with none yet (Sec. 56).
6. Teacher opens `/teacher/marks-entry`, clicks "Class Report" or
   "Subject Report" on their own picker row → same print view, same PDF/
   Excel options, scoped to their own subject+section - confirmed a
   request naming a subject+section the teacher is not assigned to is
   rejected by `requireAssignment` before any report is built.

## Addendum

`report-view.jsp` as first written showed the report title/table/summary
but no institution name, logo, or generation timestamp - Sec. 34's
"Institution identity... Generation date" applies to Print as much as to
PDF/Excel, and the PDF path already had both via `PDFUtil`'s header while
the on-screen print view (what `window.print()` actually prints) did not.
Filled with a small `.institution-header` block reusing the same
`SystemSetting` the PDF path already reads, plus a `generatedAtDisplay`
attribute (both servlets, formatted via `DateUtil.formatForDisplay`
matching `VerificationServlet`'s Phase 12 precedent) rather than a raw
`LocalDateTime.toString()` in the JSP.

## Deferred beyond this phase

- **Teacher access to Topper/Improvement/Exam Summary** scoped to their
  own subject - Sec. 68 doesn't require it, and it would need its own
  scoping design (a subject-scoped topper list, say) distinct from the
  admin-level, cross-subject versions this phase built; a reasonable
  future addition, not built speculatively now.
- **CSV export** - Sec. 34 names PDF, Excel, and Print specifically;
  `ExcelUtil.writeXlsx`'s output already opens cleanly in any spreadsheet
  tool, so a dedicated CSV path would add a fourth output format nothing
  in Sec. 33/34 asks for.
- **Report result caching** - every report is recomputed on each request
  from `ResultSummary`/`Result` rows already indexed by exam/section/
  subject; at this project's scale that is fast enough that caching would
  add complexity without a measured need for it.
