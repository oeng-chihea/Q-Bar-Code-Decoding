package com.excel.reconciler.service;

import com.excel.reconciler.model.UnmatchedWaybill;
import com.excel.reconciler.model.WaybillStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Remembers waybills that were scanned but missing from the Excel table, and reports how long the current
 * upload's ones have stayed unmatched. Matched waybills are never tracked. Waybills from earlier uploads stay
 * in the store so their start date is kept, but they are only reported again when an upload contains them.
 * The store is optional: without a database this service reports itself as unavailable and
 * reconciliation carries on unchanged.
 */
@Service
public class WaybillTrackingService {
    private static final Logger log = LoggerFactory.getLogger(WaybillTrackingService.class);

    static final int MAX_WAYBILL_LENGTH = 64;

    public record TrackingResult(boolean available, int overdueAfterDays, List<UnmatchedWaybill> waybills) {
        public long overdueCount() {
            return waybills.stream().filter(w -> w.status() == WaybillStatus.OVERDUE).count();
        }
    }

    private final Optional<WaybillTrackingStore> store;
    private final int overdueAfterDays;
    private final Clock clock;

    @Autowired
    public WaybillTrackingService(Optional<WaybillTrackingStore> store,
                                  @Value("${app.waybill.overdue-days:6}") int overdueAfterDays,
                                  @Value("${app.waybill.zone:Asia/Phnom_Penh}") String zone) {
        this(store, overdueAfterDays, Clock.system(ZoneId.of(zone)));
    }

    WaybillTrackingService(Optional<WaybillTrackingStore> store, int overdueAfterDays, Clock clock) {
        this.store = store;
        this.overdueAfterDays = Math.max(0, overdueAfterDays);
        this.clock = clock;
    }

    /**
     * Records this upload's result and returns this upload's unmatched waybills, oldest first.
     * Never throws: a database problem only makes tracking unavailable for this upload.
     *
     * @param unmatchedCodes codes scanned from images that matched no Excel row
     * @param matchedCodes   codes scanned from images that matched an Excel row; these win over unmatchedCodes
     */
    public TrackingResult track(Collection<String> unmatchedCodes, Collection<String> matchedCodes,
                                String reconciliationId) {
        if (store.isEmpty()) {
            return unavailable();
        }

        try {
            LocalDate today = LocalDate.now(clock);
            Set<String> matched = clean(matchedCodes);
            Set<String> unmatched = clean(unmatchedCodes);
            unmatched.removeAll(matched);

            WaybillTrackingStore waybillStore = store.get();
            waybillStore.markMatched(matched, today);
            waybillStore.recordUnmatched(unmatched, today, reconciliationId);

            List<UnmatchedWaybill> waybills = waybillStore.findOpenByWaybillNos(unmatched).stream()
                    .map(tracked -> toWaybill(tracked, today))
                    .sorted(Comparator.comparing(UnmatchedWaybill::startDate)
                            .thenComparing(UnmatchedWaybill::waybillNo))
                    .toList();
            return new TrackingResult(true, overdueAfterDays, waybills);
        } catch (RuntimeException e) {
            log.warn("Waybill tracking skipped for this reconciliation: {}", e.getMessage());
            return unavailable();
        }
    }

    public int overdueAfterDays() {
        return overdueAfterDays;
    }

    private TrackingResult unavailable() {
        return new TrackingResult(false, overdueAfterDays, List.of());
    }

    private UnmatchedWaybill toWaybill(WaybillTrackingStore.TrackedWaybill tracked, LocalDate today) {
        long daysOpen = Math.max(0, ChronoUnit.DAYS.between(tracked.firstSeenDate(), today));
        WaybillStatus status;
        if (daysOpen > overdueAfterDays) {
            status = WaybillStatus.OVERDUE;
        } else if (daysOpen == overdueAfterDays) {
            status = WaybillStatus.DUE_TODAY;
        } else if (daysOpen == 0) {
            status = WaybillStatus.NEW;
        } else {
            status = WaybillStatus.ON_TRACK;
        }
        return new UnmatchedWaybill(
                tracked.waybillNo(),
                tracked.firstSeenDate(),
                tracked.firstSeenDate().plusDays(overdueAfterDays),
                daysOpen,
                Math.max(0, overdueAfterDays - daysOpen),
                Math.max(0, daysOpen - overdueAfterDays),
                status);
    }

    /** Trimmed, de-duplicated (case-insensitively, like the database column) and limited to what the column can hold. */
    private static Set<String> clean(Collection<String> codes) {
        Set<String> cleaned = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (codes == null) {
            return cleaned;
        }
        for (String code : codes) {
            if (code == null) {
                continue;
            }
            String trimmed = code.trim();
            if (!trimmed.isEmpty() && trimmed.length() <= MAX_WAYBILL_LENGTH) {
                cleaned.add(trimmed);
            }
        }
        return cleaned;
    }
}
