package com.intelliresult.nexus.entity;

import com.intelliresult.nexus.entity.enums.SectionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** A cohort subdivision within a running semester (e.g. "Section A"). */
@Entity
@Table(name = "sections", uniqueConstraints = @UniqueConstraint(columnNames = {"semester_id", "name"}))
public class Section extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    @Column(name = "name", nullable = false, length = 10)
    private String name;

    @Column(name = "capacity")
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "section_type", nullable = false, length = 20)
    private SectionType sectionType = SectionType.PERMANENT;

    protected Section() {
    }

    public Section(Semester semester, String name) {
        this.semester = semester;
        this.name = name;
    }

    public Semester getSemester() { return semester; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public SectionType getSectionType() { return sectionType; }
    public void setSectionType(SectionType sectionType) { this.sectionType = sectionType; }
}
