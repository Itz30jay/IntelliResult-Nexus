# Phase 5d — Admin: Subjects + Teacher Assignments: Architecture Decisions

## 1. Passing-marks-vs-max-marks: Service layer, not a new DB constraint

schema.sql's CHECK constraints guarantee "if a component is enabled, its
marks aren't null" and "at least one component is enabled." They don't
guarantee passing ≤ max - a same-row comparison MySQL 8 *could* express,
but `SubjectService.validateComponent()` does it instead, matching Sec.
37's assignment of validation logic to the Service layer and keeping the
DB constraints focused on structural completeness, not value sensibility.

## 2. Assign form is inline, not a separate page

Three dropdowns (teacher, subject, section) don't warrant a page of their
own. `TeacherAssignmentServlet` branches on path for `/assign` and
`/unassign` POSTs, same one-servlet-per-resource pattern as every other
Phase 5 controller.

## 3. Real bug found and fixed: 7 controllers set `flashError` but never read it back

`softDelete`/`restore`/`assign`/`unassign` failures (BusinessRuleException)
stash a message in session and redirect to the list page - but none of
those list pages' `doGet` methods ever read that attribute back out. The
error was silently swallowed. Found by grepping for the read-side pattern
across all seven affected controllers (`AcademicYearServlet`,
`CourseServlet`, `DepartmentServlet`, `SectionServlet`, `SemesterServlet`,
plus this phase's `SubjectServlet`/`TeacherAssignmentServlet`) and fixing
all seven with a small shared `readFlashError()` helper per class via
targeted edits, verified afterward with `javac` rather than assumed correct
from the edit succeeding.

---

## Verification

javac uncapped: 1011 errors across all 125 files, still 100% the known
missing-dependency cascade (checked for anything outside the known
patterns - none found). JSTL/HTML tag balance and a `fmt:formatDate`/
`LocalDate` sweep on all 3 new pages: clean.
