package com.intelliresult.nexus.entity;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.Hibernate;

import java.util.Objects;

/**
 * Root of every entity. IDENTITY generation matches MySQL's AUTO_INCREMENT
 * columns directly (MySQL has no native sequence object the way Postgres/
 * Oracle do, so IDENTITY - not SEQUENCE or TABLE - is the correct strategy
 * here). equals()/hashCode() follow Hibernate's own documented proxy-safe
 * pattern rather than a naive getClass()/id implementation:
 *   - Hibernate.getClass(this) unwraps a lazy-loading proxy to its real
 *     entity class before comparing, so a proxy and its initialized entity
 *     correctly compare equal - a plain getClass() call would not.
 *   - hashCode() is constant (based on the class, never on id or any
 *     mutable field) so an entity's hash bucket never changes across its
 *     lifecycle. Hashing on id would break the Set/Map contract for any
 *     entity added to a HashSet before being persisted (id is null then,
 *     and changes once assigned).
 *   - Two transient entities (both id == null) are never equal to each
 *     other via this method, even if they're literally the same Java
 *     object reference reaching the `this == o` check first - only
 *     database identity, once assigned, makes two entities equal.
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public Long getId() {
        return id;
    }

    protected void setId(Long id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        BaseEntity other = (BaseEntity) o;
        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
