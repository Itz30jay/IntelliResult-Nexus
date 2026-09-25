-- ============================================================================
-- IntelliResult Nexus - Phase 2: Database Schema
-- Target: MySQL 8.0+ / InnoDB / utf8mb4
--
-- Design notes that apply across the whole file (see docs/architecture/
-- PHASE2-DATABASE.md for the full reasoning and worked examples):
--
--  * InnoDB auto-creates an index on any column used in a FOREIGN KEY, so
--    FK columns are NOT given a second, redundant explicit index below.
--    Section 50's required indexes are covered either by that automatic FK
--    index, by a UNIQUE constraint (which is also an index), or - for the
--    handful of non-FK filter columns Section 50 calls out - an explicit
--    CREATE INDEX at the bottom of this file.
--  * Computed values that belong to the Result Calculation Engine
--    (total_marks, percentage, grade, grade_point, rank) are plain nullable
--    columns populated by Java, never DB-generated columns or triggers.
--    Section 11 requires ONE centralized place that computes a result;
--    duplicating that logic into the database would silently create a
--    second one that can drift out of sync with the first.
--  * Multi-row rules that cannot be expressed as a single-row CHECK
--    constraint (grading-rule overlap, "only one active teacher assignment
--    per subject+section") are called out in comments at the relevant table
--    and enforced in the Service layer (Phase 5+), not simulated here with
--    triggers - this keeps validation logic in one language or the other,
--    per the same "one source of truth" reasoning as above.
--  * deleted / deleted_by / deleted_at implement Section 27's recycle bin.
--    Applied to tables that represent records an admin could plausibly
--    create by mistake and want to restore. NOT applied to result_history
--    or activity_logs (append-only audit trails - Section 27 explicitly
--    says "academic result history remains protected", which this reads as
--    "not even soft-deletable"), and not duplicated onto students/teachers
--    (they inherit deletion through their one-to-one users row - see the
--    comment on the students table for why).
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 1;

-- ----------------------------------------------------------------------------
-- departments: top-level academic organizational unit (e.g. "Computer Science").
-- ----------------------------------------------------------------------------
CREATE TABLE departments (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(150)    NOT NULL,
    code        VARCHAR(20)     NOT NULL,
    deleted     BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_by  BIGINT UNSIGNED NULL,
    deleted_at  DATETIME        NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_departments_code UNIQUE (code)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- courses: a program offered by a department (e.g. "B.Tech Computer Science").
-- total_semesters drives how many semesters/ Phase 5's academic-setup UI
-- offers to generate for a given course.
-- ----------------------------------------------------------------------------
CREATE TABLE courses (
    id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    department_id    BIGINT UNSIGNED NOT NULL,
    name             VARCHAR(150)    NOT NULL,
    code             VARCHAR(20)     NOT NULL,
    total_semesters  TINYINT UNSIGNED NOT NULL,
    deleted          BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_by       BIGINT UNSIGNED NULL,
    deleted_at       DATETIME        NULL,
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_courses_code UNIQUE (code),
    CONSTRAINT fk_courses_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT chk_courses_total_semesters CHECK (total_semesters BETWEEN 1 AND 12)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- academic_years: e.g. "2025-2026". is_current is a convenience flag; keeping
-- exactly one row at is_current = TRUE is a Service-layer invariant
-- (AcademicYearService, Phase 5) - not something a single-row CHECK can
-- express, since it depends on every OTHER row in the table.
-- ----------------------------------------------------------------------------
CREATE TABLE academic_years (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    label       VARCHAR(20)  NOT NULL,
    start_date  DATE         NOT NULL,
    end_date    DATE         NOT NULL,
    is_current  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_academic_years_label UNIQUE (label),
    CONSTRAINT chk_academic_years_dates CHECK (end_date > start_date)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- semesters: one running instance of "semester N of course X in academic year
-- Y" - e.g. "Semester 3 of B.Tech CS for 2025-2026". Deliberately a child of
-- BOTH course and academic_year (not just a bare number 1-8) because sections
-- and subjects need to anchor to a concrete, dated instance, not an abstract
-- curriculum position.
-- ----------------------------------------------------------------------------
CREATE TABLE semesters (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    course_id         BIGINT UNSIGNED  NOT NULL,
    academic_year_id  BIGINT UNSIGNED  NOT NULL,
    semester_number   TINYINT UNSIGNED NOT NULL,
    start_date        DATE             NULL,
    end_date          DATE             NULL,
    created_at        DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_semesters_course_year_number UNIQUE (course_id, academic_year_id, semester_number),
    CONSTRAINT fk_semesters_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT fk_semesters_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years (id),
    CONSTRAINT chk_semesters_number CHECK (semester_number BETWEEN 1 AND 12)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- sections: a cohort subdivision within a running semester (e.g. "Section A").
-- ----------------------------------------------------------------------------
CREATE TABLE sections (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    semester_id  BIGINT UNSIGNED NOT NULL,
    name         VARCHAR(10)     NOT NULL,
    capacity     INT UNSIGNED    NULL,
    -- Upgrade addition: PERMANENT is an ongoing institutional cohort that
    -- persists across semesters/years (the common case, hence the default);
    -- ONE_TIME is an ad-hoc grouping stood up for a single exam cycle or
    -- elective batch. Purely descriptive metadata - nothing in the Result
    -- Engine or Marks Entry currently branches on it - so a VARCHAR+CHECK
    -- rather than a native ENUM, matching this file's own established
    -- reasoning for role/status columns elsewhere.
    section_type VARCHAR(20)     NOT NULL DEFAULT 'PERMANENT',
    deleted      BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_by   BIGINT UNSIGNED NULL,
    deleted_at   DATETIME        NULL,
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_sections_semester_name UNIQUE (semester_id, name),
    CONSTRAINT fk_sections_semester FOREIGN KEY (semester_id) REFERENCES semesters (id),
    CONSTRAINT chk_sections_type CHECK (section_type IN ('ONE_TIME', 'PERMANENT'))
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- subjects: has_theory/has_practical/has_internal (rather than a single
-- "subject_type" enum) because Section 29's marks-entry flow describes
-- entering theory AND practical AND internal marks for the same subject -
-- a real subject can have more than one mark component simultaneously,
-- which a single-value type field can't represent. total_max_marks is a
-- STORED GENERATED column (not a Java-computed field like the results-table
-- calculations) because it is pure arithmetic over this row's own three
-- columns with no business rule attached - safe to let MySQL guarantee it
-- never drifts, unlike a result's grade/percentage which involves the
-- grading-rule lookup that Section 11 requires stay centralized in Java.
-- ----------------------------------------------------------------------------
CREATE TABLE subjects (
    id                      BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    semester_id             BIGINT UNSIGNED NOT NULL,
    department_id           BIGINT UNSIGNED NOT NULL,
    subject_code            VARCHAR(20)     NOT NULL,
    subject_name            VARCHAR(150)    NOT NULL,
    credits                 DECIMAL(3,1)    NULL,

    has_theory              BOOLEAN         NOT NULL DEFAULT TRUE,
    theory_max_marks        DECIMAL(6,2)    NULL,
    theory_passing_marks    DECIMAL(6,2)    NULL,

    has_practical           BOOLEAN         NOT NULL DEFAULT FALSE,
    practical_max_marks     DECIMAL(6,2)    NULL,
    practical_passing_marks DECIMAL(6,2)    NULL,

    has_internal            BOOLEAN         NOT NULL DEFAULT FALSE,
    internal_max_marks      DECIMAL(6,2)    NULL,
    internal_passing_marks  DECIMAL(6,2)    NULL,

    total_max_marks DECIMAL(7,2) AS (
        COALESCE(theory_max_marks, 0) + COALESCE(practical_max_marks, 0) + COALESCE(internal_max_marks, 0)
    ) STORED,

    deleted      BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_by   BIGINT UNSIGNED NULL,
    deleted_at   DATETIME        NULL,
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_subjects_code UNIQUE (subject_code),
    CONSTRAINT fk_subjects_semester FOREIGN KEY (semester_id) REFERENCES semesters (id),
    CONSTRAINT fk_subjects_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT chk_subjects_has_any_component CHECK (has_theory OR has_practical OR has_internal),
    CONSTRAINT chk_subjects_theory_marks CHECK (has_theory = FALSE OR (theory_max_marks IS NOT NULL AND theory_passing_marks IS NOT NULL)),
    CONSTRAINT chk_subjects_practical_marks CHECK (has_practical = FALSE OR (practical_max_marks IS NOT NULL AND practical_passing_marks IS NOT NULL)),
    CONSTRAINT chk_subjects_internal_marks CHECK (has_internal = FALSE OR (internal_max_marks IS NOT NULL AND internal_passing_marks IS NOT NULL))
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- users: single identity/auth table for ALL three roles. full_name and email
-- live here (not duplicated into students/teachers) so there is exactly one
-- place that answers "what is this person's name/email" regardless of role.
-- email is nullable (upgrade note, formerly NOT NULL): the public
-- Registration flow's login identifier is roll_no/employee_code, not email,
-- and does not collect an email address at all - only login credentials
-- (see registration_requests below). email remains the identifier for
-- pre-existing/admin-created accounts and, when a student/teacher chooses to
-- add one later, the recovery address Forgot Password's OTP is mailed to.
-- uq_users_email is left in place: MySQL treats multiple NULLs as distinct
-- for a UNIQUE index, so any number of accounts with no email coexist fine,
-- while two accounts still can never claim the same real address.
-- No separate admin_profiles table: an admin currently needs no attributes
-- beyond identity, and an empty extension table with no columns of its own
-- would be exactly the kind of unused scaffolding this project's own rules
-- (and Section 63's "no unnecessary global state") argue against. If admin-
-- specific fields are ever needed they belong on a purpose-built table for
-- whatever that feature is (e.g. system_settings, Section 52), not a bare
-- admin_profiles row that exists only to exist.
-- password_hash is VARCHAR(60): BCrypt output is always exactly 60
-- characters, so this is a precise bound, not an arbitrary one.
-- ----------------------------------------------------------------------------
CREATE TABLE users (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    email          VARCHAR(150) NULL,
    password_hash  CHAR(60)     NOT NULL,
    full_name      VARCHAR(150) NOT NULL,
    role           VARCHAR(20)  NOT NULL,
    phone          VARCHAR(20)  NULL,
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at  DATETIME     NULL,
    deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_by     BIGINT UNSIGNED NULL,
    deleted_at     DATETIME     NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT fk_users_deleted_by FOREIGN KEY (deleted_by) REFERENCES users (id),
    CONSTRAINT chk_users_role CHECK (role IN ('ADMIN', 'TEACHER', 'STUDENT'))
) ENGINE=InnoDB;

CREATE INDEX idx_users_role ON users (role);

-- ----------------------------------------------------------------------------
-- registration_requests: the public Registration flow's staging area.
-- Deliberately NOT a status column bolted onto `users` - a self-registered
-- Student/Teacher supplies only full_name/identifier/phone/password (the
-- literal Registration form fields), never course/section or
-- department/designation, both of which students.course_id and
-- teachers.department_id require NOT NULL. Materializing a real users +
-- students/teachers row before that data exists would either violate those
-- constraints or force them nullable everywhere else in the codebase that
-- already, correctly, assumes a Student always has a course. Keeping the raw
-- submission in its own table until an admin supplies the missing academic
-- placement as part of approval means every other table's invariants never
-- have to bend for a state (pending review) that concerns only this table.
-- identifier is the prospective roll_no (STUDENT) or employee_code
-- (TEACHER) - validated for availability by the Service layer (against both
-- this table's other PENDING rows and the live students/teachers tables),
-- the same "multi-row rule lives in Service, not a constraint" pattern
-- already established for grading_rules/teacher_subjects elsewhere in this
-- file. email is optional (Sec. "registration doesn't collect email"), only
-- ever used later as a Forgot Password recovery address once the account is
-- created. created_user_id is populated only once APPROVED, so an approved
-- row still shows an admin exactly which live account it became.
-- ----------------------------------------------------------------------------
CREATE TABLE registration_requests (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    role              VARCHAR(20)     NOT NULL,
    full_name         VARCHAR(150)    NOT NULL,
    identifier        VARCHAR(30)     NOT NULL,
    phone             VARCHAR(20)     NOT NULL,
    email             VARCHAR(150)    NULL,
    password_hash     CHAR(60)        NOT NULL,
    status            VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    admin_remark      TEXT            NULL,
    created_user_id   BIGINT UNSIGNED NULL,
    reviewed_by       BIGINT UNSIGNED NULL,
    reviewed_at       DATETIME        NULL,
    submitted_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_registration_requests_created_user FOREIGN KEY (created_user_id) REFERENCES users (id),
    CONSTRAINT fk_registration_requests_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES users (id),
    CONSTRAINT chk_registration_requests_role CHECK (role IN ('STUDENT', 'TEACHER')),
    CONSTRAINT chk_registration_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
) ENGINE=InnoDB;

-- A pending queue is always filtered by status first, then usually sorted by
-- submitted_at - this composite index covers both without a filesort.
CREATE INDEX idx_registration_requests_status ON registration_requests (status, submitted_at);

-- ----------------------------------------------------------------------------
-- otp_codes: Forgot Password's one-time codes. otp_hash (never the plain
-- code) uses the same PasswordUtil.hash()/matches() BCrypt pair every
-- password in this schema already goes through - an OTP is itself a short
-- secret credential, so reusing the one hashing seam this codebase already
-- has (rather than inventing a second, weaker "just compare strings" path
-- for this one table) is the same "exactly one seam" reasoning PasswordUtil's
-- own class Javadoc gives for passwords themselves.
-- attempt_count bounds brute-force guessing of a 6-digit code within its own
-- short expiry window; consumed_at (once set) makes a code single-use even
-- if it is still technically unexpired.
-- ----------------------------------------------------------------------------
CREATE TABLE otp_codes (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT UNSIGNED NOT NULL,
    otp_hash      CHAR(60)        NOT NULL,
    expires_at    DATETIME        NOT NULL,
    consumed_at   DATETIME        NULL,
    attempt_count TINYINT UNSIGNED NOT NULL DEFAULT 0,
    requested_ip  VARCHAR(45)     NULL,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_otp_codes_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE INDEX idx_otp_codes_user ON otp_codes (user_id, created_at);

-- ----------------------------------------------------------------------------
-- password_reset_requests: the self-service half of Forgot Password stops at
-- OTP verification, deliberately - "keep Admin in the loop" for the actual
-- credential change means the newly-chosen password is staged here, hashed,
-- and only copied onto users.password_hash by AdminPasswordResetServlet's
-- approval, mirroring registration_requests' identical
-- "verified-but-not-yet-live" shape and the same PENDING/APPROVED/REJECTED
-- vocabulary used throughout this schema (revaluation_requests,
-- registration_requests) rather than inventing a fourth status spelling.
-- otp_verified_at is stamped the moment OTP verification succeeds, before
-- the request row itself is even created, so a reviewing admin can always
-- see the request was never possible without that step happening first.
-- ----------------------------------------------------------------------------
CREATE TABLE password_reset_requests (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT UNSIGNED NOT NULL,
    new_password_hash   CHAR(60)        NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    otp_verified_at     DATETIME        NOT NULL,
    reviewed_by         BIGINT UNSIGNED NULL,
    reviewed_at         DATETIME        NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_password_reset_requests_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_password_reset_requests_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES users (id),
    CONSTRAINT chk_password_reset_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
) ENGINE=InnoDB;

CREATE INDEX idx_password_reset_requests_status ON password_reset_requests (status, created_at);

-- ----------------------------------------------------------------------------
-- students: a one-to-one extension of users holding only academic-specific
-- attributes. department_id is deliberately NOT repeated here - it is
-- reachable via course_id -> courses.department_id, and storing it twice
-- would let the two silently disagree (Section 35: "avoid unnecessary
-- duplication"). "Deleting" a student is done by soft-deleting their users
-- row, not a separate flag here, so there is exactly one place that answers
-- "is this person gone" for any user regardless of role.
-- current_section_id is the student's PRESENT section; it is a simplification
-- that does not preserve which section a student sat in for a past exam if
-- they later change sections. Flagged here rather than silently accepted:
-- if that historical precision turns out to matter once real usage patterns
-- are visible, the fix is a per-exam section reference, not a schema
-- decision worth guessing at speculatively now.
-- ----------------------------------------------------------------------------
CREATE TABLE students (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT UNSIGNED NOT NULL,
    roll_no             VARCHAR(30)     NOT NULL,
    course_id           BIGINT UNSIGNED NOT NULL,
    current_section_id  BIGINT UNSIGNED NULL,
    admission_year_id   BIGINT UNSIGNED NULL,
    date_of_birth       DATE            NULL,
    contact_number      VARCHAR(20)     NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_students_user UNIQUE (user_id),
    CONSTRAINT uq_students_roll_no UNIQUE (roll_no),
    CONSTRAINT fk_students_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_students_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT fk_students_section FOREIGN KEY (current_section_id) REFERENCES sections (id),
    CONSTRAINT fk_students_admission_year FOREIGN KEY (admission_year_id) REFERENCES academic_years (id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- teachers: one-to-one extension of users. Unlike students, department_id
-- here is NOT derivable from anything else a teacher references (teachers
-- aren't tied to a single course), so keeping it directly is not duplication.
-- ----------------------------------------------------------------------------
CREATE TABLE teachers (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT UNSIGNED NOT NULL,
    employee_code  VARCHAR(30)     NOT NULL,
    department_id  BIGINT UNSIGNED NOT NULL,
    designation    VARCHAR(100)    NULL,
    joining_date   DATE            NULL,
    contact_number VARCHAR(20)     NULL,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_teachers_user UNIQUE (user_id),
    CONSTRAINT uq_teachers_employee_code UNIQUE (employee_code),
    CONSTRAINT fk_teachers_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_teachers_department FOREIGN KEY (department_id) REFERENCES departments (id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- teacher_subjects: the many-to-many assignment junction (Section 8).
-- "Remove assignment" is modeled as setting unassigned_at rather than
-- deleting the row, so a later audit can still answer "who was assigned to
-- this subject when these marks were entered" - directly in the spirit of
-- Section 48's auditability requirement, even though Section 8 itself only
-- asked for assign/remove/view, not history.
-- Preventing a second ACTIVE assignment for the same
-- (teacher, subject, section) while one is already active cannot be
-- expressed as a plain UNIQUE index in MySQL 8: unlike PostgreSQL, MySQL has
-- no partial/filtered unique index (a WHERE clause on the index), so a
-- UNIQUE(teacher_id, subject_id, section_id) here would also block
-- re-assigning a teacher after a prior assignment was intentionally ended.
-- TeacherSubjectService (Phase 5) enforces "no duplicate active assignment"
-- with an explicit query instead.
-- ----------------------------------------------------------------------------
CREATE TABLE teacher_subjects (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    teacher_id     BIGINT UNSIGNED NOT NULL,
    subject_id     BIGINT UNSIGNED NOT NULL,
    section_id     BIGINT UNSIGNED NOT NULL,
    -- Upgrade addition: class timing lives on the assignment, not on
    -- `sections`, because two different subjects taught to the SAME section
    -- legitimately meet at different times (a section has many classes; a
    -- class is one teacher+subject+section combination). All three are
    -- nullable - an assignment made before a timetable is finalized is still
    -- a valid assignment, just one My Classes shows without a time yet.
    -- Upgrade: schedule_days (a free-text "Mon, Wed, Fri" field) removed -
    -- conflict detection between two of the same teacher's assignments is
    -- now purely time-range-based (TeacherAssignmentService.assign), so a
    -- day-of-week field with no code ever reading it back would just be
    -- unused surface area. class_start_time/class_end_time are what a
    -- teacher's own schedule and the double-booking check both key off.
    class_start_time TIME         NULL,
    class_end_time   TIME         NULL,
    assigned_by    BIGINT UNSIGNED NOT NULL,
    assigned_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    unassigned_by  BIGINT UNSIGNED NULL,
    unassigned_at  DATETIME        NULL,
    CONSTRAINT fk_teacher_subjects_teacher FOREIGN KEY (teacher_id) REFERENCES teachers (id),
    CONSTRAINT fk_teacher_subjects_subject FOREIGN KEY (subject_id) REFERENCES subjects (id),
    CONSTRAINT fk_teacher_subjects_section FOREIGN KEY (section_id) REFERENCES sections (id),
    CONSTRAINT fk_teacher_subjects_assigned_by FOREIGN KEY (assigned_by) REFERENCES users (id),
    CONSTRAINT fk_teacher_subjects_unassigned_by FOREIGN KEY (unassigned_by) REFERENCES users (id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- exams: default_max_marks is a REFERENCE value for the exam type (e.g. "unit
-- tests are normally out of 50"), not the authoritative maximum for any
-- particular subject's marks - that authority stays on
-- subjects.total_max_marks, since two subjects in the same exam can
-- legitimately have different maximums (e.g. a 100-mark theory subject and a
-- 50-mark lab-only subject in the same Mid-Semester exam).
-- ----------------------------------------------------------------------------
CREATE TABLE exams (
    id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name               VARCHAR(150)    NOT NULL,
    exam_type          VARCHAR(30)     NOT NULL,
    academic_year_id   BIGINT UNSIGNED NOT NULL,
    semester_id        BIGINT UNSIGNED NOT NULL,
    -- Upgrade: start_date/end_date (DATE) widened to full timestamps so an
    -- exam records exactly when it opens/closes, not just which calendar
    -- days it spans - the literal "Start Time / End Time (date + time)"
    -- ask. Renamed rather than kept alongside a separate pair of TIME
    -- columns: a second pair storing only a time-of-day would let date and
    -- time silently disagree about which exam window is authoritative,
    -- exactly the duplication this schema's own header rule warns against.
    start_time         DATETIME        NOT NULL,
    end_time           DATETIME        NOT NULL,
    -- Upgrade: how many times this exam may be conducted (e.g. a
    -- supplementary/makeup sitting) - captured, validated, and displayed by
    -- ExamService/the exam form. Enforcing a hard per-student attempt cap
    -- would require adding an attempt dimension to results'
    -- (student, exam, subject) uniqueness, cascading through ranking/CGPA/
    -- marksheets; deliberately out of scope here as a schema-key change,
    -- not silently skipped - this column exists so that follow-up has
    -- somewhere real to read the configured limit from when it is built.
    attempt_limit      INT UNSIGNED    NOT NULL DEFAULT 1,
    default_max_marks  DECIMAL(6,2)    NULL,
    status             VARCHAR(20)     NOT NULL DEFAULT 'CREATED',
    deleted            BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_by         BIGINT UNSIGNED NULL,
    deleted_at         DATETIME        NULL,
    created_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_exams_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years (id),
    CONSTRAINT fk_exams_semester FOREIGN KEY (semester_id) REFERENCES semesters (id),
    CONSTRAINT chk_exams_dates CHECK (end_time >= start_time),
    CONSTRAINT chk_exams_attempt_limit CHECK (attempt_limit >= 1),
    CONSTRAINT chk_exams_type CHECK (exam_type IN (
        'UNIT_TEST', 'MID_SEMESTER', 'PRACTICAL', 'INTERNAL_ASSESSMENT',
        'FINAL_EXAMINATION', 'IMPROVEMENT_EXAMINATION'
    )),
    CONSTRAINT chk_exams_status CHECK (status IN (
        'CREATED', 'SCHEDULED', 'ACTIVE', 'SUBMISSION', 'APPROVAL', 'PUBLISHED', 'LOCKED'
    ))
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- results: the central transactional table. total_marks / percentage / grade
-- / grade_point / is_pass are nullable and populated exclusively by
-- ResultCalculationService (Phase 7) - see the file header note on why these
-- are never DB-generated. Deliberately no class_rank/overall_rank columns:
-- rank is an aggregate across every subject a student took in one exam, not
-- a per-subject-result fact, so it doesn't belong on this row; whether it
-- gets cached in a dedicated table is a decision Phase 7 is better placed to
-- make once the calculation engine's real query patterns are known, not a
-- schema guess made speculatively here.
-- The UNIQUE constraint below is what enforces Section 47's "prevent
-- duplicate result records" - one row per (student, exam, subject), full stop.
-- ----------------------------------------------------------------------------
CREATE TABLE results (
    id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    student_id       BIGINT UNSIGNED NOT NULL,
    exam_id          BIGINT UNSIGNED NOT NULL,
    subject_id       BIGINT UNSIGNED NOT NULL,

    theory_marks     DECIMAL(6,2)    NULL,
    practical_marks  DECIMAL(6,2)    NULL,
    internal_marks   DECIMAL(6,2)    NULL,

    total_marks      DECIMAL(6,2)    NULL,
    percentage       DECIMAL(5,2)    NULL,
    grade            VARCHAR(10)     NULL,
    grade_point      DECIMAL(4,2)    NULL,
    is_pass          BOOLEAN         NULL,

    status           VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    submitted_by     BIGINT UNSIGNED NULL,
    submitted_at     DATETIME        NULL,
    approved_by      BIGINT UNSIGNED NULL,
    approved_at      DATETIME        NULL,
    published_at     DATETIME        NULL,
    locked_at        DATETIME        NULL,

    deleted          BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_by       BIGINT UNSIGNED NULL,
    deleted_at       DATETIME        NULL,
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_results_student_exam_subject UNIQUE (student_id, exam_id, subject_id),
    CONSTRAINT fk_results_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_results_exam FOREIGN KEY (exam_id) REFERENCES exams (id),
    CONSTRAINT fk_results_subject FOREIGN KEY (subject_id) REFERENCES subjects (id),
    CONSTRAINT fk_results_submitted_by FOREIGN KEY (submitted_by) REFERENCES users (id),
    CONSTRAINT fk_results_approved_by FOREIGN KEY (approved_by) REFERENCES users (id),
    CONSTRAINT chk_results_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'PUBLISHED', 'LOCKED')),
    CONSTRAINT chk_results_marks_nonnegative CHECK (
        (theory_marks IS NULL OR theory_marks >= 0) AND
        (practical_marks IS NULL OR practical_marks >= 0) AND
        (internal_marks IS NULL OR internal_marks >= 0)
    )
) ENGINE=InnoDB;

CREATE INDEX idx_results_status ON results (status);

-- ----------------------------------------------------------------------------
-- result_history: append-only audit trail (Section 13). Never UPDATEd or
-- DELETEd by application code - a row here is a permanent record of one
-- change. old_internal_marks/new_internal_marks are an addition beyond the
-- spec's literal field list (which names only old/new theory and practical);
-- added for the same reason the file header explains for subjects.has_internal
-- - a subject's internal-marks component can be corrected too, and that
-- correction deserves the same audit trail as theory/practical.
-- ----------------------------------------------------------------------------
CREATE TABLE result_history (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    result_id           BIGINT UNSIGNED NOT NULL,
    old_theory_marks     DECIMAL(6,2)   NULL,
    old_practical_marks  DECIMAL(6,2)   NULL,
    old_internal_marks   DECIMAL(6,2)   NULL,
    new_theory_marks     DECIMAL(6,2)   NULL,
    new_practical_marks  DECIMAL(6,2)   NULL,
    new_internal_marks   DECIMAL(6,2)   NULL,
    changed_by          BIGINT UNSIGNED NOT NULL,
    change_reason       TEXT            NOT NULL,
    changed_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_result_history_result FOREIGN KEY (result_id) REFERENCES results (id),
    CONSTRAINT fk_result_history_changed_by FOREIGN KEY (changed_by) REFERENCES users (id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- revaluation_requests (Section 14). resolved_by is an addition beyond the
-- spec's literal field list, for the same reason as result_history above -
-- Section 48 requires answering "who" for every academically significant
-- change, and an approval/rejection with no recorded approver fails that.
--
-- Upgrade: the workflow gained a genuine delegation step that did not exist
-- before - PENDING (submitted) -> ASSIGNED (an admin hands it to any
-- teacher) -> APPROVED/REJECTED (that teacher evaluates and resolves it,
-- which is the point marks actually change - see RevaluationService).
-- assigned_teacher_id/assigned_by/assigned_at record the delegation itself,
-- separately from resolved_by/resolved_at recording who actually evaluated
-- it, because those are two different people/moments and Section 48's
-- audit-trail reasoning applies to both, not just the final one.
-- teacher_report is the assigned teacher's detailed write-up, kept distinct
-- from admin_remark (the admin's own optional note made at assignment time,
-- e.g. instructions to the teacher) rather than overloading one free-text
-- column to mean two different authors' two different messages.
-- new_theory_marks/new_practical_marks/new_internal_marks are the teacher's
-- proposed corrected marks, staged here exactly like
-- admin-resolve-revaluation-resolve.jsp's pre-existing pattern (pre-filled
-- with the result's current values) before the ResultService.doCorrectResult
-- pathway ever runs.
-- ----------------------------------------------------------------------------
CREATE TABLE revaluation_requests (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    student_id            BIGINT UNSIGNED NOT NULL,
    result_id             BIGINT UNSIGNED NOT NULL,
    reason                TEXT            NOT NULL,
    status                VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    assigned_teacher_id   BIGINT UNSIGNED NULL,
    assigned_by           BIGINT UNSIGNED NULL,
    assigned_at           DATETIME        NULL,
    admin_remark          TEXT            NULL,
    teacher_report        TEXT            NULL,
    new_theory_marks      DECIMAL(6,2)    NULL,
    new_practical_marks   DECIMAL(6,2)    NULL,
    new_internal_marks    DECIMAL(6,2)    NULL,
    requested_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_by           BIGINT UNSIGNED NULL,
    resolved_at           DATETIME        NULL,
    CONSTRAINT fk_revaluation_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_revaluation_result FOREIGN KEY (result_id) REFERENCES results (id),
    CONSTRAINT fk_revaluation_assigned_teacher FOREIGN KEY (assigned_teacher_id) REFERENCES teachers (id),
    CONSTRAINT fk_revaluation_assigned_by FOREIGN KEY (assigned_by) REFERENCES users (id),
    CONSTRAINT fk_revaluation_resolved_by FOREIGN KEY (resolved_by) REFERENCES users (id),
    CONSTRAINT chk_revaluation_status CHECK (status IN ('PENDING', 'ASSIGNED', 'APPROVED', 'REJECTED'))
) ENGINE=InnoDB;

CREATE INDEX idx_revaluation_status ON revaluation_requests (status);
-- The assigned teacher's own "my queue" view filters on this pair directly.
CREATE INDEX idx_revaluation_assigned_teacher ON revaluation_requests (assigned_teacher_id, status);

-- ----------------------------------------------------------------------------
-- grading_rules (Section 12). "Deactivate" in the spec is why this has
-- is_active rather than the generic deleted/deleted_by/deleted_at triple used
-- elsewhere - a grading rule that graded an already-published result must
-- stay queryable by exactly that name, not soft-deleted and hidden the way a
-- mistakenly-created department would be.
-- Overlap validation ("percentage ranges for the same academic year must not
-- overlap") is a multi-row rule - checking a new/edited range against every
-- OTHER row for that academic_year - which cannot be written as a single-row
-- CHECK constraint. GradingService (Phase 5) enforces it with an explicit
-- query before insert/update.
-- ----------------------------------------------------------------------------
CREATE TABLE grading_rules (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    academic_year_id  BIGINT UNSIGNED NOT NULL,
    min_percentage    DECIMAL(5,2)    NOT NULL,
    max_percentage    DECIMAL(5,2)    NOT NULL,
    grade             VARCHAR(10)     NOT NULL,
    grade_point       DECIMAL(4,2)    NOT NULL,
    is_active         BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_grading_rules_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years (id),
    CONSTRAINT chk_grading_rules_range CHECK (
        min_percentage >= 0 AND max_percentage <= 100 AND min_percentage <= max_percentage
    )
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- notifications (Section 23) - exact spec field list, nothing added.
-- ----------------------------------------------------------------------------
CREATE TABLE notifications (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT UNSIGNED NOT NULL,
    title       VARCHAR(200)    NOT NULL,
    message     TEXT            NOT NULL,
    is_read     BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE INDEX idx_notifications_user_unread ON notifications (user_id, is_read);

-- ----------------------------------------------------------------------------
-- activity_logs (Section 26) - append-only audit trail, never updated.
-- user_id is nullable to cover system-initiated entries and failed-login
-- attempts against an email that never resolved to a real account.
-- ip_address is VARCHAR(45): the exact length needed to hold the longest
-- possible IPv6 literal, not an arbitrary round number.
-- ----------------------------------------------------------------------------
CREATE TABLE activity_logs (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT UNSIGNED NULL,
    action      VARCHAR(100)    NOT NULL,
    details     TEXT            NULL,
    ip_address  VARCHAR(45)     NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_activity_logs_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE INDEX idx_activity_logs_created_at ON activity_logs (created_at);

-- ----------------------------------------------------------------------------
-- notices (Section 25). audience uses MySQL's native SET type: the target
-- roles are a small, fixed vocabulary and a notice can legitimately target
-- more than one at once ("for teachers and students"), which a single-value
-- column can't express. A SET is a native, indexable fit for exactly that
-- shape without the overhead of a separate notice_audiences junction table
-- for what is always at most three values.
-- ----------------------------------------------------------------------------
CREATE TABLE notices (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    title          VARCHAR(200)    NOT NULL,
    content        TEXT            NOT NULL,
    audience       SET('ADMIN', 'TEACHER', 'STUDENT') NOT NULL DEFAULT 'ADMIN,TEACHER,STUDENT',
    priority       VARCHAR(10)     NOT NULL DEFAULT 'NORMAL',
    is_published   BOOLEAN         NOT NULL DEFAULT FALSE,
    published_date DATETIME        NULL,
    expiry_date    DATETIME        NULL,
    created_by     BIGINT UNSIGNED NOT NULL,
    deleted        BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_by     BIGINT UNSIGNED NULL,
    deleted_at     DATETIME        NULL,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_notices_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT chk_notices_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT chk_notices_expiry CHECK (expiry_date IS NULL OR published_date IS NULL OR expiry_date > published_date)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- Deferred FK constraints: deleted_by on every soft-deletable table should
-- reference users(id), same as users.deleted_by does, but departments/
-- courses/sections/subjects/exams/notices/results are all created earlier in
-- this file than users is - adding the constraint inline at CREATE TABLE
-- time would fail with "users doesn't exist yet". Added here, once every
-- table already exists, rather than reordering the whole file around this
-- one audit-field's dependency.
-- ----------------------------------------------------------------------------
ALTER TABLE departments ADD CONSTRAINT fk_departments_deleted_by FOREIGN KEY (deleted_by) REFERENCES users (id);
ALTER TABLE courses      ADD CONSTRAINT fk_courses_deleted_by      FOREIGN KEY (deleted_by) REFERENCES users (id);
ALTER TABLE sections     ADD CONSTRAINT fk_sections_deleted_by     FOREIGN KEY (deleted_by) REFERENCES users (id);
ALTER TABLE subjects     ADD CONSTRAINT fk_subjects_deleted_by     FOREIGN KEY (deleted_by) REFERENCES users (id);
ALTER TABLE exams        ADD CONSTRAINT fk_exams_deleted_by        FOREIGN KEY (deleted_by) REFERENCES users (id);
ALTER TABLE results      ADD CONSTRAINT fk_results_deleted_by      FOREIGN KEY (deleted_by) REFERENCES users (id);
ALTER TABLE notices      ADD CONSTRAINT fk_notices_deleted_by      FOREIGN KEY (deleted_by) REFERENCES users (id);

-- ----------------------------------------------------------------------------
-- Phase 5f: System Settings (Sec. 52). A genuine singleton, not a generic
-- key-value store - deliberately, even though a key-value table would be
-- more "flexible" for adding arbitrary future settings without a migration.
-- Every other table in this schema prefers real typed columns and real
-- constraints over a loose string-keyed blob (see notices.audience's native
-- SET type + converter for the most direct example of that preference), and
-- a fixed, small, named set of institution-identity fields fits that same
-- philosophy far better than an open-ended key/value pair would.
--
-- Scoped deliberately narrower than Sec. 52's full bullet list: institution
-- identity + marksheet branding only. "Academic year" is already
-- academic_years.is_current (Phase 5c) - duplicating it here would create a
-- second source of truth for the same fact. "Default grading configuration"
-- is already the Dynamic Grading Engine (grading_rules, Phase 5e). "Email
-- configuration" is already externalized to application.properties /
-- INTELLIRESULT_* env vars (Phase 1) - SMTP credentials belong outside the
-- database, not in an admin-editable table. "Result publication
-- configuration" and "Notification configuration" both imply behavior
-- (auto-publish rules, notification toggles) that no service reads yet -
-- Phase 8 and Phase 14 respectively are where those would gain a real
-- consumer; adding the column now would mean an admin-editable setting that
-- silently does nothing, which is worse than not having it yet.
--
-- id is always 1: enforced by the CHECK constraint below, not by
-- AUTO_INCREMENT/omitted here on purpose - there is exactly one row, ever,
-- seeded once and only ever UPDATEd afterward, never INSERTed a second time.
-- ----------------------------------------------------------------------------
CREATE TABLE system_settings (
    id                     BIGINT UNSIGNED PRIMARY KEY,
    institution_name       VARCHAR(200)    NOT NULL DEFAULT 'Your Institution Name (edit in Admin > Settings)',
    institution_address    VARCHAR(300)    NULL,
    institution_logo_path  VARCHAR(500)    NULL,
    signatory_name         VARCHAR(150)    NULL,
    signatory_designation  VARCHAR(150)    NULL,
    updated_by             BIGINT UNSIGNED NULL,
    updated_at             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_system_settings_singleton CHECK (id = 1),
    CONSTRAINT fk_system_settings_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- Phase 7: Result Summaries (Sec. 11). One student's aggregate for one exam -
-- SGPA/overall percentage/rank/pass-fail across every subject Result that
-- student has in that exam. Both results' own class Javadoc and this file's
-- header comment on the results table (above) said explicitly that this
-- decision - whether rank gets cached in a dedicated table - belonged to
-- Phase 7 once the calculation engine's real query patterns were known, not
-- to a schema guess made speculatively in Phase 2. This is that decision:
-- yes, for the same reason subjects.total_max_marks is a stored/generated
-- column rather than recomputed on every read (Sec. 49) - these values are
-- read on every dashboard load and every marksheet, and ranking one student
-- requires knowing every other student's percentage in the same cohort, not
-- just their own row.
--
-- subjects_counted/subjects_expected (rather than a single is_complete flag):
-- keeping both raw numbers lets a future caller show "4 of 5 subjects
-- graded" rather than just a boolean, at no extra cost - ResultSummary's own
-- isComplete() derives the boolean from them for callers that just need the
-- yes/no (Sec. 47's "prevent publishing incomplete results" belongs to
-- Phase 8, which reads that flag; this table only ever reports the fact).
--
-- total_credits is stored (not re-derived from results/subjects every time)
-- specifically so a later semester's CGPA computation never has to re-walk
-- an earlier exam's Result rows just to learn how many credits that
-- semester was worth - the same "avoid unnecessary database calls" Sec. 49
-- reasoning behind caching this table at all.
--
-- overall_grade/overall_grade_point/sgpa are all nullable even though every
-- row that exists has at least one calculated subject: a subject with
-- null/zero credits (Sec. 7 allows a non-credit-bearing subject) can leave
-- sgpa with nothing to average over even when overall_percentage/grade are
-- perfectly well-defined - see ResultCalculationService's handling of
-- Subject.credits. cgpa is additionally null on every non-FINAL_EXAMINATION
-- exam's summary by design, not by omission - CGPA is a cumulative-across-
-- *semesters* figure, and only a Final Examination represents "this
-- semester is done" among Sec. 9's exam types; populating it on every Unit
-- Test would make CGPA jump around with interim assessments the way no real
-- transcript does.
--
-- class_rank/overall_rank have no corresponding application-level default
-- and are populated exclusively by one bulk UPDATE...JOIN (RANK() OVER,
-- MySQL 8+) issued from ResultSummaryDAO.recalculateRanks - never written
-- row-by-row from application code - which is also why there is no CHECK
-- constraint relating them to each other or to overall_percentage: their
-- correctness is guaranteed by that one query being correct, not by a
-- constraint independently re-deriving the same fact.
--
-- No soft-delete triple: everything here is derived from results rows that
-- already carry their own audit trail (Sec. 13/48) - nothing here an admin
-- "created by mistake" and would want restored from a recycle bin (Sec. 27,
-- deliberately not applied to result_history/activity_logs above for the
-- same "nothing to undelete, only to regenerate" reasoning). A wrong
-- summary is fixed by recomputing it, not restoring an old version.
-- ----------------------------------------------------------------------------
CREATE TABLE result_summaries (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    student_id            BIGINT UNSIGNED NOT NULL,
    exam_id               BIGINT UNSIGNED NOT NULL,

    subjects_counted      INT UNSIGNED    NOT NULL,
    subjects_expected     INT UNSIGNED    NOT NULL,

    total_obtained_marks  DECIMAL(8,2)    NOT NULL,
    total_max_marks       DECIMAL(8,2)    NOT NULL,
    total_credits         DECIMAL(5,1)    NOT NULL,
    overall_percentage    DECIMAL(5,2)    NOT NULL,
    overall_grade         VARCHAR(10)     NULL,
    overall_grade_point   DECIMAL(4,2)    NULL,
    sgpa                  DECIMAL(4,2)    NULL,
    cgpa                  DECIMAL(4,2)    NULL,
    is_pass               BOOLEAN         NOT NULL,

    class_rank            INT UNSIGNED    NULL,
    overall_rank          INT UNSIGNED    NULL,

    previous_exam_id      BIGINT UNSIGNED NULL,
    previous_percentage   DECIMAL(5,2)    NULL,
    percentage_change     DECIMAL(6,2)    NULL,

    created_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- One summary per student per exam - the row calculateSubjectResult's
    -- sibling constraint (uq_results_student_exam_subject, above) protects
    -- one level down. ResultCalculationService reads this constraint's
    -- existence as its own upsert signal (find-then-update-or-insert), the
    -- same pattern MarksEntryService already uses for individual results.
    CONSTRAINT uq_result_summaries_student_exam UNIQUE (student_id, exam_id),
    CONSTRAINT fk_result_summaries_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_result_summaries_exam FOREIGN KEY (exam_id) REFERENCES exams (id),
    CONSTRAINT fk_result_summaries_previous_exam FOREIGN KEY (previous_exam_id) REFERENCES exams (id),
    CONSTRAINT chk_result_summaries_percentage CHECK (overall_percentage >= 0 AND overall_percentage <= 100),
    CONSTRAINT chk_result_summaries_counts CHECK (subjects_counted <= subjects_expected)
) ENGINE=InnoDB;

-- student_id already gets a usable leftmost-prefix index for free from the
-- unique constraint above; exam_id needs its own, since recalculateRanks and
-- findByExam both filter/join on it directly and neither ever includes
-- student_id in that filter.
CREATE INDEX idx_result_summaries_exam ON result_summaries (exam_id);

-- ----------------------------------------------------------------------------
-- marksheet_verifications: Phase 12 / Section 20's QR-verification token
-- store. One row per (student, exam) - minted the first time that
-- student's marksheet PDF is generated, then reused for every later
-- download of the same marksheet (see docs/architecture/
-- PHASE12-MARKSHEET.md). Deliberately NOT a snapshot of the result at
-- token-generation time: /verify/result/{token} always re-reads the
-- current results rows for this student_id+exam_id, so a later authorized
-- correction (Section 14) is reflected immediately without this table
-- needing to change. No deleted/deleted_by/deleted_at - the same "nothing
-- here to restore, only to regenerate" reasoning result_summaries above
-- already carries.
-- ----------------------------------------------------------------------------
CREATE TABLE marksheet_verifications (
    id                   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    student_id           BIGINT UNSIGNED NOT NULL,
    exam_id              BIGINT UNSIGNED NOT NULL,
    verification_token   VARCHAR(64)     NOT NULL,
    generated_by         BIGINT UNSIGNED NULL,
    created_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Both uniques double as their own index (this file's own header rule),
    -- so no separate CREATE INDEX is needed for either the student+exam
    -- lookup or the public token lookup.
    CONSTRAINT uq_marksheet_verifications_student_exam UNIQUE (student_id, exam_id),
    CONSTRAINT uq_marksheet_verifications_token UNIQUE (verification_token),
    CONSTRAINT fk_marksheet_verifications_student FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_marksheet_verifications_exam FOREIGN KEY (exam_id) REFERENCES exams (id),
    CONSTRAINT fk_marksheet_verifications_generated_by FOREIGN KEY (generated_by) REFERENCES users (id)
) ENGINE=InnoDB;
