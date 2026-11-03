package com.excel.reconciler.model;

import java.time.LocalDate;

/**
 * A scanned waybill that has no matching row in the Excel table, together with how long it has been open.
 *
 * @param createDate    the CREATED date printed on the waybill's receipt, or null when it could not be read
 * @param dueDate       createDate plus the overdue limit, or null without a createDate
 * @param daysOpen      whole days from createDate to today
 * @param daysLeft      days remaining before it becomes overdue (0 once due or overdue)
 * @param overdueDays   days past the overdue limit (0 while not overdue)
 * @param customerName  the customer's name read from the waybill's receipt, or null when unknown
 */
public record UnmatchedWaybill(
        String waybillNo,
        LocalDate createDate,
        LocalDate dueDate,
        long daysOpen,
        long daysLeft,
        long overdueDays,
        WaybillStatus status,
        String customerName) {
}
