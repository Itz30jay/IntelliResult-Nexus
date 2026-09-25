package com.intelliresult.nexus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Top-level academic organizational unit (e.g. "Computer Science"). No @OneToMany back to Course/Subject/Teacher - see Phase 3 notes on why child collections are deliberately avoided in favor of DAO query methods. */
@Entity
@Table(name = "departments")
public class Department extends SoftDeletableEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "code", nullable = false, length = 20, unique = true)
    private String code;

    protected Department() {
        // JPA requires a no-arg constructor for proxy/reflection instantiation.
    }

    public Department(String name, String code) {
        this.name = name;
        this.code = code;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
