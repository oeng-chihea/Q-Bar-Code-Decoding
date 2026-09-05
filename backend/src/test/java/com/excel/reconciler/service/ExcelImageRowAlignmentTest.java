package com.excel.reconciler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExcelImageRowAlignmentTest {

    @Test
    void restoresTheBlankReturnColumnWhenImageExtractionShiftsFinancialValues() {
        ExcelImageExtractorService service = new ExcelImageExtractorService(null, new ObjectMapper());
        List<String> headers = List.of(
                "Number", "Waybill Number", "Create Time", "Shipping Outlets",
                "Amount payable to customers", "Commission", "Retrun", "COD",
                "client's name", "Customer ID", "POD Outlets"
        );
        List<List<String>> rows = List.of(List.of(
                "1", "J01400726571", "2026-08-26 11:01:45", "សាខាសៀមរាប/REPDP01",
                "", "25,000", "300", "24,700", "OENG JIHO", "J0086007386", "សាខាតាខ្មៅ/BBMDP01"
        ));

        List<String> aligned = service.normalizeExtractedRows(headers, rows).get(0);

        assertEquals("25,000", aligned.get(4));
        assertEquals("300", aligned.get(5));
        assertEquals("", aligned.get(6));
        assertEquals("24,700", aligned.get(7));
        assertEquals("OENG JIHO", aligned.get(8));
        assertEquals("J0086007386", aligned.get(9));
        assertEquals("សាខាតាខ្មៅ/BBMDP01", aligned.get(10));
    }
}
