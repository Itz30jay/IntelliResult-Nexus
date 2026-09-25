package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.config.HibernateUtil;
import com.intelliresult.nexus.entity.SystemSetting;

public class SystemSettingDAOImpl implements SystemSettingDAO {

    @Override
    public SystemSetting get() {
        return HibernateUtil.getCurrentSession().find(SystemSetting.class, SystemSetting.SINGLETON_ID);
    }

    @Override
    public void update(SystemSetting settings) {
        HibernateUtil.getCurrentSession().merge(settings);
    }
}
