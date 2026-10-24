package com.excel.reconciler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExcelCreateDateTest {
    private static final ZoneId PHNOM_PENH = ZoneId.of("Asia/Phnom_Penh");

    private ExcelImageExtractorService serviceAt(String instant) {
        return new ExcelImageExtractorService(null, new ObjectMapper(),
                Clock.fixed(Instant.parse(instant), PHNOM_PENH));
    }

    @Test
    void replacesTheImageCreateTimeWithTheUploadDate() {
        List<String> headers = List.of("Number", "Waybill Number", "Create Time", "COD");
        List<List<String>> rows = List.of(
                List.of("1", "J01400726571", "2026-08-26 11:01:45", "24,700"),
                List.of("2", "J01400110265", "2026-08-28 16:06:15", "24,700"));

        ExcelImageExtractorService.Table table = serviceAt("2026-10-25T05:00:00Z").applyCreateDate(headers, rows);

        assertEquals(List.of("Number", "Waybill Number", "Create Date", "COD"), table.headers());
        assertEquals(List.of("1", "J01400726571", "2026-10-25", "24,700"), table.rows().get(0));
        assertEquals(List.of("2", "J01400110265", "2026-10-25", "24,700"), table.rows().get(1));
    }

    @Test
    void usesTheDayInPhnomPenhNotUtc() {
        // 20:00 UTC on the 25th is already 03:00 on the 26th in Cambodia (UTC+7)
        ExcelImageExtractorService.Table table = serviceAt("2026-10-25T20:00:00Z").applyCreateDate(
                List.of("Waybill Number", "Create Time"), List.of(List.of("J01", "2026-08-26 11:01:45")));

        assertEquals("2026-10-26", table.rows().get(0).get(1));
    }

    @Test
    void leavesTablesWithoutACreationColumnAndBlankRowsAlone() {
        ExcelImageExtractorService service = serviceAt("2026-10-25T05:00:00Z");

        List<String> noDate = List.of("Waybill Number", "COD");
        List<List<String>> rows = List.of(List.of("J01", "100"));
        assertEquals(new ExcelImageExtractorService.Table(noDate, rows), service.applyCreateDate(noDate, rows));

        ExcelImageExtractorService.Table withBlank = service.applyCreateDate(
                List.of("Waybill Number", "Create Time"),
                List.of(List.of("J01", "x"), List.of("", "")));
        assertEquals(List.of("", ""), withBlank.rows().get(1));
    }
}
