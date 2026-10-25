package com.excel.reconciler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExcelCreateDateTest {
    private final ExcelImageExtractorService service = new ExcelImageExtractorService(null, new ObjectMapper());

    @Test
    void renamesTheCreationColumnAndLeavesItEmptyInsteadOfUsingTheUploadDate() {
        List<String> headers = List.of("Number", "Waybill Number", "Create Time", "COD");
        List<List<String>> rows = List.of(
                List.of("1", "J01400726571", "2026-08-26 11:01:45", "24,700"),
                List.of("2", "J01400110265", "2026-08-28 16:06:15", "24,700"));

        ExcelImageExtractorService.Table table = service.applyCreateDate(headers, rows);

        assertEquals(List.of("Number", "Waybill Number", "Create Date", "COD"), table.headers());
        assertEquals(List.of("1", "J01400726571", "", "24,700"), table.rows().get(0));
        assertEquals(List.of("2", "J01400110265", "", "24,700"), table.rows().get(1));
    }

    @Test
    void leavesTablesWithoutACreationColumnAlone() {
        List<String> noDate = List.of("Waybill Number", "COD");
        List<List<String>> rows = List.of(List.of("J01", "100"));

        assertEquals(new ExcelImageExtractorService.Table(noDate, rows), service.applyCreateDate(noDate, rows));
    }
}
