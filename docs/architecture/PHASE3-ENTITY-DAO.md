# Phase 3 — Entity + DAO: Architecture Decisions

## 1. Mapped superclass hierarchy

`BaseEntity` (id) → `CreatedAtEntity` (+createdAt) → `TimestampedEntity`
(+updatedAt) → `SoftDeletableEntity` (+deleted/deletedBy/deletedAt). Each
entity extends whichever level matches what its schema.sql table actually
has - not every table has the same timestamp/soft-delete shape (e.g.
`notifications`/`activity_logs` have `created_at` only; `students`/`teachers`
have both timestamps but no soft-delete; `result_history` has neither and
extends `BaseEntity` directly). The hierarchy exists to avoid repeating the
same three-to-six fields across 18 classes, not to force every entity into a
shape it doesn't have - see each entity's own class-level comment for why it
extends what it does.

## 2. equals()/hashCode() - proxy-safe, verified against current Hibernate docs

Every entity inherits `BaseEntity`'s implementation: `Hibernate.getClass()`
(not raw `getClass()`) for the type check, so a lazy-loading proxy compares
correctly against its initialized entity; id-based equality only when both
ids are non-null; a constant, class-based `hashCode()` so an entity's hash
bucket never changes across its lifecycle (important for anything added to a
`Set`/`Map` before being persisted, since `id` is null until then). Confirmed
against Hibernate's own documentation rather than assumed from general
Java-entity folklore, since this is a well-known but easy-to-get-subtly-wrong
pattern.

## 3. No bidirectional `@OneToMany` collections

Every relationship in this codebase is a unidirectional `@ManyToOne` from
child to parent - `Department` has no `List<Course>`, `Section` has no
`List<Student>`, etc. "Get all children of X" is always a DAO query
(`courseDAO.findByDepartment(id)`) instead of collection traversal. This is
a deliberate trade against the more common bidirectional-collection tutorial
pattern, for a reason specific to this project: Sec. 49 explicitly warns
against "loading thousands of records into memory," and an unbounded
`@OneToMany` is exactly the shape of bug that causes it (a `Department`
entity that transitively pulls in every `Student` ever enrolled the moment
someone touches `department.getStudents()`). DAO methods give the Service
layer control over filtering/pagination that a raw collection can't.

## 4. `Subject.totalMaxMarks` - `@Generated(GenerationTime.ALWAYS)`, verified

The column is `GENERATED ALWAYS AS (...) STORED` in MySQL (schema.sql).
Mapped with `insertable = false, updatable = false` (Hibernate must never
try to write it) plus `@Generated(GenerationTime.ALWAYS)` (Hibernate must
re-`SELECT` it after every insert/update, since only MySQL knows the
computed value). Confirmed against Hibernate's own current documentation
before writing this - the annotation shape for DB-generated columns has
changed across Hibernate versions, and this is exactly the kind of thing
worth checking rather than pattern-matching from an older tutorial.

## 5. `NoticeAudienceConverter` for MySQL's native `SET` type

JPA has no first-class mapping for MySQL's `SET` column type, so
`Notice.audience` (`Set<UserRole>`) uses a plain `AttributeConverter`
(portable JPA API, not a Hibernate-specific `UserType`) rather than trying to
force-fit an unsupported column shape into a standard mapping. One
consequence, documented on `NoticeDAO`: HQL can't query "does this converted
Set contain X" against a converted value, so `findPublishedForAudience()` is
a native SQL query using MySQL's `FIND_IN_SET()`, not HQL - the one query in
this phase that had to drop to native SQL, and it's called out explicitly
rather than silently mixed in among the HQL ones.

## 6. `deletedBy` is a plain `Long`; `TeacherSubject.assignedBy`/`unassignedBy` are full relationships

A deliberate inconsistency, explained rather than silently applied: every
`SoftDeletableEntity.deletedBy` is a raw `Long` (the DB's raw id value), not
a managed `@ManyToOne User`, because it's written once at delete time and
read only inside the occasional recycle-bin admin view. `TeacherSubject`'s
`assignedBy`/`unassignedBy`, by contrast, ARE full lazy `@ManyToOne User`
relationships, because assignment history is a primary, frequently-displayed
feature (Sec. 8's "View assignments"), not an occasional audit lookup -
resolving the assigner's name via the relationship directly is worth the
extra association here in a way it isn't for eight rarely-viewed
`deletedBy` columns.

## 7. Generic DAO pattern, with an extra layer for soft-deletable entities

`GenericDAO<T, ID>` (interface) + `AbstractDAO<T, ID>` (Hibernate-backed
implementation) give every DAO `save`/`update`/`findById`/`findAll`/`delete`
for free. A second layer, `AbstractSoftDeletableDAO<T extends
SoftDeletableEntity, ID>`, overrides `findAll()` to exclude soft-deleted rows
by default and adds `findDeleted()` / `findAllIncludingDeleted()` - so
"active records only" is guaranteed consistent across the 8 entities that
need it (`Department`, `Course`, `Section`, `Subject`, `User`, `Exam`,
`Result`, `Notice`) instead of each DAO repeating (and risking forgetting)
a `WHERE deleted = false` clause by hand.

## 8. `GradingRule.covers(percentage)` lives on the entity, not the DAO

`GradingRuleDAO` has exactly one custom method -
`findActiveByAcademicYear(yearId)` - deliberately not a
`findGradeForPercentage(...)` query. "Which rule matches this percentage" is
calculation logic that Sec. 11 assigns to `ResultCalculationService`, so the
DAO's job stops at fetching the candidate rules; the actual matching happens
in Java by calling each `GradingRule.covers(pct)` (a pure function of that
row's own two boundary columns) from the Service layer in Phase 7. Keeping
this off the DAO is what keeps "how a percentage becomes a grade" defined in
exactly one place once Phase 7 exists.

---

## Verification

- **javac, no classpath**: all 85 `.java` files in the project (54 new this
  phase: 4 mapped superclasses, 6 enums, 1 converter, 18 entities, 2 generic
  DAO classes, 18 DAO interfaces, 18 DAO implementations... plus Phase 1/2's
  existing files) compile with zero real syntax errors - every error
  produced is the expected "package jakarta/org does not exist" cascade from
  having no dependency JARs available, confirmed by inspecting a sample of
  the "cannot find symbol" errors directly (all resolve to annotation types
  like `@Entity`/`@MappedSuperclass` that failed only because their import
  failed).
- **Schema-to-entity cross-check** (a purpose-built script, not just visual
  review): parsed every column out of `schema.sql` and every `@Column`/
  `@JoinColumn` mapping out of every entity, then diffed them table by table.
  Result: all 18 entities' mapped columns match their table's real columns
  **exactly** - zero schema columns left unmapped, zero entity fields
  pointing at columns that don't exist.
- **DAO interface/impl check**: confirmed all 18 DAO interfaces have a
  corresponding `*Impl` providing every declared method, and all 18 `Impl`
  classes extend the correct base (`AbstractDAO` or `AbstractSoftDeletableDAO`).

What this does *not* cover: an actual booted Hibernate `SessionFactory`
validating these mappings against the live schema end-to-end (Maven Central
isn't reachable from this sandbox, so the real dependency JARs can't be
fetched to run one). The schema-to-entity cross-check above is the closest
substitute available without that - real column-level comparison against
the same `schema.sql` that was itself verified against live MySQL in
Phase 2, rather than a guess at consistency.
