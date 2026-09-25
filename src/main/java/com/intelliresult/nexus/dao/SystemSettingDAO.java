package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.SystemSetting;

/**
 * Deliberately not {@code extends GenericDAO<SystemSetting, Long>}. Every
 * other DAO in this package models a collection - find one of many, list
 * all, delete one - and {@link SystemSetting} isn't a collection, it's a
 * single fixed row. Forcing it through the generic contract would mean
 * {@code findAll()} returning a one-element list nothing should ever
 * iterate, and a {@code delete()} nothing should ever call. A narrower,
 * honest interface beats reusing the wrong abstraction for the sake of
 * reuse.
 */
public interface SystemSettingDAO {

    /** The one row, or {@code null} if it hasn't been created yet - a fresh database with schema.sql but not seed-data.sql. SystemSettingsService.getSettings() is the only caller that needs to handle the null case; every other caller goes through that service method, never this DAO directly. */
    SystemSetting get();

    /** Upsert by design, not just update: {@code merge()} on an id that doesn't exist yet inserts it. This is what lets SystemSettingsService self-heal a missing row without a separate create() method. */
    void update(SystemSetting settings);
}
