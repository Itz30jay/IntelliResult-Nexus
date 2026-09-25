package com.intelliresult.nexus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A program offered by a department (e.g. "B.Tech Computer Science"). */
@Entity
@Table(name = "courses")
public class Course extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "code", nullable = false, length = 20, unique = true)
    private String code;

    @Column(name = "total_semesters", nullable = false)
    private short totalSemesters;

    protected Course() {
    }

    public Course(Department department, String name, String code, int totalSemesters) {
        this.department = department;
        this.name = name;
        this.code = code;
        this.totalSemesters = (short) totalSemesters;
    }

    public Department getDepartment() { return department; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public int getTotalSemesters() { return totalSemesters; }
    public void setTotalSemesters(int totalSemesters) { this.totalSemesters = (short) totalSemesters; }
}
