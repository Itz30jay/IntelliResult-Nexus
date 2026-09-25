# Phase 13 — Bulk Excel Processing

## Starting point (verified before writing anything)

- `pom.xml` already declares `org.apache.poi:poi`/`poi-ooxml` 5.5.1,
  commented `Phase 13` - no dependency changes needed.
- `exception/DataImportException.java` already existed, written in Phase 1
  with a Javadoc that pre-specifies this phase's error-reporting contract:
  "Carries every row-level error found... a Service catching this always
  has an already-rolled-back transaction by the time it reaches here."
  This is the single most consequential thing found during exploration -
  see Decisions below.
- `service/dto/UserCreateRequest`/`UserCreationResult` and `UserService
  .createUser` (Phase 5b) already do single-student account creation in
  full, including the class's own Javadoc explicitly deferring "bulk
  import/export" to "Phase 13's dedicated ExcelUtil/POI infrastructure".
- `service/MarksEntryService.saveDraft`/`submitGrid` (Phase 6b) already
  validate an entire marks-entry batch before writing anything, throwing
  `ValidationException` with a field-error map on any violation - an
  independent, pre-existing all-or-nothing pattern for marks specifically,
  intentionally not replaced or duplicated by this phase.
- `application.properties`'s `security.public.paths`/filters needed no
  changes - both new upload endpoints sit under already-role-gated
  prefixes (`/admin/...`, `/teacher/...`).
- No existing multipart/file-upload handling anywhere in the codebase -
  this phase is the first to add `@MultipartConfig`.

## Decisions

**All-or-nothing per file, not partial success per row.** Sec. 21's
"Display Successful rows, Failed rows" reads, in isolation, like a
partial-success model. `DataImportException`'s own pre-existing Javadoc
says otherwise, explicitly and specifically for this phase, so it governs:
every row is validated with zero writes, and only if every row passes
does either import service actually create/save anything. A file with one
bad row imports nothing and reports every problem found in one pass,
rather than requiring fix-one/re-upload/discover-the-next cycles.

**Student import reuses `UserService.createUser`; marks import reuses
`MarksEntryService.saveDraft`/`submitGrid`.** Neither import service
duplicates account-creation or marks-persistence logic. Each does its own
*pre-validation* (checks that mirror, but do not replace, what the reused
method itself checks) so that by the time the reused method is actually
called, it is expected to succeed - the only residual risk is a genuine
concurrent write from a second admin/teacher session between this
service's read-only checks and its write, an accepted, undefended edge
case for what is in practice a single-operator bulk action.

**Marks import's own parse failures and `saveDraft`'s validation failures
are both surfaced as `DataImportException`.** These are different layers
(Excel-cell parsing here vs. Phase 6b's marks-range rules there) but the
same *kind* of failure from a caller's perspective - "here is a list of
what's wrong with this batch" - so `MarksImportService` catches Phase
6b's `ValidationException` and re-throws its field-error map as a
`DataImportException` row list. Phase 6b's own exception type and
validation logic are unmodified; this is translation at the boundary, not
a change to established behavior.

**Course/section (students) and exam/subject/section (marks) are chosen
once per upload, never read from spreadsheet columns.** Sec. 21's own
phrasing - "for selected Exam / Class / Section / Subject" - already says
this; `UserCreateRequest`'s single `courseId`/`sectionId` (not a per-row
value) confirms it for students. Course/section dropdowns are populated
exactly like `UserFormServlet.loadDropdownData` already does (`findAll()`,
unfiltered) - the same established precedent, not a new one.

**Bulk publish/lock extends `ResultApprovalServlet`, not a new servlet.**
`resultService.publishExam`/`lockExam` (Phase 8) are already
self-contained, single-exam, individually-transactional operations. Bulk
publish/lock is that same call, looped across several selected exam IDs -
belongs beside the single-exam form already calling it, not beside it as
a parallel feature. No new DAO/service method was needed: each loop
iteration's own `BusinessRuleException` (e.g. "no approved results yet")
is what tells the admin an exam was skipped and why - the existing
per-exam eligibility check, reused N times, not re-implemented as an
upfront "list eligible exams" query.

**CSRF token travels in the form's query string, not a hidden field, for
both upload forms.** `CsrfFilter` reads its token via
`request.getParameter()` from a filter that runs before the target
servlet's `@MultipartConfig` is in scope. Whether a servlet container
correctly parses multipart form fields into parameters when accessed from
a preceding filter is container-specific behavior this sandbox has no way
to verify against a running server. A query-string parameter is parsed
from the URL regardless of the body's content type, which sidesteps the
question entirely rather than depending on an unverifiable assumption
about it.

