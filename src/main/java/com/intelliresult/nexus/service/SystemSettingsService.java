package com.intelliresult.nexus.service;

import com.intelliresult.nexus.config.HibernateUtil;
import com.intelliresult.nexus.dao.SystemSettingDAO;
import com.intelliresult.nexus.dao.SystemSettingDAOImpl;
import com.intelliresult.nexus.entity.SystemSetting;
import com.intelliresult.nexus.exception.ValidationException;
import com.intelliresult.nexus.service.dto.SystemSettingsRequest;
import com.intelliresult.nexus.util.ValidationUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Owns Sec. 52's System Settings. getSettings() is self-healing: a fresh
 * database that only ran schema.sql (not seed-data.sql) has no
 * system_settings row at all, and this is the one place that matters -
 * every other seeded table (departments, users, ...) is something an admin
 * would create through the UI anyway on a real deployment, but there is no
 * "create system settings" screen, only "edit" them, so something has to
 * conjure the row into existence the first time. See SystemSettingDAO's own
 * Javadoc for why {@code update()} on a not-yet-existing row is what makes
 * that possible without a separate insert path.
 */
public class SystemSettingsService {

    private final SystemSettingDAO systemSettingDAO = new SystemSettingDAOImpl();

    public SystemSetting getSettings() {
        SystemSetting settings = systemSettingDAO.get();
        if (settings != null) {
            return settings;
        }
        return inTransaction(() -> {
            SystemSetting fresh = new SystemSetting(SystemSetting.SINGLETON_ID);
            systemSettingDAO.update(fresh);
            return fresh;
        });
    }

    public SystemSetting updateSettings(SystemSettingsRequest req, Long adminId) {
        SystemSetting settings = getSettings();

        Map<String, String> errors = validate(req);
        if (!errors.isEmpty()) {
            throw new ValidationException("Please correct the highlighted fields.", errors);
        }

        return inTransaction(() -> {
            settings.setInstitutionName(req.institutionName().trim());
            settings.setInstitutionAddress(blankToNull(req.institutionAddress()));
            settings.setInstitutionLogoPath(blankToNull(req.institutionLogoPath()));
            settings.setSignatoryName(blankToNull(req.signatoryName()));
            settings.setSignatoryDesignation(blankToNull(req.signatoryDesignation()));
            settings.setUpdatedBy(adminId);
            systemSettingDAO.update(settings);
            return settings;
        });
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private Map<String, String> validate(SystemSettingsRequest req) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (ValidationUtil.isBlank(req.institutionName())) {
            errors.put("institutionName", "Institution name is required.");
        } else if (req.institutionName().trim().length() > 200) {
            errors.put("institutionName", "Institution name must be 200 characters or fewer.");
        }
        if (req.institutionAddress() != null && req.institutionAddress().length() > 300) {
            errors.put("institutionAddress", "Address must be 300 characters or fewer.");
        }
        if (req.institutionLogoPath() != null && req.institutionLogoPath().length() > 500) {
            errors.put("institutionLogoPath", "Logo path must be 500 characters or fewer.");
        }
        if (req.signatoryName() != null && req.signatoryName().length() > 150) {
            errors.put("signatoryName", "Signatory name must be 150 characters or fewer.");
        }
        if (req.signatoryDesignation() != null && req.signatoryDesignation().length() > 150) {
            errors.put("signatoryDesignation", "Signatory designation must be 150 characters or fewer.");
        }

        return errors;
    }

    // ---------------------------------------------------------------- transaction helper

    private interface TransactionalWork<T> {
        T run();
    }

    private <T> T inTransaction(TransactionalWork<T> work) {
        Session session = HibernateUtil.getCurrentSession();
        Transaction tx = session.beginTransaction();
        try {
            T result = work.run();
            tx.commit();
            return result;
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }
}
