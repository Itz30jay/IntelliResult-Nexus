package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.SoftDeletableEntity;

import java.util.List;

/**
 * Declares the two recycle-bin queries (Sec. 27) that
 * {@link AbstractSoftDeletableDAO} already implements, so that a DAO
 * interface for one of the 8 soft-deletable entities (Course, Department,
 * Exam, Notice, Result, Section, Subject, User) can extend this instead of
 * {@link GenericDAO} directly and actually expose them.
 * <p>
 * This fixes a real bug, not a hypothetical one: {@code DepartmentDAO},
 * {@code CourseDAO}, {@code SectionDAO}, {@code SubjectDAO}, and
 * {@code ExamDAO} all extended {@code GenericDAO} only, while their
 * controllers (DepartmentServlet, CourseServlet, SectionServlet,
 * SubjectServlet, ExamServlet) called {@code .findDeleted()} on a field
 * declared with that interface type. A concrete {@code XyzDAOImpl} has the
 * method - inherited from {@code AbstractSoftDeletableDAO} - but Java
 * resolves method calls against the *declared* type of the variable, and
 * the interface never declared it. Every one of those five controllers
 * would fail to compile. Caught here, once, and fixed at the interface
 * level rather than patched five times: every DAO interface for a
 * soft-deletable entity now extends this instead of {@code GenericDAO}
 * directly, including {@code UserDAO}, {@code ResultDAO}, and
 * {@code NoticeDAO}, which don't have a caller yet but would have hit the
 * identical wall the moment Phase 5f/7/8 added one.
 */
public interface SoftDeletableDAO<T extends SoftDeletableEntity, ID> extends GenericDAO<T, ID> {

    /** Recycle-bin listing (Sec. 27: "View deleted records"). */
    List<T> findDeleted();

    /** Every row regardless of deleted status - reporting/audit use only. */
    List<T> findAllIncludingDeleted();
}
