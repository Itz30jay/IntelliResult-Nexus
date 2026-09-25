-- ============================================================================
-- seed-data.sql - bootstrap only, no demo/sample data.
--
-- Upgrade (rebuild pass): every fictional department, course, semester,
-- section, subject, teacher, student, exam, result, notice, and
-- registration/revaluation example that used to live in this file has been
-- removed. This file now creates exactly one thing: the first Admin account,
-- so there is a way to log in at all on a brand-new database. Everything
-- else - departments, courses, academic years, semesters, sections,
-- subjects, grading scale, teachers, students, exams - is real,
-- institution-specific data that only the person running this system can
-- supply, and the Admin panel (Academic Setup, Registrations, Grading
-- Rules, etc.) is where it belongs, not a hardcoded fixture pretending to
-- be a real college.
--
-- system_settings is not seeded here either: SystemSettingsService.
-- getSettings() creates that singleton row itself, with the schema's own
-- placeholder defaults, the first time anyone opens Admin > Settings - see
-- schema.sql's own note on that table.
--
-- CHANGE THE PASSWORD BELOW IMMEDIATELY AFTER YOUR FIRST LOGIN.
-- Email:    admin@yourcollege.edu
-- Password: (given to you once, outside this file, at delivery time -
--            never store a real password in a seed script anyone with
--            repository access can read)
-- ============================================================================

INSERT INTO users (email, password_hash, full_name, role) VALUES
    ('admin@yourcollege.edu', '$2a$10$mNR.fmvf2mSGJ8vlbn4Z0.cB8Kx59ow7VAILSrIBEg0h88f64n5Mm', 'Administrator', 'ADMIN');