**Export shares each import's servlet, not a separate one.** Sec. 21's
"Excel export" is read as the reciprocal of "Excel import" - a
current-data extract whose first N columns are exactly what the paired
import method reads back, doubling as a template - distinct from Phase
15's polished, presentation-oriented report exports, which this phase
does not touch. `StudentImportServlet` and `MarksImportServlet` each
carry both urlPatterns for this reason, the same one-class/several-
related-actions shape `ResultApprovalServlet` already established.

**`ExcelUtil.writeXlsx` uses fixed column widths, not
`Sheet.autoSizeColumn`.** That POI method estimates width from AWT font
metrics, an environment dependency unverifiable in this sandbox; a fixed
width has none and is visually indistinguishable for the short columns
(roll numbers, names, marks) both templates use.

## Files

**New:**
- `util/ExcelUtil.java` (+ `test/.../ExcelUtilTest.java`)
- `service/StudentImportService.java`, `service/MarksImportService.java`
- `controller/StudentImportServlet.java` (`/admin/users/import`,
  `/admin/users/export`), `controller/MarksImportServlet.java`
  (`/teacher/marks-entry/import`, `/teacher/marks-entry/export`)
- `webapp/admin/student-import.jsp`, `webapp/teacher/marks-import.jsp`

**Modified (small, targeted edits only):**
- `controller/ResultApprovalServlet.java` - two new urlPatterns
  (`/admin/approvals/bulk-publish`, `/admin/approvals/bulk-lock`) and one
  new private method looping the exact publish/lock calls already there
- `webapp/admin/approvals.jsp` - a new Bulk Actions panel (exam
  checklist, two SweetAlert2-confirmed buttons) between the single-exam
  picker and the existing detailed review sections
- `webapp/admin/users.jsp` - an "Import Students" button beside the
  existing "+ Create User" one
- `webapp/teacher/marks-entry-select.jsp` - an "Import Excel" link beside
  each row's existing "Enter Marks" link
- `application.properties` - one new `import.excel.maxFileSizeBytes` key
  (Sec. 64/65), positioned before the pre-existing Phase 14 Mail section

**Untouched:** `pom.xml`, `web.xml`, every filter, every entity, `schema.sql`,
`seed-data.sql`, `UserService`, `MarksEntryService` (called, never edited),
`ResultService` (called, never edited). **Zero new database tables and
zero schema changes this phase** - bulk import/export/publish/lock all
operate entirely through existing entities and existing service methods.

## Verification

Manually walked (no live MySQL/Tomcat in this sandbox - see
PHASE12-MARKSHEET.md's own Verification section for the same constraint
and the same reasoning for why a careful trace is this phase's substitute):

1. Admin picks a Course (+ optional Section) on `/admin/users/import`,
   downloads the current roster as a template (empty header row if the
   course has no students yet), fills in new rows, re-uploads.
2. A file with one bad row (duplicate roll number, invalid email) imports
   *zero* students and lists every problem row - confirmed against
   `StudentImportService`'s validate-before-any-write loop, not assumed.
3. A clean file creates every row via the same `UserService.createUser`
   path a single manual creation already uses - same password generation,
   same activity logging, no parallel code path.
4. Teacher opens `/teacher/marks-entry`, clicks "Import Excel" on one
   exam/subject/section row, downloads the current grid (existing draft
   marks pre-filled, blank where nothing's entered yet), edits offline,
   re-uploads with or without "also submit" checked.
5. A file with an out-of-range mark saves nothing - confirmed
   `MarksEntryService.saveDraft`'s own pre-existing all-or-nothing
   validation still governs, its `ValidationException` field errors
   surfacing as this phase's row report.
6. Admin selects three exams on `/admin/approvals`'s new Bulk Actions
   panel, clicks Bulk Publish: two with approved results publish, one
   with none yet is reported skipped in the same flash message, exactly
   matching what clicking Publish on each individually would have done.

## Deferred beyond this phase

- **Reverting a race between pre-validation and actual write** in either
  import service (a second operator's concurrent write landing between
  this class's checks and its create/save calls) - accepted as
  vanishingly unlikely for a single-admin/single-teacher bulk action, not
  defended against with locking.
- **Bulk export of formatted, presentation-oriented reports** (Sec. 34) -
  Phase 15's Reporting is where PDF/Excel report exports with
  institution branding, statistics, and print layouts belong; this
  phase's exports are plain data extracts/templates, a different purpose.
- **Admin-side bulk marks import** - Sec. 68's access matrix keeps Marks
  Entry a Teacher capability (Admin "Optional"); bulk import following
  single-row entry's own authorization scope was the more consistent
  choice than adding a new admin privilege this phase didn't ask for.
- **`ExamDAO.findByStatus`-driven "only show eligible exams"** in the bulk
  publish/lock picker - the simpler "show all, let each attempt report
  its own outcome" was preferred over an upfront eligibility query for
  this phase's scope; worth revisiting if the exam list grows large
  enough that skipped-exam noise becomes a real UX cost.
