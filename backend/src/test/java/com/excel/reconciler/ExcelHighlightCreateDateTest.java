package com.excel.reconciler;

import com.excel.reconciler.model.ExcelRowPreview;
import com.excel.reconciler.service.ExcelHighlightService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExcelHighlightCreateDateTest {

    private byte[] workbook() throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Inventory");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Waybill Number");
            header.createCell(1).setCellValue("Create Date");
            String[][] rows = {{"J01400726571", "2026-10-25"}, {"J01400110265", "2026-10-25"}};
            for (int i = 0; i < rows.length; i++) {
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(rows[i][0]);
                row.createCell(1).setCellValue(rows[i][1]);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    public void matchedRowTakesTheReceiptDateAndOthersKeepTheirs() throws Exception {
        ExcelHighlightService service = new ExcelHighlightService();

        ExcelHighlightService.ExcelProcessingResult result = service.highlightMatches(
                new ByteArrayInputStream(workbook()), Set.of("J01400726571"), "Waybill Number", false,
                Map.of("J01400726571", "2026-10-18"));

        ExcelRowPreview matched = result.getPreviewRows().get(0);
        ExcelRowPreview other = result.getPreviewRows().get(1);
        assertEquals("2026-10-18", matched.getCells().get("Create Date"));
        assertEquals("2026-10-25", other.getCells().get("Create Date"));

        try (Workbook saved = new XSSFWorkbook(new ByteArrayInputStream(result.getModifiedExcelBytes()))) {
            assertEquals("2026-10-18", saved.getSheetAt(0).getRow(1).getCell(1).getStringCellValue());
            assertEquals("2026-10-25", saved.getSheetAt(0).getRow(2).getCell(1).getStringCellValue());
        }
    }

    @Test
    public void withoutReceiptDatesTheCreateDateIsUntouched() throws Exception {
        ExcelHighlightService service = new ExcelHighlightService();

        ExcelHighlightService.ExcelProcessingResult result = service.highlightMatches(
                new ByteArrayInputStream(workbook()), Set.of("J01400726571"), "Waybill Number", false);

        assertEquals("2026-10-25", result.getPreviewRows().get(0).getCells().get("Create Date"));
    }
}
