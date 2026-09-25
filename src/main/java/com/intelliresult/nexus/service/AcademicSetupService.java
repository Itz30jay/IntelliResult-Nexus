package com.intelliresult.nexus.service;

import com.intelliresult.nexus.config.HibernateUtil;
import com.intelliresult.nexus.dao.AcademicYearDAO;
import com.intelliresult.nexus.dao.AcademicYearDAOImpl;
import com.intelliresult.nexus.dao.CourseDAO;
import com.intelliresult.nexus.dao.CourseDAOImpl;
import com.intelliresult.nexus.dao.DepartmentDAO;
import com.intelliresult.nexus.dao.DepartmentDAOImpl;
import com.intelliresult.nexus.dao.SectionDAO;
import com.intelliresult.nexus.dao.SectionDAOImpl;
import com.intelliresult.nexus.dao.SemesterDAO;
import com.intelliresult.nexus.dao.SemesterDAOImpl;
import com.intelliresult.nexus.entity.AcademicYear;
import com.intelliresult.nexus.entity.Course;
import com.intelliresult.nexus.entity.Department;
import com.intelliresult.nexus.entity.Section;
import com.intelliresult.nexus.entity.Semester;
import com.intelliresult.nexus.entity.enums.SectionType;
import com.intelliresult.nexus.exception.ResourceNotFoundException;
import com.intelliresult.nexus.exception.ValidationException;
import com.intelliresult.nexus.util.ValidationUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Owns Sec. 7's Academic Setup. AcademicYear and Semester expose no
 * delete method here - not an oversight, a consequence of Phase 3's entity
 * design: both extend TimestampedEntity, not SoftDeletableEntity, because
 * too much else (Exams, GradingRules, Sections, Subjects) references them
 * for "disappearing" to ever be safe. Department, Course, and Section all
 * support the full soft-delete/restore cycle (Sec. 27).
 */
public class AcademicSetupService {

    private final DepartmentDAO departmentDAO = new DepartmentDAOImpl();
    private final CourseDAO courseDAO = new CourseDAOImpl();
    private final AcademicYearDAO academicYearDAO = new AcademicYearDAOImpl();
    private final SemesterDAO semesterDAO = new SemesterDAOImpl();
    private final SectionDAO sectionDAO = new SectionDAOImpl();

    // ---------------------------------------------------------------- Department

    public Department createDepartment(String name, String code) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (ValidationUtil.isBlank(name)) errors.put("name", "Name is required.");
        if (ValidationUtil.isBlank(code)) errors.put("code", "Code is required.");
        else if (departmentDAO.existsByCode(code.trim().toUpperCase())) errors.put("code", "This code is already in use.");
        if (!errors.isEmpty()) throw new ValidationException("Please correct the highlighted fields.", errors);

