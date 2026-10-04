package com.excel.reconciler.service;

import com.excel.reconciler.model.UnmatchedWaybill;
import com.excel.reconciler.model.WaybillStatus;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UnmatchedWaybillSheetWriterTest {
    private final UnmatchedWaybillSheetWriter writer = new UnmatchedWaybillSheetWriter();

    private final UnmatchedWaybill overdue = new UnmatchedWaybill("J0001", LocalDate.of(2026, 9, 20),
            LocalDate.of(2026, 9, 26), 15, 0, 9, WaybillStatus.OVERDUE, false);
    private final UnmatchedWaybill onTrack = new UnmatchedWaybill("J0002", LocalDate.of(2026, 10, 3),
            LocalDate.of(2026, 10, 9), 2, 4, 0, WaybillStatus.ON_TRACK, true);

    @Test
    void addsASheetWithOneRowPerWaybillAndKeepsTheOriginalSheet() throws Exception {
        byte[] result = writer.append(workbookWithSheet("Sheet1"), List.of(overdue, onTrack), 6);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(result))) {
            assertEquals(2, workbook.getNumberOfSheets());
            assertEquals("Sheet1", workbook.getSheetAt(0).getSheetName());
            assertEquals("original", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());

            Sheet sheet = workbook.getSheet("Unmatched waybills");
            assertEquals("Waybill Number", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("Over 6 days", sheet.getRow(0).getCell(4).getStringCellValue());

            assertEquals("J0001", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("2026-09-20", sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals("2026-09-26", sheet.getRow(1).getCell(2).getStringCellValue());
            assertEquals(15, sheet.getRow(1).getCell(3).getNumericCellValue());
            assertEquals("Yes (+9)", sheet.getRow(1).getCell(4).getStringCellValue());
            assertEquals(IndexedColors.ROSE.getIndex(),
                    sheet.getRow(1).getCell(0).getCellStyle().getFillForegroundColor());

            assertEquals("J0002", sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals("No (4 days left)", sheet.getRow(2).getCell(4).getStringCellValue());
            assertEquals(IndexedColors.AUTOMATIC.getIndex(),
                    sheet.getRow(2).getCell(0).getCellStyle().getFillForegroundColor());
        }
    }

    @Test
    void leavesTheWorkbookUntouchedWhenThereAreNoWaybills() throws Exception {
        byte[] original = workbookWithSheet("Sheet1");

        assertArrayEquals(original, writer.append(original, List.of(), 6));
    }

    @Test
    void picksAnUnusedSheetNameWhenTheDefaultIsTaken() throws Exception {
        byte[] result = writer.append(workbookWithSheet("Unmatched waybills"), List.of(overdue), 6);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(result))) {
            assertEquals("Unmatched waybills", workbook.getSheetAt(0).getSheetName());
            assertEquals("Unmatched waybills (2)", workbook.getSheetAt(1).getSheetName());
        }
    }

    private static byte[] workbookWithSheet(String name) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.createSheet(name).createRow(0).createCell(0).setCellValue("original");
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
