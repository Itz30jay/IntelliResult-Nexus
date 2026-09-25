package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.Notice;
import com.intelliresult.nexus.entity.enums.UserRole;

import java.util.List;

public class NoticeDAOImpl extends AbstractSoftDeletableDAO<Notice, Long> implements NoticeDAO {

    public NoticeDAOImpl() {
        super(Notice.class);
    }

    @Override
    public List<Notice> findPublishedForAudience(UserRole role) {
        // NoticeAudienceConverter maps audience to Set<UserRole> in Java, but
        // HQL has no way to query "does this converted Set contain X" - the
        // converter only knows how to translate a whole value, not filter
        // inside one. MySQL's own FIND_IN_SET() does exactly this against the
        // native SET column, so this is a native query rather than HQL,
        // mapped straight back to the Notice entity via the Class overload of
        // createNativeQuery (standard JPA API since 2.1, not Hibernate-specific).
        return session().createNativeQuery(
                        "SELECT * FROM notices WHERE is_published = TRUE AND deleted = FALSE "
                                + "AND FIND_IN_SET(:role, audience) > 0 "
                                + "AND (expiry_date IS NULL OR expiry_date > NOW()) "
                                + "ORDER BY priority DESC, published_date DESC",
                        Notice.class)
                .setParameter("role", role.name())
                .getResultList();
    }

    @Override
    public List<Notice> findRecent(int limit) {
        return namedQuery("FROM Notice WHERE deleted = false ORDER BY createdAt DESC")
                .setMaxResults(limit)
                .getResultList();
    }
}
