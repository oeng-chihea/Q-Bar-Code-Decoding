package com.excel.reconciler.service;

import com.excel.reconciler.model.UnmatchedWaybill;
import com.excel.reconciler.model.WaybillStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WaybillTrackingServiceTest {
    private static final ZoneId PHNOM_PENH = ZoneId.of("Asia/Phnom_Penh");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);

    private final WaybillTrackingService service = new WaybillTrackingService(6, clockOn(TODAY));

    @Test
    void countsTheDaysFromTheReceiptsCreatedDateToToday() {
        // The sample receipt: CREATED Oct 15, 2026, checked on Nov 3, 2026
        UnmatchedWaybill waybill = service.evaluate("J01422851876", LocalDate.of(2026, 10, 15), "Ah Pisey");

        assertEquals("J01422851876", waybill.waybillNo());
        assertEquals(LocalDate.of(2026, 10, 15), waybill.createDate());
        assertEquals(LocalDate.of(2026, 10, 21), waybill.dueDate());
        assertEquals(19, waybill.daysOpen());
        assertEquals(0, waybill.daysLeft());
        assertEquals(13, waybill.overdueDays());
        assertEquals(WaybillStatus.OVERDUE, waybill.status());
        assertEquals("Ah Pisey", waybill.customerName());
    }

    @Test
    void aReceiptCreatedTodayIsNew() {
        UnmatchedWaybill waybill = service.evaluate("J0001", TODAY, null);

        assertEquals(0, waybill.daysOpen());
        assertEquals(6, waybill.daysLeft());
        assertEquals(WaybillStatus.NEW, waybill.status());
    }

    @Test
    void becomesOverdueOnlyAfterMoreThanTheLimit() {
        UnmatchedWaybill onTrack = service.evaluate("J0001", TODAY.minusDays(3), null);
        UnmatchedWaybill onDueDay = service.evaluate("J0001", TODAY.minusDays(6), null);
        UnmatchedWaybill dayAfter = service.evaluate("J0001", TODAY.minusDays(7), null);

        assertEquals(WaybillStatus.ON_TRACK, onTrack.status());
        assertEquals(3, onTrack.daysLeft());
        assertEquals(WaybillStatus.DUE_TODAY, onDueDay.status());
        assertEquals(0, onDueDay.overdueDays());
        assertEquals(WaybillStatus.OVERDUE, dayAfter.status());
        assertEquals(1, dayAfter.overdueDays());
    }

    @Test
    void withoutACreatedDateTheDaysAreNotCounted() {
        UnmatchedWaybill waybill = service.evaluate("J0001", null, "Ah Pisey");

        assertNull(waybill.createDate());
        assertNull(waybill.dueDate());
        assertEquals(0, waybill.daysOpen());
        assertEquals(WaybillStatus.NO_DATE, waybill.status());
        assertEquals("Ah Pisey", waybill.customerName());
    }

    @Test
    void usesTheConfiguredTimeZoneToDecideWhatTodayIs() {
        // 20:00 UTC on Nov 2 is already Nov 3 in Cambodia (UTC+7)
        Clock lateEveningUtc = Clock.fixed(TODAY.minusDays(1).atTime(20, 0).toInstant(ZoneOffset.UTC), PHNOM_PENH);

        UnmatchedWaybill waybill = new WaybillTrackingService(6, lateEveningUtc).evaluate("J0001", TODAY, null);

        assertEquals(WaybillStatus.NEW, waybill.status());
    }

    private static Clock clockOn(LocalDate day) {
        return Clock.fixed(day.atTime(12, 0).atZone(PHNOM_PENH).toInstant(), PHNOM_PENH);
    }
}
