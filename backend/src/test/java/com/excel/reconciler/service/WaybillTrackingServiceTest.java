package com.excel.reconciler.service;

import com.excel.reconciler.model.UnmatchedWaybill;
import com.excel.reconciler.model.WaybillStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaybillTrackingServiceTest {
    private static final ZoneId PHNOM_PENH = ZoneId.of("Asia/Phnom_Penh");
    private static final LocalDate DAY_ONE = LocalDate.of(2026, 10, 1);

    private final InMemoryStore store = new InMemoryStore();

    @Test
    void newUnmatchedWaybillStartsToday() {
        var result = trackOn(DAY_ONE, Set.of("J0001"), Set.of());

        assertTrue(result.available());
        UnmatchedWaybill waybill = onlyWaybill(result);
        assertEquals("J0001", waybill.waybillNo());
        assertEquals(DAY_ONE, waybill.startDate());
        assertEquals(DAY_ONE.plusDays(6), waybill.dueDate());
        assertEquals(0, waybill.daysOpen());
        assertEquals(6, waybill.daysLeft());
        assertEquals(WaybillStatus.NEW, waybill.status());
        assertTrue(waybill.inThisUpload());
    }

    @Test
    void startDateNeverChangesWhenTheSameWaybillIsSeenAgain() {
        trackOn(DAY_ONE, Set.of("J0001"), Set.of());

        UnmatchedWaybill waybill = onlyWaybill(trackOn(DAY_ONE.plusDays(3), Set.of("J0001"), Set.of()));

        assertEquals(DAY_ONE, waybill.startDate());
        assertEquals(3, waybill.daysOpen());
        assertEquals(3, waybill.daysLeft());
        assertEquals(WaybillStatus.ON_TRACK, waybill.status());
    }

    @Test
    void becomesOverdueOnlyAfterMoreThanTheLimit() {
        trackOn(DAY_ONE, Set.of("J0001"), Set.of());

        UnmatchedWaybill onDueDay = onlyWaybill(trackOn(DAY_ONE.plusDays(6), Set.of(), Set.of()));
        UnmatchedWaybill dayAfter = onlyWaybill(trackOn(DAY_ONE.plusDays(7), Set.of(), Set.of()));

        assertEquals(WaybillStatus.DUE_TODAY, onDueDay.status());
        assertEquals(0, onDueDay.overdueDays());
        assertEquals(WaybillStatus.OVERDUE, dayAfter.status());
        assertEquals(1, dayAfter.overdueDays());
        assertEquals(0, dayAfter.daysLeft());
    }

    @Test
    void countsOverdueWaybillsFromEarlierUploads() {
        trackOn(DAY_ONE, Set.of("OLD-1", "OLD-2"), Set.of());

        var result = trackOn(DAY_ONE.plusDays(10), Set.of("NEW-1"), Set.of());

        assertEquals(2, result.overdueCount());
        assertEquals(3, result.waybills().size());
        assertFalse(find(result, "OLD-1").inThisUpload());
        assertTrue(find(result, "NEW-1").inThisUpload());
    }

    @Test
    void matchedWaybillStopsBeingTracked() {
        trackOn(DAY_ONE, Set.of("J0001", "J0002"), Set.of());

        var result = trackOn(DAY_ONE.plusDays(2), Set.of(), Set.of("J0001"));

        assertEquals(List.of("J0002"), result.waybills().stream().map(UnmatchedWaybill::waybillNo).toList());
    }

    @Test
    void waybillThatIsUnmatchedAgainAfterBeingMatchedRestartsTheCount() {
        trackOn(DAY_ONE, Set.of("J0001"), Set.of());
        trackOn(DAY_ONE.plusDays(2), Set.of(), Set.of("J0001"));

        UnmatchedWaybill waybill = onlyWaybill(trackOn(DAY_ONE.plusDays(20), Set.of("J0001"), Set.of()));

        assertEquals(DAY_ONE.plusDays(20), waybill.startDate());
        assertEquals(WaybillStatus.NEW, waybill.status());
    }

    @Test
    void matchedWinsWhenTheSameWaybillIsBothMatchedAndUnmatchedInOneUpload() {
        var result = trackOn(DAY_ONE, Set.of("J0001"), Set.of("j0001"));

        assertTrue(result.available());
        assertTrue(result.waybills().isEmpty());
    }

    @Test
    void matchedWaybillsThatWereNeverTrackedAreIgnored() {
        var result = trackOn(DAY_ONE, Set.of(), Set.of("J0001"));

        assertTrue(result.available());
        assertTrue(result.waybills().isEmpty());
    }

    @Test
    void treatsWaybillNumbersCaseInsensitivelyAndIgnoresBlankOrOversizedValues() {
        var result = trackOn(DAY_ONE, List.of("j0001", "J0001", "  J0001  ", " ", "", "X".repeat(65)), Set.of());

        assertEquals(1, result.waybills().size());
        assertEquals("j0001", onlyWaybill(result).waybillNo());
    }

    @Test
    void listsTheCurrentUploadEvenWhenOlderOpenWaybillsFillTheCap() {
        List<String> older = new ArrayList<>();
        for (int i = 0; i < WaybillTrackingService.MAX_OPEN_WAYBILLS; i++) {
            older.add("OLD-%04d".formatted(i));
        }
        trackOn(DAY_ONE, older, Set.of());

        var result = trackOn(DAY_ONE.plusDays(1), Set.of("FRESH-1"), Set.of());

        assertTrue(find(result, "FRESH-1").inThisUpload());
    }

    @Test
    void reportsUnavailableWithoutADatabase() {
        var service = new WaybillTrackingService(Optional.empty(), 6, clockOn(DAY_ONE));

        var result = service.track(Set.of("J0001"), Set.of(), "rec-1");

        assertFalse(result.available());
        assertTrue(result.waybills().isEmpty());
        assertEquals(0, result.overdueCount());
    }

    @Test
    void reportsUnavailableInsteadOfFailingWhenTheDatabaseErrors() {
        WaybillTrackingStore failing = new InMemoryStore() {
            @Override
            public void recordUnmatched(Collection<String> waybillNos, LocalDate today, String reconciliationId) {
                throw new IllegalStateException("connection lost");
            }
        };
        var service = new WaybillTrackingService(Optional.of(failing), 6, clockOn(DAY_ONE));

        var result = service.track(Set.of("J0001"), Set.of(), "rec-1");

        assertFalse(result.available());
    }

    @Test
    void usesTheConfiguredTimeZoneToDecideWhatTodayIs() {
        // 20:00 UTC on Oct 1 is already Oct 2 in Cambodia (UTC+7)
        Clock lateEveningUtc = Clock.fixed(
                DAY_ONE.atTime(20, 0).toInstant(ZoneOffset.UTC), PHNOM_PENH);
        var service = new WaybillTrackingService(Optional.of(store), 6, lateEveningUtc);

        UnmatchedWaybill waybill = onlyWaybill(service.track(Set.of("J0001"), Set.of(), "rec-1"));

        assertEquals(DAY_ONE.plusDays(1), waybill.startDate());
    }

    private WaybillTrackingService.TrackingResult trackOn(LocalDate day, Collection<String> unmatched,
                                                          Collection<String> matched) {
        return new WaybillTrackingService(Optional.of(store), 6, clockOn(day)).track(unmatched, matched, "rec-1");
    }

    private static Clock clockOn(LocalDate day) {
        return Clock.fixed(day.atTime(12, 0).atZone(PHNOM_PENH).toInstant(), PHNOM_PENH);
    }

    private static UnmatchedWaybill onlyWaybill(WaybillTrackingService.TrackingResult result) {
        assertEquals(1, result.waybills().size());
        return result.waybills().get(0);
    }

    private static UnmatchedWaybill find(WaybillTrackingService.TrackingResult result, String waybillNo) {
        return result.waybills().stream()
                .filter(w -> w.waybillNo().equalsIgnoreCase(waybillNo))
                .findFirst()
                .orElseThrow();
    }

    /** Mirrors the SQL semantics of {@link JdbcWaybillTrackingStore}. */
    private static class InMemoryStore implements WaybillTrackingStore {
        private record Entry(String waybillNo, LocalDate firstSeen, boolean open) {
        }

        private final Map<String, Entry> entries = new LinkedHashMap<>();

        @Override
        public void recordUnmatched(Collection<String> waybillNos, LocalDate today, String reconciliationId) {
            for (String waybillNo : waybillNos) {
                Entry existing = entries.get(key(waybillNo));
                if (existing == null || !existing.open()) {
                    entries.put(key(waybillNo), new Entry(existing == null ? waybillNo : existing.waybillNo(), today, true));
                }
            }
        }

        @Override
        public void markMatched(Collection<String> waybillNos, LocalDate today) {
            for (String waybillNo : waybillNos) {
                Entry existing = entries.get(key(waybillNo));
                if (existing != null && existing.open()) {
                    entries.put(key(waybillNo), new Entry(existing.waybillNo(), existing.firstSeen(), false));
                }
            }
        }

        @Override
        public List<TrackedWaybill> findOpen(int limit) {
            return entries.values().stream()
                    .filter(Entry::open)
                    .sorted(java.util.Comparator.comparing(Entry::firstSeen))
                    .limit(limit)
                    .map(e -> new TrackedWaybill(e.waybillNo(), e.firstSeen()))
                    .toList();
        }

        @Override
        public List<TrackedWaybill> findOpenByWaybillNos(Collection<String> waybillNos) {
            return waybillNos.stream()
                    .map(w -> entries.get(key(w)))
                    .filter(e -> e != null && e.open())
                    .map(e -> new TrackedWaybill(e.waybillNo(), e.firstSeen()))
                    .toList();
        }

        private static String key(String waybillNo) {
            return waybillNo.toLowerCase(Locale.ROOT);
        }
    }
}
