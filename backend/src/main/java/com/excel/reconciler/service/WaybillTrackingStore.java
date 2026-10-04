package com.excel.reconciler.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Persistence for waybills that were scanned but not found in the Excel table.
 * Dates are supplied by the caller so day counting never depends on the database server's time zone.
 */
public interface WaybillTrackingStore {

    record TrackedWaybill(String waybillNo, LocalDate firstSeenDate) {
    }

    /**
     * Starts tracking new waybills, refreshes ones already open (their start date is kept),
     * and reopens previously matched ones with a fresh start date.
     */
    void recordUnmatched(Collection<String> waybillNos, LocalDate today, String reconciliationId);

    /** Stops tracking open waybills that are now matched. Waybills that were never tracked are ignored. */
    void markMatched(Collection<String> waybillNos, LocalDate today);

    /** Open waybills, oldest first, up to {@code limit}. */
    List<TrackedWaybill> findOpen(int limit);

    /** Open waybills among the given numbers. */
    List<TrackedWaybill> findOpenByWaybillNos(Collection<String> waybillNos);
}
