# Phase 5c — Admin: Academic Setup: Architecture Decisions

## 1. A real, breaking bug caught before it shipped: `fmt:formatDate` vs. `LocalDate`

The first draft of `academic-years.jsp` used
`<fmt:formatDate value="${y.startDate}" pattern="dd MMM yyyy"/>` to display
`AcademicYear.startDate`, which is a `java.time.LocalDate` (Phase 3's entity
design). JSTL's `<fmt:formatDate>` was built around `java.util.Date` and
does not accept `java.time` types - passing a `LocalDate` throws
`javax.el.ELException: Cannot convert ... to class java.util.Date` at
render time. Verified against multiple independent sources rather than
assumed, precisely because the Phase 5a EL/record question had already
shown that a stack assumption can be wrong in either direction (that one
turned out fine; this one didn't). Fixed by dropping `fmt:formatDate` for
`LocalDate` fields in favor of plain `${date}` output, which calls
`LocalDate.toString()` (ISO `yyyy-MM-dd`) - not as decorative as a custom
pattern, but correct, and it has a second benefit: HTML `<input type="date">`
fields both submit and expect exactly that format, so form values round-trip
through validation errors with no parsing step needed in either direction.
Swept the rest of the webapp tree afterward and confirmed no other page
had the same pattern.

## 2. `AcademicYear` and `Semester` get no delete action - a direct consequence of Phase 3

Neither entity extends `SoftDeletableEntity` (Phase 3 chose
`TimestampedEntity` for both, since Exams, Sections, Subjects, and
GradingRules all reference them, making "disappear" unsafe). This phase
doesn't retrofit delete support the entities were deliberately not given -
`AcademicYearServlet` and `SemesterServlet` simply have no `/delete`
mapping. `AcademicYear` gets `/set-current` instead, as the one non-CRUD
action this resource actually needs.

## 3. Semester's identity is fixed after creation; only its dates are editable

`(course, academicYear, semesterNumber)` is the unique key
(`uq_semesters_course_year_number` in `schema.sql`). Editing a semester
lets an admin correct its start/end dates but not reassign it to a
different course, year, or number - by the time a semester exists, Sections
and Subjects may already reference it, and silently letting its identity
shift out from under them would be a worse problem than requiring a new
semester to be created if the identity was genuinely wrong. Reflected
directly in `AcademicSetupService.updateSemester()`'s signature, which
takes only dates, and in the edit form, which shows the course/year/number
as read-only text rather than editable fields.

## 4. `AcademicSetupService` is one class covering five entities

Unlike `UserService` (genuinely complex: multi-entity transactions, temp
passwords, a last-admin safety check), each of these five entities'
operations are a handful of lines - validate, save, done. Section 7 of the
spec itself names them as one feature ("ADMIN — ACADEMIC SETUP"), so one
service class with clearly-delimited per-entity sections matches that
grouping rather than creating five thin classes that would each mostly
just wrap two or three DAO calls.

## 5. One servlet per entity, covering list/create/edit/delete/restore together

Same reasoning as `UserFormServlet` in Phase 5b: branching on the exact
servlet path inside one class, rather than five classes per entity, since
the operations share a redirect target, error-handling shape, and DAO
dependency. `AcademicYearServlet`/`SemesterServlet` simply omit the
`/delete` branch entirely, matching point 2.

## 6. `restoreX()` methods use plain `findById()`, not `findAllIncludingDeleted()`

An early draft of the three restore methods (`Department`, `Course`,
`Section`) loaded every row via `findAllIncludingDeleted()` and filtered
for a matching id in Java - working, but wasteful. `AbstractDAO.findById()`
is inherited as-is by every soft-deletable DAO and was never overridden to
exclude deleted rows (only `findAll()`/`findDeleted()`/`count()` were) - so
a plain `findById()` already finds a soft-deleted row correctly. Caught
during review and simplified to the direct, more efficient call.

---

## Verification

- **javac, uncapped**: 943 errors across all 119 project files. Every
  category checked traces to the same missing-dependency cascade as every
  phase before this one, including `AcademicSetupService.java` specifically
  (the same two `org.hibernate` imports every transaction-managing Service
  in this project has).
- **JSTL/HTML tag balance**: all 11 new pages, each combined with the shell
  fragments as the document they actually render as, balance exactly.
- **`fmt:formatDate`/`LocalDate` sweep**: grepped the entire `webapp` tree
  after the fix to confirm no other page carries the same landmine.
