package com.excel.reconciler.model;

import java.time.LocalDate;

/**
 * A scanned waybill that has no matching row in the Excel table, together with how long it has been open.
 *
 * @param startDate     first day the waybill was seen unmatched
 * @param dueDate       startDate plus the overdue limit
 * @param daysOpen      whole days since startDate
 * @param daysLeft      days remaining before it becomes overdue (0 once due or overdue)
 * @param overdueDays   days past the overdue limit (0 while not overdue)
 * @param inThisUpload  true when the current upload contains this waybill unmatched
 */
public record UnmatchedWaybill(
        String waybillNo,
        LocalDate startDate,
        LocalDate dueDate,
        long daysOpen,
        long daysLeft,
        long overdueDays,
        WaybillStatus status,
        boolean inThisUpload) {
}
