# Phase 12 — Digital Marksheet & QR Verification

## Starting point (verified before writing anything)

Before writing any code, the existing codebase was re-read rather than
assumed, per this project's own working rule (see PHASE1-FOUNDATION.md).
Several things were already in place, planted by earlier phases
specifically for this one:

- `pom.xml` already declares `com.github.librepdf:openpdf` and
  `com.google.zxing:core`/`javase`, both explicitly commented
  `Phase 12`, with the OpenPDF-over-iText-5 licensing rationale
  (AGPL vs. LGPL/MPL) already written down. No dependency changes were
  needed.
- `application.properties`'s `security.public.paths` already lists
  `/verify`, so `AuthenticationFilter` lets `/verify/*` through with no
  session at all, and `AuthorizationFilter`'s role-prefix map doesn't
  match it either. **No filter or config change was needed** for the
  public verification endpoint to work correctly.
- `SecurityUtil.generateToken()`'s own Javadoc already names this exact
  use ("QR-verification tokens... Phase 12"). Reused as-is.
- `DateUtil.FILENAME_TIMESTAMP`'s own Javadoc already names "generated
  report and marksheet filenames (Phase 12/15)". Reused as-is.
- `ResultSummary.isComplete()` (Phase 7) was confirmed to be exactly the
  signal needed to gate "may an official marksheet be issued yet" -  no
  new completeness computation was written.
- Context7 was queried for both new libraries before any PDF/QR code was
  written: OpenPDF's own current header/footer/watermark examples
  (`PdfPageEventHelper`, `PdfGState` + `showTextAligned` with a rotation
  angle for the diagonal watermark, `PdfPTable`/`PdfPCell` styling) and
  ZXing's own `QRCodeWriter`/`MatrixToImageWriter` API, rather than
  relying on possibly-stale training data for either.

## Decisions

**A new `marksheet_verifications` table, not a column on
`result_summaries`.** `ResultSummary`'s own class Javadoc is explicit
that it is a single-responsibility derived cache, regenerated wholesale
by `ResultCalculationService`, not a place for unrelated audit fields.
Verification-token tracking is a distinct concern (who requested it,
when) that would blur that boundary. A small dedicated table, added the
same way `result_summaries` itself was added in Phase 7, keeps both
tables single-purpose. `deleted`/`deleted_by`/`deleted_at` were
deliberately left off, for the identical reason `result_summaries` also
lacks them: nothing here is ever "restored", only regenerated.

