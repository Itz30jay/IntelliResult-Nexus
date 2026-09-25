package com.intelliresult.nexus.exception;

import java.util.Collections;
import java.util.List;

/**
 * Thrown by the bulk Excel import pipeline (Phase 13) when one or more rows
 * fail validation. Carries every row-level error found - not just the first
 * one - so the import summary screen can list "Row 14: marks exceed maximum",
 * "Row 22: duplicate student ID" etc. in a single pass, rather than making
 * the user fix one error, re-upload, and discover the next. Sec. 21 requires
 * that invalid data is never partially inserted, so a Service catching this
 * always has an already-rolled-back transaction by the time it reaches here.
 */
public class DataImportException extends BaseApplicationException {

    private final List<String> rowErrors;

    public DataImportException(String message, List<String> rowErrors) {
        super(message);
        this.rowErrors = rowErrors == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(rowErrors);
    }

    public DataImportException(String message, List<String> rowErrors, Throwable cause) {
        super(message, cause);
        this.rowErrors = rowErrors == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(rowErrors);
    }

    /** Never null; one human-readable entry per rejected row, e.g. "Row 14: marks exceed subject maximum (105 > 100)". */
    public List<String> getRowErrors() {
        return rowErrors;
    }
}
