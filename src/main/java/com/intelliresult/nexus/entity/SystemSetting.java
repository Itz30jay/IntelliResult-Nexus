package com.intelliresult.nexus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Sec. 52's System Settings - deliberately not a subclass of BaseEntity.
 * BaseEntity's {@code @GeneratedValue(strategy = GenerationType.IDENTITY)}
 * is exactly wrong here: this table has exactly one row, forever, at a
 * fixed id (see {@link #SINGLETON_ID} and schema.sql's
 * {@code chk_system_settings_singleton} constraint), never auto-assigned.
 * Scope is deliberately narrower than every bullet in Sec. 52's list -
 * institution identity and marksheet-branding fields only. See
 * schema.sql's own note on system_settings for exactly which settings were
 * left out and why (already represented elsewhere, or gated behind a
 * feature that doesn't exist yet).
 * <p>
 * updatedBy is a plain {@code Long}, not a {@code @ManyToOne}, for the
 * identical reason {@link SoftDeletableEntity#getDeletedBy()} is: written
 * once per save and read only inside the settings admin screen itself,
 * not a relationship anything else needs to traverse.
 */
@Entity
@Table(name = "system_settings")
public class SystemSetting {

    /** The only id this table's one row ever has. */
    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(name = "institution_name", nullable = false, length = 200)
    private String institutionName;

    @Column(name = "institution_address", length = 300)
    private String institutionAddress;

    @Column(name = "institution_logo_path", length = 500)
    private String institutionLogoPath;

    @Column(name = "signatory_name", length = 150)
    private String signatoryName;

    @Column(name = "signatory_designation", length = 150)
    private String signatoryDesignation;

    @Column(name = "updated_by")
    private Long updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SystemSetting() {
    }

    /**
     * Only ever called from SystemSettingsService.getSettings() the first
     * time it finds no row yet (a fresh database with schema.sql but not
     * seed-data.sql) - always with {@link #SINGLETON_ID}. The default
     * institution name here matches seed-data.sql's own placeholder value,
     * so a freshly self-healed row and a seeded one start from the same
     * baseline either way.
     */
    public SystemSetting(Long id) {
        this.id = id;
        this.institutionName = "IntelliResult Nexus Institute";
    }

    public Long getId() {
        return id;
    }

    public String getInstitutionName() {
        return institutionName;
    }

    public void setInstitutionName(String institutionName) {
        this.institutionName = institutionName;
    }

    public String getInstitutionAddress() {
        return institutionAddress;
    }

    public void setInstitutionAddress(String institutionAddress) {
        this.institutionAddress = institutionAddress;
    }

    public String getInstitutionLogoPath() {
        return institutionLogoPath;
    }

    public void setInstitutionLogoPath(String institutionLogoPath) {
        this.institutionLogoPath = institutionLogoPath;
    }

    public String getSignatoryName() {
        return signatoryName;
    }

    public void setSignatoryName(String signatoryName) {
        this.signatoryName = signatoryName;
    }

    public String getSignatoryDesignation() {
        return signatoryDesignation;
    }

    public void setSignatoryDesignation(String signatoryDesignation) {
        this.signatoryDesignation = signatoryDesignation;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
