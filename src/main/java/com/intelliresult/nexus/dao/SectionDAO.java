package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.Section;

import java.util.List;

public interface SectionDAO extends SoftDeletableDAO<Section, Long> {
    List<Section> findBySemester(Long semesterId);
}