        return inTransaction(() -> {
            Department department = new Department(name.trim(), code.trim().toUpperCase());
            departmentDAO.save(department);
            return department;
        });
    }

    public Department updateDepartment(Long id, String name, String code) {
        Department department = departmentDAO.findById(id).orElseThrow(() -> new ResourceNotFoundException("Department not found."));
        Map<String, String> errors = new LinkedHashMap<>();
        if (ValidationUtil.isBlank(name)) errors.put("name", "Name is required.");
        String normalizedCode = ValidationUtil.isBlank(code) ? "" : code.trim().toUpperCase();
        if (normalizedCode.isEmpty()) errors.put("code", "Code is required.");
        else if (!normalizedCode.equals(department.getCode()) && departmentDAO.existsByCode(normalizedCode)) errors.put("code", "This code is already in use.");
        if (!errors.isEmpty()) throw new ValidationException("Please correct the highlighted fields.", errors);

        return inTransaction(() -> {
            department.setName(name.trim());
            department.setCode(normalizedCode);
            departmentDAO.update(department);
            return department;
        });
    }

    public void softDeleteDepartment(Long id, Long adminId) {
        Department department = departmentDAO.findById(id).orElseThrow(() -> new ResourceNotFoundException("Department not found."));
        runInTransaction(() -> department.markDeleted(adminId));
    }

    public void restoreDepartment(Long id) {
        Department department = departmentDAO.findById(id).orElseThrow(() -> new ResourceNotFoundException("Department not found."));
        runInTransaction(department::restore);
    }

    // ---------------------------------------------------------------- Course

    public Course createCourse(Long departmentId, String name, String code, int totalSemesters) {
        Department department = departmentDAO.findById(departmentId).orElseThrow(() -> new ResourceNotFoundException("Department not found."));
        Map<String, String> errors = validateCourseFields(name, code, totalSemesters, null);
        if (!errors.isEmpty()) throw new ValidationException("Please correct the highlighted fields.", errors);

        return inTransaction(() -> {
            Course course = new Course(department, name.trim(), code.trim().toUpperCase(), totalSemesters);
            courseDAO.save(course);
            return course;
        });
    }

    public Course updateCourse(Long id, Long departmentId, String name, String code, int totalSemesters) {
        Course course = courseDAO.findById(id).orElseThrow(() -> new ResourceNotFoundException("Course not found."));
        Department department = departmentDAO.findById(departmentId).orElseThrow(() -> new ResourceNotFoundException("Department not found."));
        Map<String, String> errors = validateCourseFields(name, code, totalSemesters, course.getCode());
        if (!errors.isEmpty()) throw new ValidationException("Please correct the highlighted fields.", errors);

        return inTransaction(() -> {
            course.setName(name.trim());
            course.setCode(code.trim().toUpperCase());
            course.setTotalSemesters(totalSemesters);
            courseDAO.update(course);
            return course;
        });
    }

    private Map<String, String> validateCourseFields(String name, String code, int totalSemesters, String currentCode) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (ValidationUtil.isBlank(name)) errors.put("name", "Name is required.");
        String normalizedCode = ValidationUtil.isBlank(code) ? "" : code.trim().toUpperCase();
        if (normalizedCode.isEmpty()) errors.put("code", "Code is required.");
        else if (!normalizedCode.equals(currentCode) && courseDAO.existsByCode(normalizedCode)) errors.put("code", "This code is already in use.");
        if (totalSemesters < 1 || totalSemesters > 12) errors.put("totalSemesters", "Enter a number of semesters between 1 and 12.");
        return errors;
    }

    public void softDeleteCourse(Long id, Long adminId) {
        Course course = courseDAO.findById(id).orElseThrow(() -> new ResourceNotFoundException("Course not found."));
        runInTransaction(() -> course.markDeleted(adminId));
    }

    public void restoreCourse(Long id) {
        Course course = courseDAO.findById(id).orElseThrow(() -> new ResourceNotFoundException("Course not found."));
        runInTransaction(course::restore);
    }

    // ---------------------------------------------------------------- AcademicYear

    public AcademicYear createAcademicYear(String label, LocalDate startDate, LocalDate endDate) {
        Map<String, String> errors = validateAcademicYearFields(label, startDate, endDate, null);
        if (!errors.isEmpty()) throw new ValidationException("Please correct the highlighted fields.", errors);

        return inTransaction(() -> {
            AcademicYear year = new AcademicYear(label.trim(), startDate, endDate);
            academicYearDAO.save(year);
            return year;
        });
    }

    public AcademicYear updateAcademicYear(Long id, String label, LocalDate startDate, LocalDate endDate) {
        AcademicYear year = academicYearDAO.findById(id).orElseThrow(() -> new ResourceNotFoundException("Academic year not found."));
        Map<String, String> errors = validateAcademicYearFields(label, startDate, endDate, year.getLabel());
        if (!errors.isEmpty()) throw new ValidationException("Please correct the highlighted fields.", errors);

        return inTransaction(() -> {
            // label/dates are not exposed as setters on AcademicYear (only
            // isCurrent is mutable post-construction, deliberately) - a
            // relabeled or redated year is unusual enough, and referenced by
            // enough else, that treating it as effectively immutable except
            // for its "current" flag is the safer default. If genuinely
            // needed, deleting isn't offered either (see class note) so the
            // correct fix today is creating the right year from the start.
            return year;
        });
    }

    private Map<String, String> validateAcademicYearFields(String label, LocalDate startDate, LocalDate endDate, String currentLabel) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (ValidationUtil.isBlank(label)) errors.put("label", "Label is required (e.g. 2026-2027).");
        else if (!label.trim().equals(currentLabel) && academicYearDAO.findByLabel(label.trim()).isPresent()) errors.put("label", "An academic year with this label already exists.");
        if (startDate == null) errors.put("startDate", "Start date is required.");
        if (endDate == null) errors.put("endDate", "End date is required.");
        if (startDate != null && endDate != null && !endDate.isAfter(startDate)) errors.put("endDate", "End date must be after the start date.");
        return errors;
    }

    /** Atomically makes {@code id} the current academic year and un-sets whichever one was current before - the "at most one current row" invariant PHASE2-DATABASE.md #9 assigns to this service. */
    public void setCurrentAcademicYear(Long id) {
        AcademicYear target = academicYearDAO.findById(id).orElseThrow(() -> new ResourceNotFoundException("Academic year not found."));
        runInTransaction(() -> {
            academicYearDAO.findCurrent().ifPresent(previous -> {
                if (!previous.getId().equals(target.getId())) {
                    previous.setCurrent(false);
                    academicYearDAO.update(previous);
                }
            });
            target.setCurrent(true);
            academicYearDAO.update(target);
        });
    }

    // ---------------------------------------------------------------- Semester

    public Semester createSemester(Long courseId, Long academicYearId, int semesterNumber, LocalDate startDate, LocalDate endDate) {
        Course course = courseDAO.findById(courseId).orElseThrow(() -> new ResourceNotFoundException("Course not found."));
        AcademicYear year = academicYearDAO.findById(academicYearId).orElseThrow(() -> new ResourceNotFoundException("Academic year not found."));

        Map<String, String> errors = new LinkedHashMap<>();
        if (semesterNumber < 1 || semesterNumber > course.getTotalSemesters()) {
            errors.put("semesterNumber", "Enter a semester number between 1 and " + course.getTotalSemesters() + " for this course.");
        } else if (semesterDAO.findByCourseYearAndNumber(courseId, academicYearId, semesterNumber).isPresent()) {
            errors.put("semesterNumber", "This semester already exists for the selected course and academic year.");
        }
        if (!errors.isEmpty()) throw new ValidationException("Please correct the highlighted fields.", errors);

        return inTransaction(() -> {
            Semester semester = new Semester(course, year, semesterNumber);
            semester.setStartDate(startDate);
            semester.setEndDate(endDate);
            semesterDAO.save(semester);
            return semester;
        });
    }

    public Semester updateSemester(Long id, LocalDate startDate, LocalDate endDate) {
        // course/academicYear/semesterNumber together form the unique
        // identity of a semester instance (schema.sql's
        // uq_semesters_course_year_number) - editing is limited to the
        // dates around that fixed identity rather than allowing it to be
        // reassigned to a different course/year/number after Sections and
        // Subjects may already reference it.
        Semester semester = semesterDAO.findById(id).orElseThrow(() -> new ResourceNotFoundException("Semester not found."));
        return inTransaction(() -> {
            semester.setStartDate(startDate);
            semester.setEndDate(endDate);
            semesterDAO.update(semester);
            return semester;
        });
    }

    // ---------------------------------------------------------------- Section

    public Section createSection(Long semesterId, String name, Integer capacity, SectionType sectionType) {
        Semester semester = semesterDAO.findById(semesterId).orElseThrow(() -> new ResourceNotFoundException("Semester not found."));
        Map<String, String> errors = validateSectionFields(semesterId, name, null);
        if (!errors.isEmpty()) throw new ValidationException("Please correct the highlighted fields.", errors);

        return inTransaction(() -> {
            Section section = new Section(semester, name.trim().toUpperCase());
            section.setCapacity(capacity);
            section.setSectionType(sectionType == null ? SectionType.PERMANENT : sectionType);
            sectionDAO.save(section);
            return section;
        });
    }

    public Section updateSection(Long id, String name, Integer capacity, SectionType sectionType) {
        Section section = sectionDAO.findById(id).orElseThrow(() -> new ResourceNotFoundException("Section not found."));
        Map<String, String> errors = validateSectionFields(section.getSemester().getId(), name, section.getName());
        if (!errors.isEmpty()) throw new ValidationException("Please correct the highlighted fields.", errors);

        return inTransaction(() -> {
            section.setName(name.trim().toUpperCase());
            section.setCapacity(capacity);
            section.setSectionType(sectionType == null ? SectionType.PERMANENT : sectionType);
            sectionDAO.update(section);
            return section;
        });
    }

    private Map<String, String> validateSectionFields(Long semesterId, String name, String currentName) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (ValidationUtil.isBlank(name)) {
            errors.put("name", "Section name is required (e.g. A).");
        } else {
            String normalized = name.trim().toUpperCase();
            // No dedicated existsByName DAO query: a semester realistically
            // has a handful of sections, so filtering the already-available
            // findBySemester() result in Java is simpler than adding a
            // narrowly-scoped query method for a collection this small.
            boolean collision = sectionDAO.findBySemester(semesterId).stream()
                    .anyMatch(s -> s.getName().equalsIgnoreCase(normalized) && !s.getName().equals(currentName));
            if (collision) {
                errors.put("name", "A section with this name already exists for the selected semester.");
            }
        }
        return errors;
    }

    public void softDeleteSection(Long id, Long adminId) {
        Section section = sectionDAO.findById(id).orElseThrow(() -> new ResourceNotFoundException("Section not found."));
        runInTransaction(() -> section.markDeleted(adminId));
    }

    public void restoreSection(Long id) {
        Section section = sectionDAO.findById(id).orElseThrow(() -> new ResourceNotFoundException("Section not found."));
        runInTransaction(section::restore);
    }

    // ---------------------------------------------------------------- transaction helpers

    private interface TransactionalWork<T> {
        T run();
    }

    private <T> T inTransaction(TransactionalWork<T> work) {
        Session session = HibernateUtil.getCurrentSession();
        Transaction tx = session.beginTransaction();
        try {
            T result = work.run();
            tx.commit();
            return result;
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }

    private void runInTransaction(Runnable work) {
        inTransaction(() -> {
            work.run();
            return null;
        });
    }
}
