# IntelliResult Nexus

**Intelligent Academic Result, Performance Analytics & Verification ERP**

A layered, enterprise-style academic result-processing and performance-analytics
platform: `JSP → Servlet Controller → Service → DAO → Hibernate Entity → MySQL`.

> **Status: Phase 16 of 18 (Polish) - complete.**
> An audit-and-fix phase, not a new feature: the most significant finding
> was that `toggleSidebar()` had been defined in every `*-foot.jspf` since
> its own respective phase but never actually invoked anywhere - no
> button existed, meaning the mobile navigation Sec. 43 requires had been
> silently broken since it was first written. Fixed with one hamburger
> button per topbar. Also closed: `index.jsp` was still Phase 1's raw
> placeholder despite its own comment promising a Phase 4 redirect;
> `error-404.jsp`/`error-500.jsp` were still inline-styled placeholders;
> `error-500.jsp`'s own comment named unfinished exception-logging work,
> now wired up via this codebase's one deliberate, narrowly-scoped
> scriptlet; none of ~20 DataTables initializations had `responsive: true`
> set, fixed once via a global default rather than twenty edits. Plus a
> skip-link, `:focus-visible`, global submit-loading states, and a
> horizontal-scroll utility for wide tables. Zero controller/service/DAO/
> entity changes - JSP/CSS/JS only.
> See [Phase Progress](#phase-progress).

## Tech Stack

| Layer | Choice | Version |
|---|---|---|
| Language | Java | 17 (LTS) |
| Web platform | Jakarta EE 11 (Servlet 6.1 / JSP 4.0) | — |
| Servlet container | Apache Tomcat | 11.0.x |
| ORM | Hibernate ORM | 7.4.5.Final |
| Database | MySQL | 8.0 / 8.4+ |
| Connection pool | HikariCP | 7.1.0 |
| Build | Maven | 3.9+ |
| Logging | Log4j2 | 2.26.0 |
| Password hashing | jBCrypt | 0.4 |
| Error tracking | Sentry Java SDK | 8.28.0 |
| Excel import/export | Apache POI | 5.5.1 |
| PDF marksheets | OpenPDF (LGPL/MPL) | 3.0.5 |
| QR verification | ZXing | 3.5.4 |
| Email | Jakarta Mail + Angus Mail | 2.1.5 / 2.0.3 |

See `docs/architecture/PHASE1-FOUNDATION.md` for the reasoning behind each
infrastructure deviation from the original spec (Jakarta EE namespace,
OpenPDF vs iText 5, HikariCP, package naming),
`docs/architecture/PHASE2-DATABASE.md` for the schema decisions (Courses/
Semesters as first-class tables, subject component flags, why several
multi-row rules live in the Service layer rather than as constraints), and
`docs/architecture/PHASE3-ENTITY-DAO.md` for the entity/DAO layer (mapped
superclass hierarchy, proxy-safe equals/hashCode, why relationships are
unidirectional, the generic DAO pattern), and
`docs/architecture/PHASE4-SECURITY.md` for authentication/authorization
(why filters are registered in web.xml instead of via @WebFilter, session
fixation protection, the CSRF token pattern, open-redirect protection),
`docs/architecture/PHASE5A-ADMIN-DASHBOARD.md` for the admin shell/dashboard
(why Phase 5 is split into sub-deliveries, the EL/Java-record verification
catch, dashboard aggregate query design),
`docs/architecture/PHASE5B-USER-MANAGEMENT.md` for user management (why
role is immutable after creation, the temporary-password flash pattern),
and `docs/architecture/PHASE5C-ACADEMIC-SETUP.md` for academic setup (a
real fmt:formatDate/LocalDate bug caught before shipping, why AcademicYear
and Semester have no delete action), and
`docs/architecture/PHASE5D-SUBJECTS-ASSIGNMENTS.md` for subjects and
teacher assignments (a real 7-controller flash-message bug found and fixed),
and `docs/architecture/PHASE5E-EXAM-GRADING.md` for exam management and
grading rules (two real bugs found and fixed - a missing-CSS-import gap
affecting 15 admin forms, and a missing-interface-method gap that would
have failed to compile in 5 controllers, the second one confirmed via an
actual scoped `javac` build, not just inspection - plus the
lifecycle-stepper EL-safety choices and why grading-rule uniqueness is
checked beyond what the spec literally asks for), and
`docs/architecture/PHASE5F-NOTICES-SETTINGS.md` for notices and system
settings (why System Settings is a genuine singleton table rather than a
generic key-value store, why five specific EL constructs were deliberately
pushed into servlet code instead - one of them a real, silent,
would-never-have-worked bug caught before it shipped, not a hypothetical
one - and exactly which Sec. 52 settings were left out and why), and
`docs/architecture/PHASE6A-TEACHER-DASHBOARD.md` for the start of the
Teacher module (why `Result.applyCalculatedScore()` staying untouched
isn't a gap but a boundary the entity itself already drew in Phase 3; a
third real pre-existing bug found and fixed - `ResultDAO.java` had never
actually compiled, missing three imports its own method signatures needed,
undetected since Phase 3 until this phase was the first to stage `Result`
into an actual build; and why My Classes/My Subjects are two sorted tables
over one dataset rather than a grouped view), and
`docs/architecture/PHASE6B-MARKS-ENTRY.md` for Marks Entry itself (why
Save Draft and Submit are one form rather than a forced two-step flow; why
validation is all-or-nothing across the whole grid rather than silently
skipping problem rows; a real `\u00b7`-is-not-JSP-syntax mistake caught
before it shipped, and why the fix wasn't an HTML entity either, which
`<c:out>` would have double-escaped; and why one exam-status gate wasn't
enough - Submitted Results needed a second, more permissive one just to
be reachable for its own purpose).

## Prerequisites

- JDK 17+
- Maven 3.9+
- MySQL 8.0+ running locally (or reachable) with a database created for this app
- Apache Tomcat 11.0.x (for actually running the WAR - `mvn compile`/`mvn test`
  don't need it)

## Setup

1. **Create the database, app user, and schema:**
   ```sql
   CREATE DATABASE intelliresult_nexus CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
   CREATE USER 'intelliresult_app'@'localhost' IDENTIFIED BY 'your-local-password';
   GRANT ALL PRIVILEGES ON intelliresult_nexus.* TO 'intelliresult_app'@'localhost';
   ```
   ```bash
   mysql -u root intelliresult_nexus < src/main/resources/db/schema.sql
   mysql -u root intelliresult_nexus < src/main/resources/db/seed-data.sql   # creates the one bootstrap Admin account - required, not demo data
   ```
   Both files were executed against a real MySQL 8.0.46 instance while being
   built - not just visually reviewed - including deliberately trying to
   insert invalid data to confirm the constraints actually reject it. See
   `docs/architecture/PHASE2-DATABASE.md` for exactly what was tested.
   `seed-data.sql` creates exactly one row: the first Admin login. There is
   no sample department/course/student/teacher/exam data of any kind -
   everything else (your institution's real departments, courses,
   semesters, sections, subjects, grading scale, and every Student/Teacher
   account) is created after logging in, either directly by the Admin
   under Academic Setup, or by real people registering themselves at
   `/register` for the Admin to verify. **Change the bootstrap Admin's
   password immediately after your first login.**

2. **Set the required environment variable** (there is no default password -
   this is deliberate; see `AppConfig.java`):
   ```bash
   export INTELLIRESULT_DB_PASSWORD=your-local-password
   ```
   Everything else in `application.properties` has a working local-dev default.
   To point at a different DB/user, or to enable Sentry, override the
   matching key, e.g. `INTELLIRESULT_DB_URL`, `INTELLIRESULT_SENTRY_DSN`
   (see `application.properties` for the full list and the naming rule).

3. **Build:**
   ```bash
   mvn clean compile
   ```
   > This project was built in a sandboxed environment without outbound
   > access to Maven Central, so this exact command has **not** been executed
   > against a live repository here. Every dependency coordinate and version
   > in `pom.xml` was verified against Maven Central / the project's own
   > release pages, and every `.xml`/`.java` file was checked for
   > well-formedness, but running `mvn clean compile` yourself is the first
   > real build verification this project will get - please do that before
   > relying on it.

4. **Run the tests:**
   ```bash
   mvn test
   ```
   Phase 1 only has unit tests for the pure, dependency-free utility classes
   (`ValidationUtil`, `DateUtil`, `AppConfig`). Meaningful integration,
   workflow, and security tests need entities and services that don't exist
   until later phases - Phase 17 is where the full suite described in the
   spec (auth, authorization, workflow, re-evaluation, import, PDF, security)
   gets built.

5. **Package and deploy:**
   ```bash
   mvn clean package
   # copy target/intelliresult-nexus.war to Tomcat 11's webapps/ directory
   ```
   With no entities yet, a successful deploy means: Tomcat starts, Log4j2
   initializes, Sentry initializes (or logs that it's disabled, if no DSN is
   set), Hibernate builds an (empty) SessionFactory against your MySQL
   instance, and `http://localhost:8080/intelliresult-nexus/` renders the
   Phase 1 placeholder page.

## Project Structure

```
IntelliResult-Nexus/
├── pom.xml
├── src/main/java/com/intelliresult/nexus/
│   ├── config/       AppConfig, HibernateUtil          (Phase 1)
│   ├── listener/      ApplicationStartupListener        (Phase 1)
│   ├── filter/        HibernateSessionFilter, AuthenticationFilter, AuthorizationFilter, CsrfFilter (Phase 1 + 4; AuthenticationFilter gained the unreadNotificationCount request attribute in Phase 14)
│   ├── exception/     Exception hierarchy                (Phase 1)
│   ├── util/           DateUtil, ValidationUtil, PasswordUtil, SecurityUtil, AppConstants, NavigationUtil (Phase 1 + 4), GradeUtil (Phase 7), PDFUtil + QRCodeUtil (Phase 12), ExcelUtil (Phase 13)
│   ├── entity/         21 entities (verified by script against hibernate.cfg.xml in Phase 14 - corrects "20", this file's own stated count since Phase 3) + 4 mapped superclasses + enums/ + converter/  (Phase 3; ResultSummary added Phase 7, MarksheetVerification added Phase 12)
│   ├── dao/             GenericDAO/AbstractDAO + 21 DAO interfaces + implementations (Phase 3; ResultSummaryDAO added Phase 7, ResultDAO gained methods in Phases 7/10/11, ResultSummaryDAO gained comparison queries in Phase 11, MarksheetVerificationDAO added Phase 12, NotificationDAO gained countUnreadByUser in Phase 14, ResultSummaryDAO gained findByExamAndSection in Phase 15)
│   ├── service/        AuthenticationService (Phase 4), DashboardService + UserService (Phase 5a/5b), AcademicSetupService (Phase 5c), SubjectService + TeacherAssignmentService (Phase 5d), ExamService + GradingRuleService (Phase 5e), NoticeService + SystemSettingsService (Phase 5f), TeacherDashboardService (Phase 6a), MarksEntryService (Phase 6b), ResultCalculationService (Phase 7), ResultService (Phase 8; gained correctResult in Phase 9, refactored for composability in Phase 10), RevaluationService (Phase 10), StudentAnalyticsService (Phase 11), VerificationService + MarksheetService (Phase 12), StudentImportService + MarksImportService (Phase 13), NotificationService + EmailNotificationService (Phase 14, turning on the Notification entity/DAO and Mail config both dormant since Phase 3/1), ReportService + ReportType (Phase 15, mapping all six Sec. 33 report types into one ReportData shape)
│   └── controller/     LoginServlet, LogoutServlet, PasswordChangeServlet (Phase 4), AdminDashboardServlet + 6 user-management servlets (Phase 5a/5b), 6 academic-setup servlets (Phase 5c), SubjectServlet + TeacherAssignmentServlet (Phase 5d), ExamServlet + GradingRuleServlet (Phase 5e), NoticeServlet + SystemSettingsServlet (Phase 5f; NoticeServlet gained the audience-notify hook in Phase 14), TeacherDashboardServlet + TeacherClassesServlet + TeacherExamsServlet (Phase 6a), MarksEntryServlet + TeacherResultsServlet + TeacherActivityServlet (Phase 6b), ResultApprovalServlet (Phase 8; gained bulk-publish/bulk-lock in Phase 13, gained the student-notify hook in Phase 14), ResultServlet (Phase 9), StudentDashboardServlet (Phase 10, rewritten Phase 11) + StudentRevaluationServlet + AdminRevaluationServlet (Phase 10; gained the student-notify hook in Phase 14), StudentResultServlet + StudentAnalysisServlet (Phase 11), MarksheetServlet + MarksheetDownloadServlet + VerificationServlet (Phase 12), StudentImportServlet + MarksImportServlet (Phase 13), NotificationServlet (Phase 14), ReportServlet + TeacherReportServlet (Phase 15)
├── src/main/resources/
│   ├── hibernate.cfg.xml   (Phase 1; SystemSetting + MarksheetVerification mappings fixed in Phase 14 - see that phase's doc)
│   ├── log4j2.xml
│   ├── application.properties
│   └── db/
│       ├── schema.sql        21 tables, all FKs/checks/indexes    (Phase 2; system_settings added Phase 5f, result_summaries added Phase 7, marksheet_verifications added Phase 12; zero schema changes in Phase 13)
│       └── seed-data.sql     one bootstrap Admin account only - see file header for why nothing else is pre-seeded
├── src/main/webapp/
│   ├── WEB-INF/web.xml
│   ├── index.jsp, common/error-{404,500}.jsp   (Phase 1 placeholders; replaced with real role-aware pages in Phase 16, closing a gap Phase 4 had left open despite its own comment saying otherwise)
│   ├── common/login.jsp, change-password.jsp, access-denied.jsp   (Phase 4 - real, permanent pages), report-view.jsp (Phase 15 - shared print layout for both admin/reports and teacher/reports)
│   ├── common/fragments/ - admin-head/foot.jspf (Phase 5a), teacher-head/foot.jspf (Phase 6a), student-head/foot.jspf (Phase 10, nav extended Phase 11 + Phase 12 + Phase 14 - see PHASE6A doc for why this pair-per-role pattern isn't shared despite near-identical content); all three *-foot.jspf gained a shared flash-message renderer in Phase 14 and, in Phase 16, DataTables Responsive + a global submit-loading-state handler; all three *-head.jspf gained a skip-link and the mobile menu button toggleSidebar() had been missing since it was first written
│   ├── common/report-view.jsp - table wrapped in `.table-scroll` in Phase 16
│   ├── assets/css/tokens.css, auth.css (Phase 4, `.verify-*` public-page rules appended Phase 12), app-shell.css (Phase 5a, renamed from admin-shell.css in Phase 6a once Teacher needed the same role-agnostic selectors; `.nav-badge` added Phase 14, `.mobile-menu-btn` added Phase 16); tokens.css gained `:focus-visible`, `.skip-link`, `.table-scroll` in Phase 16
│   ├── admin/ - dashboard, users, academic-setup, subjects, teacher-assignments, exams, grading-rules, notices, settings, approvals, results, result-history, revaluations, revaluation-resolve, student-import, notifications, reports (34 pages; Phase 5a-5f + Phase 8-10 + Phase 13-15)
│   ├── teacher/ - dashboard, my-classes, my-subjects, exams, marks-entry (select+grid), draft-results, submitted-results, activity, marks-import (10 pages; Phase 6a-6b + Phase 13; no dedicated Teacher reports page - the two report links on marks-entry-select.jsp open the shared common/report-view.jsp directly)
│   ├── student/ - dashboard (Phase 10, rewritten Phase 11), results, analysis (Phase 11), revaluation (Phase 10), marksheet (Phase 12), notifications (Phase 14) - 6 pages
│   └── verify/ - result.jsp (Phase 12) - this project's first public, unauthenticated page other than login
├── src/test/java/...                                              (utility unit tests only - GradeUtilTest added Phase 7, QRCodeUtilTest added Phase 12, ExcelUtilTest added Phase 13; no new tests in Phase 14/15 - NotificationService/EmailNotificationService/ReportService are all Hibernate-backed like every other service, deferred to Phase 17 with the rest)
└── docs/architecture/PHASE1-FOUNDATION.md, ..., PHASE16-POLISH.md
```

## Phase Progress

- [x] Phase 1 - Foundation
- [x] Phase 2 - Database schema, relationships, indexes, seed data
- [x] Phase 3 - Entity + DAO: 18 Hibernate entities, generic + specific DAOs
- [x] Phase 4 - Security: BCrypt, login/logout/password change, auth + authz filters, session fixation protection, CSRF
- [x] Phase 5a - Admin: Shell + Dashboard: sidebar/topbar shell, live stats + Chart.js charts from real seed data
- [x] Phase 5b - Admin: User Management: full CRUD for admin/teacher/student accounts, DataTables list, activate/disable, admin-initiated password reset
- [x] Phase 5c - Admin: Academic Setup: Departments, Courses, Academic Years, Semesters, Sections - full CRUD where the entity design supports it
- [x] Phase 5d - Admin: Subjects + Teacher Assignments: 3-component Subject form, assign/unassign with history
- [x] Phase 5e - Admin: Exam Management + Grading Rules: 7-stage exam lifecycle, overlap/duplicate-safe grading rules
- [x] Phase 5f - Admin: Notices + System Settings: Notice Board with audience/priority/expiry, a genuine singleton Settings table - **Phase 5 (Admin) complete**
- [x] Phase 6a - Teacher: Dashboard + Assigned Classes/Subjects/Exams: Teacher shell built from scratch; a real pre-existing `ResultDAO` compile bug found and fixed
- [x] **Phase 6b - Teacher: Marks Entry + Draft/Submit Workflow** (this delivery): the full Sec. 29 marks-entry grid, Draft/Submitted Results, My Activity - **Phase 6 (Teacher) complete**
- [x] **Phase 7 - Result Calculation Engine** (this delivery): subject-level total/percentage/grade/gradePoint/pass via `ResultCalculationService` + `GradeUtil`; exam-level SGPA/overall grade/pass-fail/CGPA/rank/improvement-vs-previous via a new `result_summaries` table, ranked in bulk with a MySQL 8 `RANK() OVER` native query
- [x] **Phase 8 - Approval / Publishing / Locking** (this delivery): `ResultService` (Approve/Publish/Lock + Sec. 47 integrity guards) + `/admin/approvals` (ResultApprovalServlet + approvals.jsp) - the first controller-reachable caller of Phase 7's calculation engine
- [x] **Phase 9 - Result Versioning & Audit** (this delivery): `ResultService.correctResult` (Sec. 47's authorized-correction path, works on any status) + `/admin/results` browser + `/admin/results/history` audit trail/correction UI - reuses `ResultHistory`/`ResultHistoryDAO` (Phase 3, unused until now)
- [x] **Phase 10 - Re-evaluation** (this delivery): `RevaluationService` (submit/resolve, composing Phase 9's `correctResult`) + `/student/dashboard` + `/student/revaluation` (this project's first student-facing pages) + `/admin/revaluations` (list + resolve)
- [x] **Phase 11 - Comparative Analytics**: `StudentAnalyticsService` (class/topper/subject comparison, subject strength/weakness trend analysis) + `/student/dashboard` (rewritten) + `/student/results` + `/student/analysis` - first real Chart.js usage in this project
- [x] **Phase 12 - Digital Marksheet & QR Verification**: `MarksheetService` + `VerificationService`, `PDFUtil` (OpenPDF) + `QRCodeUtil` (ZXing), `/student/marksheet` + `/student/marksheet/download`, public `/verify/result/{token}` (no auth - already whitelisted since Phase 4) - one new table (`marksheet_verifications`), zero changes to any previously existing table
- [x] **Phase 13 - Bulk Excel Processing**: `ExcelUtil` (Apache POI), `StudentImportService` + `MarksImportService` (both all-or-nothing per `DataImportException`'s pre-existing Phase 1 contract, both reusing Phase 5b/6b's own creation/persistence methods rather than duplicating them), `/admin/users/import`+`/export`, `/teacher/marks-entry/import`+`/export`, and a Bulk Actions panel on `/admin/approvals` looping Phase 8's existing single-exam `publishExam`/`lockExam` - zero new database tables, zero schema changes
- [x] **Phase 14 - Notifications**: `NotificationService` + `EmailNotificationService` (Sec. 37/24's own names), turning on the `Notification` entity/DAO (dormant since Phase 3) and the `Mail` config block (dormant since Phase 1); four triggers wired at their existing call sites - result published (single + bulk), re-evaluation resolved, notice published - in-app always first, email best-effort second. Found and fixed two unrelated pre-existing gaps along the way: `SystemSetting`/`MarksheetVerification` missing from `hibernate.cfg.xml`, and `flashSuccess`/`flashError` set by three earlier phases but never actually rendered anywhere
- [x] **Phase 15 - Reporting**: `ReportService` (Sec. 37) maps Class Result / Subject Analysis / Topper / Improvement / Exam / Academic Year reports into one `ReportData` shape feeding `PDFUtil.buildTabularReport`, `ExcelUtil.writeXlsx`, and a shared print view uniformly; `/admin/reports` (full suite, already linked since Phase 5a) + `/teacher/reports/*` (Class Result + Subject Analysis only, scoped by Phase 13's own `requireAssignment` guard) - zero new database tables, zero schema changes, every underlying query already existed
- [x] **Phase 16 - Polish** (this delivery): an audit, not a new feature. Found and fixed: `toggleSidebar()` defined since Phases 5a/6a/10 but never once invoked anywhere, meaning mobile navigation had been silently broken the entire time; `index.jsp` still Phase 1's raw placeholder despite its own comment promising a Phase 4 redirect; `error-404/500.jsp` still inline-styled placeholders, the latter's own comment naming unfinished exception-logging work now wired via this codebase's one deliberate scriptlet; zero of ~20 DataTables initializations had `responsive: true`, fixed once via a global default. Plus a skip-link, `:focus-visible`, global submit-loading states, and a horizontal-scroll table utility. Zero controller/service/DAO/entity changes
- [ ] Phase 17 - Testing
- [ ] Phase 18 - Documentation (SRS, ER/UML/DFD diagrams, deployment guide)

## License / Academic Use

Suitable for a B.Tech major project, portfolio piece, or GitHub showcase.
Third-party dependency licenses (Apache 2.0, LGPL/MPL for OpenPDF, EPL for
JSTL/Mail) are unchanged from their upstream projects - see each project's own
license for redistribution terms.


cd C:\Users\jayki\IntelliResult-Nexus
$env:Path = "C:\Downloaded\apache-maven-3.9.16-bin\apache-maven-3.9.16\bin;" + $env:Path
$env:CATALINA_HOME = "C:\Downloaded\apache-tomcat-11.0.25-windows-x64\apache-tomcat-11.0.25"
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"
$env:INTELLIRESULT_DB_PASSWORD = "jaykishandas30@"
& "$env:CATALINA_HOME\bin\startup.bat"


http://localhost:8080/intelliresult-nexus/login
