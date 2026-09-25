package com.intelliresult.nexus.service.dto;

import com.intelliresult.nexus.entity.Result;
import com.intelliresult.nexus.entity.Student;
import com.intelliresult.nexus.entity.enums.ResultStatus;

/**
 * One row of the marks-entry grid (Sec. 29). {@code existingResult} is
 * {@code null} when this student has no Result row for this exam+subject
 * yet - the normal state before a teacher has entered anything, not an
 * error. Built by loading every student in the section first
 * (StudentDAO.findBySection) and only then cross-referencing existing
 * Results, rather than the other way around - the section's roster is the
 * source of truth for "who should have a row in this grid," not whichever
 * students happen to already have a Result.
 */
public record MarksEntryRow(Student student, Result existingResult) {

    public boolean hasExistingResult() {
        return existingResult != null;
    }

    /** A row is editable while it has no result yet, or while its result is still DRAFT - matches Sec. 10's "SUBMITTED: normal editing restricted" exactly. */
    public boolean isEditable() {
        return existingResult == null || existingResult.getStatus() == ResultStatus.DRAFT;
    }
}
