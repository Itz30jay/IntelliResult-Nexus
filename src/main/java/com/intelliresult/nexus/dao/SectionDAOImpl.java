package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.Section;

import java.util.List;

public class SectionDAOImpl extends AbstractSoftDeletableDAO<Section, Long> implements SectionDAO {

    public SectionDAOImpl() {
        super(Section.class);
    }

    @Override
    public List<Section> findBySemester(Long semesterId) {
        return namedQuery("FROM Section WHERE semester.id = :semId AND deleted = false ORDER BY name")
                .setParameter("semId", semesterId)
                .getResultList();
    }
}
