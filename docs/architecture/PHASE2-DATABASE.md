# Phase 2 — Database: Architecture Decisions

Schema and seed data were both executed against a real MySQL 8.0.46 instance
during this phase, not just visually reviewed - see the Verification section
at the bottom for exactly what was tested and how.

---

## 1. Courses and Semesters as first-class tables

Phase 1's overview-level ER diagram only sketched `USERS` / `STUDENTS` /
`SUBJECTS` / `SECTIONS` etc. and did not include `courses` or `semesters` as
separate entities. Section 35's required-table list names both explicitly
("Departments, Courses, Academic years, Semesters, Sections..."), so Phase 2
adds them properly: a `semester` is modeled as one running instance of
"semester N of course X in academic year Y" (`semesters.course_id`,
`.academic_year_id`, `.semester_number`), which is what lets `sections` and
`subjects` anchor to a concrete, dated instance rather than an abstract
curriculum position. The Phase 1 diagram is superseded by this phase's; see
the updated diagram in this delivery.

## 2. Subjects: component flags instead of a single `subject_type`

The spec's Section 7 lists "Subject Type... Theory, Practical, Internal,
Final, Improvement" as if it were one categorical field, but Section 29's
marks-entry flow describes entering theory **and** practical **and**
internal marks for the same subject - a real subject can have more than one
mark component simultaneously (e.g. a lab course scored on both a written
paper and lab evaluation), which a single-value type can't represent, and
"Final"/"Improvement" read as exam types (they appear again, unambiguously,
in Section 9's exam-type list) rather than subject types. `subjects` uses
`has_theory` / `has_practical` / `has_internal` booleans, each with its own
max/passing marks, instead - enforced by CHECK constraints so a subject can't
claim a component without the marks that define it (`chk_subjects_theory_marks`
and its practical/internal counterparts), and `total_max_marks` is a `STORED
GENERATED` column so it can never drift out of sync with its three inputs.

## 3. `default_max_marks` on exams is a reference value, not authoritative

Section 9 lists "Maximum marks" as an exam-level field, but two subjects in
the same exam can legitimately have different maximums (a 100-mark theory
subject and a 50-mark lab-only subject in the same Mid-Semester exam).
`exams.default_max_marks` is kept as a reference/display value; the
authoritative maximum for any actual mark entered is always
`subjects.total_max_marks`. Documented as a simplification: every exam type
(Unit Test, Mid-Semester, Final) scores a given subject against that same
subject-level maximum rather than a separate per-exam-type scale. If that
turns out to be wrong once real usage patterns exist, the fix is an optional
per-(exam, subject) override table added when there's a concrete need for
it - not a guess made now.

## 4. `current_section_id` on students (not a per-exam section reference)

A student's section is stored as their current standing, not tracked
per-exam. If a student changes sections mid-course, historical results won't
reflect which section they sat the exam in. Flagged explicitly rather than
silently accepted, for the same reason as #3: this is a real simplification,
not an oversight, and the fix (a per-exam section reference) is
straightforward to add later if it turns out to matter.

## 5. No `admin_profiles` table

`users` is the single identity/auth table for all three roles - `full_name`
and `email` live there once, not duplicated into role-specific tables. An
admin currently needs no attributes beyond identity, so there is no
`admin_profiles` table. An empty extension table that exists only so every
role "has one" would be exactly the unused scaffolding this project's own
rules argue against (Section 63: "no unnecessary global state"). Section 52's
institution-wide settings (logo, grading defaults, mail config) belong on a
purpose-built `system_settings` table when Phase 5 needs it - not bolted onto
a per-admin row, since those settings apply to the institution, not to any
one admin's account.

## 6. `teacher_subjects`: assign/unassign timestamps instead of delete

Section 8 says "Admin can... Remove assignment," which most simply maps to
`DELETE FROM teacher_subjects`. Instead this table has `assigned_at` /
`unassigned_at` (nullable), and "removing" an assignment sets
`unassigned_at` rather than deleting the row. This preserves "who was
assigned to this subject when these marks were entered" for audit purposes -
in the spirit of Section 48's auditability requirement, even though Section 8
itself only asked for assign/remove/view. The trade-off: MySQL 8 has no
partial/filtered unique index (unlike PostgreSQL's `WHERE` clause on a unique
index), so "no second active assignment for the same teacher+subject+section"
cannot be enforced as a table constraint here and is enforced by
`TeacherSubjectService` (Phase 5) instead.

## 7. Fields added beyond the spec's literal list

- `result_history.old_internal_marks` / `new_internal_marks` - the spec's
  Section 35 field list names only theory/practical; internal marks are a
  real component (see #2) and deserve the same audit trail.
- `revaluation_requests.resolved_by` - Section 48 requires answering "who"
  for every academically significant change; an approval/rejection with no
  recorded approver fails that.

Both are called out explicitly per Section 74's "do not silently... change"
guidance - additions, not silent deviations.

## 8. `notices.audience` uses MySQL's native `SET` type

A notice can legitimately target more than one role at once ("for teachers
and students"). `SET('ADMIN','TEACHER','STUDENT')` is a native, indexable fit
for a small, fixed, multi-select vocabulary, without the overhead of a
separate `notice_audiences` junction table for what is always at most three
values.

## 9. What stays a Service-layer rule, not a schema constraint

Three rules in the spec are inherently multi-row (they depend on comparing a
new/edited row against every *other* row in the table), which a single-row
`CHECK` constraint cannot express in any SQL dialect:

- **Grading-rule overlap** (Section 12): a new percentage range must not
  overlap any existing range for the same academic year. `GradingService`
  (Phase 5) checks this with an explicit query before insert/update.
- **One current academic year**: `academic_years.is_current` should have at
  most one `TRUE` row. `AcademicYearService` (Phase 5) enforces this.
- **One active teacher assignment** per (teacher, subject, section) - see #6.

## 10. Seed data methodology

Marks and grades in `seed-data.sql` were generated by a Python script
(not written by hand) using a per-student "ability" model so the pass/fail
distribution and per-student patterns look like real data rather than a
uniform or suspiciously patterned spread, then self-verified before any SQL
was written: for all 70 result rows, component marks sum to the stated
total, total-over-max matches the stated percentage, and the percentage
falls inside the stated grade's configured range. Demo account passwords are
real BCrypt hashes (generated and round-trip-verified with Python's `bcrypt`
library), so the seeded accounts will actually authenticate once Phase 4
builds login - not placeholder text standing in for a hash.

---

## Verification

Executed against a real MySQL 8.0.46 instance (installed in this sandbox for
exactly this purpose):

- `schema.sql` runs clean on an empty database - all 18 tables, every FK,
  every CHECK, the generated column, and the SET column.
- Constraints are *enforced*, not just parsed: confirmed an invalid `role`
  value, a subject claiming `has_theory` with no theory marks, and a
  duplicate `(student, exam, subject)` result were all rejected with the
  expected constraint-violation errors (MySQL error 3819 / 1062).
- `seed-data.sql` runs clean after `schema.sql` - row counts match exactly
  what was intended (70 results, 10 students, 5 subjects, etc.).
- Ran real analytical queries against the seeded data - a `RANK() OVER`
  class-ranking query and a subject-wise average/high/low/pass-rate query,
  i.e. the actual shape of query Phase 7 and Phase 11 will need - and
  confirmed the output is sane (the intentionally-struggling seeded student
  correctly ranks last with 4 subjects failed; every subject's pass rate is
  between 90-100%).

What this does *not* cover: the Java entity/DAO layer (Phase 3) that will
map to this schema - that mapping is verified when Phase 3 builds it, not
guessed at here.
