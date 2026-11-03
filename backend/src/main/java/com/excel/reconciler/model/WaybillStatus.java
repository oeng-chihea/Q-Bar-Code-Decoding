package com.excel.reconciler.model;

public enum WaybillStatus {
    /** The receipt was created today. */
    NEW,
    /** Open for fewer days than the overdue limit. */
    ON_TRACK,
    /** Open for exactly the overdue limit; it becomes overdue tomorrow. */
    DUE_TODAY,
    /** Open for more days than the overdue limit. */
    OVERDUE,
    /** The receipt's CREATED date could not be read, so the days cannot be counted. */
    NO_DATE
}
