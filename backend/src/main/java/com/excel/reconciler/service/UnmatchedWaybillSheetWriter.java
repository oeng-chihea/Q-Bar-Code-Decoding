package com.excel.reconciler.service;

import com.excel.reconciler.model.UnmatchedWaybill;
import com.excel.reconciler.model.WaybillStatus;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/** Adds an "Unmatched waybills" sheet to the highlighted workbook; the existing sheets are left untouched. */
@Service
public class UnmatchedWaybillSheetWriter {
    static final String SHEET_NAME = "Unmatched waybills";
    private static final int COLUMN_WIDTH = 18 * 256;

    public byte[] append(byte[] workbookBytes, List<UnmatchedWaybill> waybills, int overdueAfterDays)
            throws IOException {
        if (waybills.isEmpty()) {
            return workbookBytes;
        }

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(workbookBytes));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(uniqueSheetName(workbook));
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle overdueStyle = overdueStyle(workbook);

            String[] headers = {"Waybill Number", "Start date", "Due date", "Days open", "Over " + overdueAfterDays + " days"};
            Row headerRow = sheet.createRow(0);
            for (int column = 0; column < headers.length; column++) {
                Cell headerCell = headerRow.createCell(column);
                headerCell.setCellValue(headers[column]);
                headerCell.setCellStyle(headerStyle);
                sheet.setColumnWidth(column, COLUMN_WIDTH);
            }

            for (int index = 0; index < waybills.size(); index++) {
                UnmatchedWaybill waybill = waybills.get(index);
                Row row = sheet.createRow(index + 1);
                row.createCell(0).setCellValue(waybill.waybillNo());
                row.createCell(1).setCellValue(waybill.startDate().toString());
                row.createCell(2).setCellValue(waybill.dueDate().toString());
                row.createCell(3).setCellValue(waybill.daysOpen());
                row.createCell(4).setCellValue(overdueLabel(waybill));
                if (waybill.status() == WaybillStatus.OVERDUE) {
                    for (int column = 0; column < headers.length; column++) {
                        row.getCell(column).setCellStyle(overdueStyle);
                    }
                }
            }

            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static String overdueLabel(UnmatchedWaybill waybill) {
        return waybill.status() == WaybillStatus.OVERDUE
                ? "Yes (+" + waybill.overdueDays() + ")"
                : "No (" + waybill.daysLeft() + " days left)";
    }

    private static String uniqueSheetName(Workbook workbook) {
        String name = SHEET_NAME;
        for (int suffix = 2; workbook.getSheet(name) != null; suffix++) {
            name = SHEET_NAME + " (" + suffix + ")";
        }
        return name;
    }

    private static CellStyle headerStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private static CellStyle overdueStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setColor(IndexedColors.DARK_RED.getIndex());
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
}
