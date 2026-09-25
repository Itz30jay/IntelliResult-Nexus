package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.config.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.query.Query;

import java.util.List;
import java.util.Optional;

/**
 * Shared Hibernate plumbing for every DAO. Always reads the Session from
 * HibernateUtil.getCurrentSession() rather than opening its own - the
 * request-bound Session that HibernateSessionFilter set up (see Phase 1),
 * so every DAO called anywhere during one HTTP request shares the same
 * Session, first-level cache, and pending-changes set. No transaction
 * begin/commit here on purpose: that boundary belongs to the Service layer
 * (see HibernateSessionFilter.java's note on why session lifecycle and
 * transaction lifecycle are deliberately different concerns in this project).
 */
public abstract class AbstractDAO<T, ID> implements GenericDAO<T, ID> {

    /** protected, not private: AbstractSoftDeletableDAO needs it to build its own extra queries (findDeleted, findAllIncludingDeleted) without re-deriving it via reflection. */
    protected final Class<T> entityClass;

    protected AbstractDAO(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    protected Session session() {
        return HibernateUtil.getCurrentSession();
    }

    @Override
    public T save(T entity) {
        session().persist(entity);
        return entity;
    }

    @Override
    public void update(T entity) {
        session().merge(entity);
    }

    @Override
    public Optional<T> findById(ID id) {
        return Optional.ofNullable(session().find(entityClass, id));
    }

    @Override
    public List<T> findAll() {
        return namedQuery("FROM " + entityClass.getSimpleName()).getResultList();
    }

    @Override
    public long count() {
        return session()
                .createQuery("SELECT COUNT(e) FROM " + entityClass.getSimpleName() + " e", Long.class)
                .getSingleResult();
    }

    @Override
    public void delete(T entity) {
        session().remove(entity);
    }

    /** Shared helper so every subclass's custom finder methods (findByEmail, findByStatus, etc.) use the exact same typed-query construction pattern instead of each repeating createQuery(..., entityClass) by hand. */
    protected Query<T> namedQuery(String hql) {
        return session().createQuery(hql, entityClass);
    }
}
