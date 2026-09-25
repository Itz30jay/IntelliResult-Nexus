package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.SoftDeletableEntity;

import java.util.List;

/**
 * For the 8 entities extending SoftDeletableEntity (Department, Course,
 * Section, Subject, User, Exam, Result, Notice). Overrides findAll() to
 * exclude soft-deleted rows by default - the right default for every normal
 * screen in the system - and implements the two methods
 * {@link SoftDeletableDAO} declares for the recycle bin (Sec. 27)
 * specifically: seeing what's deleted, and (rarely) seeing literally
 * everything. Centralizing this here means "active records only by default"
 * is guaranteed consistent across all 8 DAOs instead of each one repeating
 * (and risking forgetting) a WHERE deleted = false clause.
 */
public abstract class AbstractSoftDeletableDAO<T extends SoftDeletableEntity, ID> extends AbstractDAO<T, ID>
        implements SoftDeletableDAO<T, ID> {

    private final String entityName;

    protected AbstractSoftDeletableDAO(Class<T> entityClass) {
        super(entityClass);
        this.entityName = entityClass.getSimpleName();
    }

    @Override
    public List<T> findAll() {
        return session()
                .createQuery("FROM " + entityName + " WHERE deleted = false", entityClass)
                .getResultList();
    }

    /** Recycle-bin listing (Sec. 27: "View deleted records"). */
    @Override
    public List<T> findDeleted() {
        return session()
                .createQuery("FROM " + entityName + " WHERE deleted = true", entityClass)
                .getResultList();
    }

    /** Every row regardless of deleted status - reporting/audit use only; normal screens want findAll() (active) or findDeleted() (recycle bin), not this. */
    @Override
    public List<T> findAllIncludingDeleted() {
        return session().createQuery("FROM " + entityName, entityClass).getResultList();
    }

    @Override
    public long count() {
        return session()
                .createQuery("SELECT COUNT(e) FROM " + entityName + " e WHERE e.deleted = false", Long.class)
                .getSingleResult();
    }
}
