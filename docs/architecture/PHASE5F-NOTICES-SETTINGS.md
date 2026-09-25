# Phase 5f — Notices + System Settings

Continues Phase 5 (Admin) from Phase 5e. `Notice`'s entity and DAO already
existed from Phase 3 (this phase adds service/controller/JSP, the same shape
as Phase 5e's `Exam`); `SystemSetting` did not exist anywhere - no entity,
no DAO, no table - so this phase builds all of it, including a schema
addition.

## Starting point (verified before writing anything)

Read in full before any new code: `Notice.java`, `NoticeDAO(Impl)`,
`NoticeAudienceConverter.java`, `NoticePriority.java`; confirmed no
`SystemSetting`-anything existed anywhere in the tree; `SoftDeletableEntity`
(for the `deletedBy`-is-a-raw-`Long` precedent) and `TimestampedEntity`
(for the `@UpdateTimestamp` pattern); `BaseEntity` (to see exactly why a
true singleton shouldn't extend it); `UserDAO`, `AbstractDAO`; `DashboardService`
(confirmed it already wires in `NoticeDAO.findRecent()` from Phase 5a - one
less integration this phase needed to do); `admin-head.jspf` (confirmed
`/admin/notices` and `/admin/settings` were already pre-wired, 404ing,
exactly like `/admin/exams` was before Phase 5e); the `notices` table DDL,
specifically `chk_notices_expiry`.

## Decisions

**1. Notice editing stays open regardless of published state - a
deliberate contrast with Exam (Phase 5e), not an oversight.** `ExamService`
locks editing once `PUBLISHED`, because an exam's identity and dates are
things results will anchor to. A notice isn't an audit-critical academic
record; Sec. 25 lists Create/Edit/Publish/Unpublish/Delete as parallel
actions, not a lock-on-publish sequence. The one place published state does
constrain something is `expiryDate`, and that's schema.sql's own
`chk_notices_expiry` constraint, not a design choice made here - handled by
checking it explicitly in `NoticeService.validate()` so it surfaces as a
field error (Sec. 45) instead of a database exception.

**2. `SystemSetting` does not extend `BaseEntity`.** `BaseEntity`'s
`@GeneratedValue(strategy = GenerationType.IDENTITY)` is wrong for a table
with exactly one row at a permanently fixed id
(`schema.sql`'s `chk_system_settings_singleton CHECK (id = 1)`). A
hand-written `@Id private Long id;` with no `@GeneratedValue`, set once in
the constructor to `SystemSetting.SINGLETON_ID`, is the honest mapping for
that shape.

**3. `SystemSettingDAO` does not extend `GenericDAO`.** Every other DAO
models a collection - `findAll`, `findById`, `delete` - and a singleton
isn't a collection. Forcing `SystemSetting` through that contract would mean
a `findAll()` returning a one-element list nothing should ever iterate and a
`delete()` nothing should ever call. Two methods instead:
`get()` (returns the row, or `null` if it doesn't exist yet) and `update()`
(a `merge()`-backed upsert - see decision 4).

**4. `getSettings()` self-heals a missing row rather than requiring
`seed-data.sql` to have run.** Every other seeded table is something an
admin would eventually create through the UI on a real deployment anyway
(departments, users, ...); there is no "create system settings" screen,
only "edit" - so if a production database only ran `schema.sql`, nothing
would ever insert row `id = 1` unless something did it automatically.
`SystemSettingDAOImpl.update()` uses `session.merge(entity)`, which inserts
when the id doesn't exist yet and updates when it does - so
`SystemSettingsService.getSettings()` can call the same `update()` method
either way, without a separate `create()` path to keep in sync with it.

**5. System Settings is scoped to institution identity + marksheet
branding only - five fields, not the full Sec. 52 bullet list - each
omission named, not just quietly dropped.** See `schema.sql`'s own note on
`system_settings` for the complete reasoning; the short version: "academic
year" is already `academic_years.is_current` (Phase 5c) and duplicating it
here would create a second source of truth for the same fact. "Default
grading configuration" is already the Dynamic Grading Engine (Phase 5e).
"Email configuration" is already externalized to `application.properties` /
`INTELLIRESULT_*` env vars (Phase 1) - SMTP credentials belong outside the
database. "Result publication configuration" and "Notification
configuration" both imply behavior (auto-publish rules, notification
toggles) no service reads yet; Phase 8 and Phase 14 are where those would
gain a real consumer. The five fields that remain - institution name,
address, logo path, signatory name, signatory designation - are pure data
with an unambiguous future consumer (the Phase 12 marksheet PDF, Phase 15
reports), the same "safe to add ahead of its consumer because it's data,
not a guess at unbuilt behavior" reasoning `ExamStatus.isOpenForMarksEntry()`
used in Phase 5e.

**6. `updatedBy` on `SystemSetting` is a plain `Long`, not a
`@ManyToOne`.** Directly reuses `SoftDeletableEntity.deletedBy`'s own
documented reasoning: written once per save, read only inside the settings
screen itself, not a relationship anything else needs to traverse.

**7. The audience checkboxes' pre-checked state, and the expiry date
field's pre-filled value, are computed in `NoticeServlet` (real Java), not
left to the JSP.** Two separate, concrete reasons, not one blanket
"EL is risky" rule:
   - `${notice.audience.contains('ADMIN')}` would not work: `Set<UserRole>.contains(Object)`
     erases its parameter to `Object`, so EL has no target type to coerce
     the string literal `'ADMIN'` toward. It would pass the raw `String`
     through unchanged, and `UserRole.ADMIN.equals("ADMIN")` is `false` -
     enums never equal a `String`. This isn't a hypothetical; it would
     silently and permanently fail to pre-check any box on every edit.
   - `${editingNotice.expiryDate.toLocalDate()}` chains a method call onto
     a value that is legitimately `null` for a no-expiry notice - one of
     the three seeded rows is exactly that case. Unlike the ternary operator
     (spec-guaranteed short-circuit, confirmed safe and left as-is
     elsewhere in these same two JSPs) or `.ordinal()`/`.next()` on a
     never-null enum (Phase 5e), this is a chained call on a value that
     really can be absent, and it wasn't worth finding out the hard way
     whether the EL implementation here null-guards through a method
     invocation the same way it does plain property access.

   Both computed once in `NoticeServlet.doGet()`'s `/edit` branch as plain
   booleans / a pre-formatted `String`, so the JSP only ever does
   `${audienceHasAdmin ? 'checked' : ''}` and `${expiryDateValue}` - no
   method calls on possibly-null or wrongly-typed values anywhere in either
   template.

**8. Draft/Published notice status reuses Phase 5e's `.badge-status-created`
/ `.badge-status-published` classes rather than adding new ones.** Same
semantic pairing either way - neutral for "not live yet," verified-green for
"live" - so the existing two classes already say exactly the right thing;
adding `.badge-notice-draft` etc. would be duplicate CSS expressing an
identical idea.

## Files

**Added**
- `entity/SystemSetting.java`
- `dao/SystemSettingDAO.java`, `dao/SystemSettingDAOImpl.java`
- `service/dto/NoticeRequest.java`, `service/dto/SystemSettingsRequest.java`
- `service/NoticeService.java`, `service/SystemSettingsService.java`
- `controller/NoticeServlet.java`, `controller/SystemSettingsServlet.java`
- `webapp/admin/notices.jsp`, `webapp/admin/notice-form.jsp`, `webapp/admin/settings.jsp`
- `docs/architecture/PHASE5F-NOTICES-SETTINGS.md` (this file)

**Changed**
- `db/schema.sql` - new `system_settings` table, appended at the end of the
  file (after every other table, including the deferred-FK block) so its
  own `updated_by` FK to `users` can be declared inline at `CREATE TABLE`
  time rather than needing the same deferred-`ALTER TABLE` workaround
  `departments`/`courses`/etc. need (they're defined *before* `users` in
  file order; this table isn't).
- `db/seed-data.sql` - one more notice (unpublished, broader audience, real
  expiry - alongside the two Phase 2 rows already there) for the same
  "richer material to demo" reasoning as Phase 5e's extra exams; the one
  `system_settings` row.

No changes to `web.xml` (annotation-based routing, same as every controller
since Phase 4), `admin-head.jspf` (both links already existed, pointing at
routes that simply didn't resolve until now), or `DashboardService.java`
(already wired to `NoticeDAO` since Phase 5a).

## Verification

Same method as Phase 5e, extended: the real source of every file this phase
touches or depends on - 81 real `.java` files now (up from 66), including
everything from Phase 5e's verification set plus `Notice`,
`NoticeAudienceConverter`, `SystemSetting`, their DAOs, `UserDAO`, and both
new services/controllers - compiled together against 38 hand-written stub
classes (up from 35: three more `jakarta.persistence` types -
`AttributeConverter`, `Convert`, `Converter` - for
`NoticeAudienceConverter`, plus `Session.createNativeQuery()`,
`Query.setMaxResults()`, `HttpServletRequest.getParameterValues()`, and
`@Column.columnDefinition()`, none of which any earlier phase's code
happened to call).

First run failed with 3 errors - all missing-stub-member, not project bugs:
`@Column` had no `columnDefinition()` (needed for `Notice.content`'s
`TEXT` mapping), and `HttpServletRequest` had no `getParameterValues()`
(needed for the audience checkboxes, which submit as multiple same-named
parameters). Both added, matching the real Jakarta Persistence and Jakarta
Servlet APIs exactly. Second run: exit code 0, zero errors, 124 `.class`
files from 119 total sources (81 real + 38 stub).

Same limits as Phase 5e's verification, stated there and unchanged here:
this confirms real cross-file consistency in the `.java` sources, not that
the stubs are faithful to the genuine libraries in every respect, and says
nothing about the three new/changed JSPs - no JSP compiler exists in this
sandbox. `mvn clean compile` itself still hasn't been run against a live
Maven Central anywhere in this project's history. Additionally, and new to
this phase specifically: the `system_settings` table addition to
`schema.sql` has not been run against a real MySQL server either - there is
no MySQL available in this sandbox - so the DDL's own validity (the `CHECK
(id = 1)` constraint in particular, since CHECK constraint support and
enforcement details vary across MySQL 8.x point releases) is unverified
beyond careful reading, the same caveat every table in this schema has
carried since Phase 2.

## Deferred to later phases (not gaps in this one)

- Actually reading `institutionName`/`institutionLogoPath`/signatory fields
  anywhere - their first real consumer is the Phase 12 marksheet PDF.
- A file-upload path for the institution logo. `institutionLogoPath` is a
  plain text field (a path/URL the admin enters after uploading through
  some other means); building actual upload handling wasn't attempted here
  and would be a reasonable, well-scoped Phase 12 addition instead.
- Wiring `email_notifications_enabled` (or similar) once
  `EmailNotificationService` (Phase 14) exists to actually consult it - see
  decision 5 for why adding an inert toggle now was deliberately avoided.
