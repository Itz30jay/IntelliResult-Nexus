package com.intelliresult.nexus.dao;

import java.util.List;
import java.util.Optional;

/**
 * The CRUD contract every specific DAO gets for free by extending
 * AbstractDAO. Kept deliberately small: anything beyond "persist, look up
 * by id, list all, remove" is entity-specific by nature (find by email,
 * find by status, aggregate a class average) and belongs on that entity's
 * own DAO interface, not forced into a one-size-fits-all generic contract.
 */
public interface GenericDAO<T, ID> {

    T save(T entity);

    void update(T entity);

    Optional<T> findById(ID id);

    List<T> findAll();

    /** Total row count - added for dashboard/reporting aggregates so "how many X exist" never requires loading every X into memory just to call .size(). */
    long count();

    /**
     * Hard delete. Used sparingly and only where a row genuinely has no
     * academic/audit significance (e.g. a TeacherSubject row that was never
     * actually used - most "removal" in this system is a soft delete via
     * SoftDeletableEntity.markDeleted() or, for TeacherSubject specifically,
     * unassign(), called by the Service layer, not this method).
     */
    void delete(T entity);
}
