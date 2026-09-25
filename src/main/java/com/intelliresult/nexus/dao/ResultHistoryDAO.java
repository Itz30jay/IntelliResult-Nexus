package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.ResultHistory;

import java.util.List;

public interface ResultHistoryDAO extends GenericDAO<ResultHistory, Long> {
    /** The complete version history for one result, newest first (Sec. 13's history interface). */
    List<ResultHistory> findByResult(Long resultId);
}
