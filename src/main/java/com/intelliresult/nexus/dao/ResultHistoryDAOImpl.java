package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.ResultHistory;

import java.util.List;

public class ResultHistoryDAOImpl extends AbstractDAO<ResultHistory, Long> implements ResultHistoryDAO {

    public ResultHistoryDAOImpl() {
        super(ResultHistory.class);
    }

    @Override
    public List<ResultHistory> findByResult(Long resultId) {
        return namedQuery("FROM ResultHistory WHERE result.id = :resultId ORDER BY changedAt DESC")
                .setParameter("resultId", resultId)
                .getResultList();
    }
}