**The token maps to (student, exam), not to a snapshot of the result.**
`VerificationService#verify` always re-reads the student's live `Result`
rows for that exam, rather than caching percentage/grade/status at the
moment the token was minted. This means an authorized post-publish
correction (Sec. 14's re-evaluation path) is reflected the next time
anyone scans an already-issued QR code, with nothing in this phase
needing to hook into the re-evaluation workflow at all - a marksheet
holder's QR code stays valid and accurate indefinitely rather than ever
needing to be reissued.

**"Result status" on the public page means the workflow stage
(PUBLISHED/LOCKED), not pass/fail.** Sec. 20's literal field list is
Student name / Examination / Academic year / Result status - four items,
not five, and this project's own vocabulary everywhere else already
reserves "status" for the Sec. 10 workflow enum and uses a separate
`pass`/`isPass` field for the academic outcome. `ResultStatus.mostFinal()`
(the one new method added to an existing enum, rather than duplicated
logic in two services) answers "PUBLISHED or LOCKED" from a student's set
of per-subject statuses.

**Student self-service only.** `MarksheetService.generateMarksheetPdf`
takes a plain `studentId`, so nothing about its signature prevents an
admin-facing caller later - but Phase 12 only wires up
`/student/marksheet` (+ `/download`), with `studentId` always resolved
from the session's own `Student`, never a request parameter, the same
IDOR-proof pattern `StudentResultServlet` already established. An
admin "generate any student's marksheet" screen reads as Phase 15
(Reporting) territory, not this one - Sec. 19 frames the marksheet as a
student-facing document, and adding an unrequested admin UI now would be
scope beyond what this phase asked for.

**Verification base URL is derived from the live request, not a new
config key.** A QR code is only useful if it points at whichever host
actually served the request that generated it (`localhost:8080` in dev,
the real domain in production) - a static `app.base.url` property can
silently drift out of sync with that. `MarksheetDownloadServlet` builds
`scheme://host[:port]/contextPath` from `HttpServletRequest` directly
(standard-port suppression included). A reverse proxy that doesn't
forward `X-Forwarded-*` correctly would need that handled at the
proxy/Tomcat level - noted here as a Phase 18 deployment-guide concern,
not solved in this phase (see "Deferred" below).

**PDF assembly is entity-free.** `PDFUtil` and the two new
`service/dto` records (`MarksheetData`, `MarksheetSubjectRow`) exist so
that no Hibernate entity or lazy proxy is ever touched outside
`MarksheetService`, matching every other class already in `util`
(`DateUtil`, `GradeUtil`, `ValidationUtil` are all equally
Hibernate-unaware). `PDFUtil`/`QRCodeUtil` declare the libraries' own
checked exceptions (`DocumentException`, `WriterException`, `IOException`)
rather than swallowing them; `MarksheetService` is where they're
translated into `ResultProcessingException`, matching where
`BaseApplicationException`'s own Javadoc says that translation belongs.

## Files

**New:**
- `entity/MarksheetVerification.java`, `dao/MarksheetVerificationDAO(Impl).java`
- `service/dto/MarksheetData.java`, `MarksheetSubjectRow.java`, `VerificationResult.java`
- `service/VerificationService.java`, `service/MarksheetService.java`
- `util/QRCodeUtil.java`, `util/PDFUtil.java`
- `controller/MarksheetServlet.java` (`/student/marksheet`),
  `controller/MarksheetDownloadServlet.java` (`/student/marksheet/download`),
  `controller/VerificationServlet.java` (`/verify/result/*`)
- `webapp/student/marksheet.jsp`, `webapp/verify/result.jsp`
- `test/.../util/QRCodeUtilTest.java`
- `db/schema.sql`: `marksheet_verifications` table, appended at the end
  (this file's own established "newest table goes last" convention)

**Modified (small, targeted edits only):**
- `entity/enums/ResultStatus.java` - added `mostFinal(Collection<ResultStatus>)`
- `common/fragments/student-head.jspf` - added the Marksheet nav link;
  updated the fragment's own "what's still missing" comment
- `assets/css/auth.css` - appended `.verify-*` rules for the public page
  (no new CSS file; `auth.css` is already this project's "standalone
  page" stylesheet)

**Untouched, reused exactly as built:** `pom.xml`, `web.xml`,
`application.properties`, `AuthenticationFilter`, `AuthorizationFilter`,
`SecurityUtil`, `DateUtil`, `ActivityLog`/`ActivityLogDAO`,
`SystemSetting`/`SystemSettingsService`, `ResultDAO`, `ResultSummaryDAO`,
`ResultService`, every entity except the one enum addition above.

## Verification

Manually walked (no live MySQL in this sandbox to run it against, so this
is the same "trace it, don't assume it" discipline PHASE1-FOUNDATION.md
used for the same reason):

1. A student with a complete `ResultSummary` for a published exam visits
   `/student/marksheet` → sees that exam listed → clicks Download PDF.
2. `MarksheetDownloadServlet` resolves `Student` from the session
   (never trusts a request parameter for it) → `MarksheetService` confirms
   `isComplete()` → mints a token on first request (`VerificationService`,
   inside a transaction, re-checked against the unique constraint) →
   `QRCodeUtil` encodes `https://.../verify/result/{token}` →
   `PDFUtil` renders the document → bytes stream back as
   `application/pdf`, and an `ActivityLog` row records the download.
3. A second download of the same marksheet reuses the same token (find
   path in `VerificationService`, no second row inserted) - the same QR
   code on both PDFs.
4. Scanning that QR code hits `/verify/result/{token}` with no
   session at all (confirmed against `AuthenticationFilter`'s actual
   public-path logic, not assumed) → `VerificationServlet` → valid
   banner with student name / exam / academic year / PUBLISHED-or-LOCKED
   status, nothing else.
5. An unrecognized or hand-edited token renders the same page's "Not
   Verified" state, never a stack trace or a 404.
6. A student who is not fully published for an exam sees no download
   link on `/student/marksheet` at all; a direct/stale download URL for
   that exam gets a 409 with an explanatory message, not a raw error page.

## Addendum (found during Phase 14)

This phase's "Files" list above should have included `hibernate.cfg.xml`
and did not: `MarksheetVerification` was never added to that file's
`<mapping>` list, meaning Hibernate never actually knew the entity
existed. Found and fixed in Phase 14 (see that phase's own doc) alongside
an identical, unrelated pre-existing gap for `SystemSetting` (Phase 5f).

## Deferred beyond this phase

- **Admin-initiated marksheet generation** for an arbitrary student -
  `MarksheetService`'s signature already supports it; Phase 15
  (Reporting) is where its controller/UI belongs.
- **Notification-on-generation** ("your marksheet is ready") - Phase 14's
  concern; this phase does not touch `Notification`/`NotificationService`.
- **Reverse-proxy `X-Forwarded-Proto`/`X-Forwarded-Host` handling** for
  `MarksheetDownloadServlet`'s base-URL derivation - correct for direct
  and standard proxy deployments already; Phase 18's deployment guide is
  where Tomcat's `RemoteIpValve` configuration for this belongs, matching
  how `web.xml`'s own `secure`-flag comment already defers the identical
  class of concern.
- **Institution logo upload** - `SystemSetting.institutionLogoPath` is a
  plain text path with no upload UI yet (Phase 5f scope, not this one);
  `PDFUtil.loadLogo` already degrades gracefully to a text-only header
  when the path is blank or unreadable, so this phase does not block on it.
- **No seed-data.sql change.** A verification token is minted naturally
  the first time any seeded demo student downloads a marksheet during the
  Sec. 58 demonstration flow; nothing needs to pre-exist.
