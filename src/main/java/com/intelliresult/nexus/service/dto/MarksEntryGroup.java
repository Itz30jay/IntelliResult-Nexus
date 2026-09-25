package com.intelliresult.nexus.service.dto;

import com.intelliresult.nexus.entity.Exam;
import com.intelliresult.nexus.entity.TeacherSubject;

/**
 * One (exam, subject, section) combination's marks-entry progress for a
 * given teacher. A single shared computation
 * (MarksEntryService.listGroupsForTeacher) produces the full set across
 * every active assignment and every exam in its semester; each of the
 * three screens that need this data filters differently rather than
 * running its own query - the Marks Entry picker wants exams still open
 * for entry, Draft Results wants {@code draftCount > 0}, Submitted Results
 * wants {@code submittedOrLaterCount > 0}. See
 * PHASE6B-MARKS-ENTRY.md for why groups with zero results for
 * an exam that hasn't opened yet (CREATED/SCHEDULED) aren't specially
 * excluded here - they naturally have draftCount = submittedOrLaterCount =
 * 0 and every filtered view already skips those.
 */
public record MarksEntryGroup(
        Exam exam,
        TeacherSubject assignment,
        int totalStudents,
        int draftCount,
        int submittedOrLaterCount
) {
    public int enteredCount() {
        return draftCount + submittedOrLaterCount;
    }
}
