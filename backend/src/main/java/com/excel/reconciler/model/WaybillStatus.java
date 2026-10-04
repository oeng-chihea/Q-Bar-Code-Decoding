package com.excel.reconciler.model;

public enum WaybillStatus {
    /** First seen unmatched today. */
    NEW,
    /** Unmatched for fewer days than the overdue limit. */
    ON_TRACK,
    /** Unmatched for exactly the overdue limit; it becomes overdue tomorrow. */
    DUE_TODAY,
    /** Unmatched for more days than the overdue limit. */
    OVERDUE
}
