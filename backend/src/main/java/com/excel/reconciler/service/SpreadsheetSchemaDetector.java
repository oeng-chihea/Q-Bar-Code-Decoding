package com.excel.reconciler.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Finds the table header and identifier columns from both labels and values.
 *
 * Header labels are intentionally weak hints. The strongest signal is the
 * overlap between scanned identifiers and values in a column, followed by the
 * shape and consistency of values in that column. This allows Khmer, English,
 * or previously unseen labels to work without translating uploaded data.
 */
public class SpreadsheetSchemaDetector {
    private static final int MAX_HEADER_ROWS_TO_SCAN = 40;
    private static final int MAX_DATA_ROWS_TO_SAMPLE = 40;

    private static final List<String> IDENTIFIER_ALIASES = List.of(
            "barcode", "qrcode", "sku", "waybill", "tracking", "awb", "upc", "ean",
            "itemcode", "itemid", "productid", "លេខបាកូដ", "លេខកូដ", "កូដទំនិញ", "បាកូដ",
            "លេខqr", "លេខbarcode", "លេខsku"
    );

    private static final List<String> NON_IDENTIFIER_ALIASES = List.of(
            "productname", "itemname", "description", "category", "price", "cost",
            "quantity", "qty", "outlet", "address", "customer", "name"
    );

    public Detection detect(Sheet sheet, DataFormatter formatter, Set<String> decodedCodes,
                            String preferredColumnName) {
        if (sheet == null || formatter == null || sheet.getPhysicalNumberOfRows() == 0) {
            return null;
        }

        Set<String> canonicalDecodedCodes = new LinkedHashSet<>();
        if (decodedCodes != null) {
            for (String code : decodedCodes) {
                String canonical = IdentifierCanonicalizer.canonicalize(code);
                if (!canonical.isEmpty()) {
                    canonicalDecodedCodes.add(canonical);
                }
            }
        }

        String canonicalPreferred = IdentifierCanonicalizer.canonicalize(preferredColumnName);
        Detection best = null;
        int maxRows = Math.min(MAX_HEADER_ROWS_TO_SCAN, sheet.getLastRowNum() + 1);

        for (int rowIndex = 0; rowIndex < maxRows; rowIndex++) {
            Row headerRow = sheet.getRow(rowIndex);
            if (headerRow == null || headerRow.getLastCellNum() <= 0) {
                continue;
            }

            List<String> headers = readHeaders(headerRow, formatter);
            int nonEmptyHeaders = (int) headers.stream().filter(value -> !value.isBlank()).count();
            if (nonEmptyHeaders < 1 || looksLikeDataRow(headers)) {
                continue;
            }

            List<ColumnEvidence> evidence = new ArrayList<>();
            for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
                evidence.add(scoreColumn(sheet, rowIndex, columnIndex, headers.get(columnIndex),
                        formatter, canonicalDecodedCodes, canonicalPreferred));
            }
            evidence.sort(Comparator.comparingDouble(ColumnEvidence::score).reversed()
                    .thenComparingInt(ColumnEvidence::columnIndex));

            if (evidence.isEmpty()) {
                continue;
            }

            ColumnEvidence top = evidence.get(0);
            double secondScore = evidence.size() > 1 ? evidence.get(1).score() : 0.0;
            double rowScore = top.score() + (nonEmptyHeaders * 2.0);
            Detection candidate = toDetection(rowIndex, headers, evidence, rowScore, secondScore);

            if (best == null || candidate.score() > best.score()) {
                best = candidate;
            }
        }

        return best;
    }

    private Detection toDetection(int headerRowNum, List<String> headers,
                                  List<ColumnEvidence> rankedEvidence,
                                  double rowScore, double secondScore) {
        ColumnEvidence top = rankedEvidence.get(0);
        List<Integer> identifierColumns = new ArrayList<>();
        double selectionThreshold = Math.max(1.0, top.score() * 0.85);

        for (ColumnEvidence item : rankedEvidence) {
            boolean hasIdentifierEvidence = item.exactMatches() > 0 || item.variantMatches() > 0
                    || item.identifierRate() >= 0.50 || item.identifierAlias();
            if (hasIdentifierEvidence && item.score() >= selectionThreshold) {
                identifierColumns.add(item.columnIndex());
            }
        }
        if (identifierColumns.isEmpty()) {
            identifierColumns.add(top.columnIndex());
        }

        Map<Integer, Double> columnScores = new LinkedHashMap<>();
        for (ColumnEvidence item : rankedEvidence) {
            columnScores.put(item.columnIndex(), round(item.score()));
        }

        double evidenceConfidence = Math.min(1.0, top.score() / 150.0);
        double separationConfidence = rankedEvidence.size() == 1
                ? 1.0
                : Math.min(1.0, Math.max(0.0, (top.score() - secondScore) / 80.0));
        double confidence = Math.min(0.99, 0.55 + (evidenceConfidence * 0.30)
                + (separationConfidence * 0.15));

        return new Detection(
                headerRowNum,
                List.copyOf(identifierColumns),
                headers.get(top.columnIndex()),
                List.copyOf(headers),
                round(rowScore),
                round(confidence),
                Map.copyOf(columnScores)
        );
    }

    private ColumnEvidence scoreColumn(Sheet sheet, int headerRowNum, int columnIndex,
                                        String header, DataFormatter formatter,
                                        Set<String> decodedCodes, String preferredColumn) {
        int nonBlank = 0;
        int exactMatches = 0;
        int variantMatches = 0;
        int identifierValues = 0;
        Set<String> uniqueValues = new HashSet<>();
        int dataEnd = Math.min(sheet.getLastRowNum(), headerRowNum + MAX_DATA_ROWS_TO_SAMPLE);

        for (int rowIndex = headerRowNum + 1; rowIndex <= dataEnd; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            String raw = formatter.formatCellValue(cell).trim();
            String canonical = IdentifierCanonicalizer.canonicalize(raw);
            if (canonical.isEmpty()) {
                continue;
            }

            nonBlank++;
            uniqueValues.add(canonical);
            if (looksLikeIdentifier(canonical)) {
                identifierValues++;
            }
            if (decodedCodes.contains(canonical)) {
                exactMatches++;
            } else if (matchesIdentifierVariant(canonical, decodedCodes)) {
                variantMatches++;
            }
        }

        double identifierRate = nonBlank == 0 ? 0.0 : (double) identifierValues / nonBlank;
        double uniquenessRate = nonBlank == 0 ? 0.0 : (double) uniqueValues.size() / nonBlank;
        String canonicalHeader = IdentifierCanonicalizer.canonicalize(header);
        boolean identifierAlias = containsAlias(canonicalHeader, IDENTIFIER_ALIASES);
        boolean nonIdentifierAlias = containsAlias(canonicalHeader, NON_IDENTIFIER_ALIASES);
        boolean preferredAlias = !preferredColumn.isEmpty() && canonicalHeader.equals(preferredColumn);

        // Data shape is the main language-independent signal. Header aliases
        // and the optional preferred label only break close ties.
        double score = (exactMatches * 40.0)
                + (variantMatches * 25.0)
                + (identifierRate * 100.0)
                + (uniquenessRate * 10.0)
                + (identifierAlias ? 30.0 : 0.0)
                + (preferredAlias ? 20.0 : 0.0)
                - (nonIdentifierAlias && !identifierAlias ? 12.0 : 0.0);

        return new ColumnEvidence(columnIndex, score, exactMatches, variantMatches,
                identifierRate, identifierAlias);
    }

    private List<String> readHeaders(Row row, DataFormatter formatter) {
        List<String> headers = new ArrayList<>();
        for (int columnIndex = 0; columnIndex < row.getLastCellNum(); columnIndex++) {
            Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            String value = formatter.formatCellValue(cell);
            boolean formulaLike = cell.getCellType() == org.apache.poi.ss.usermodel.CellType.FORMULA
                    || value.startsWith("=") || value.contains("(") || value.contains("!");
            headers.add(value.isBlank() || formulaLike ? "Column " + (columnIndex + 1) : value);
        }
        return headers;
    }

    private boolean looksLikeDataRow(List<String> values) {
        int nonEmpty = 0;
        int numeric = 0;
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            nonEmpty++;
            String canonical = IdentifierCanonicalizer.canonicalize(value);
            if (canonical.matches("\\d+(?:\\.\\d+)?")) {
                numeric++;
            }
        }
        return nonEmpty > 0 && numeric == nonEmpty;
    }

    private boolean looksLikeIdentifier(String canonical) {
        if (canonical == null || canonical.length() < 4 || canonical.length() > 50) {
            return false;
        }
        if (canonical.matches("\\d{4,32}")) {
            return true;
        }
        return canonical.matches("[a-z0-9./#]+") && canonical.matches(".*\\d.*");
    }

    private boolean matchesIdentifierVariant(String canonical, Set<String> decodedCodes) {
        if (canonical == null || canonical.isEmpty()) {
            return false;
        }
        for (String decoded : decodedCodes) {
            if (canonical.equals(decoded)) {
                return true;
            }
            if (canonical.matches("\\d+") && decoded.matches("\\d+")) {
                String left = canonical.replaceFirst("^0+", "");
                String right = decoded.replaceFirst("^0+", "");
                if (!left.isEmpty() && left.equals(right)) {
                    return true;
                }
                if ((canonical.length() == 11 && decoded.length() == 12)
                        || (canonical.length() == 12 && decoded.length() == 11)) {
                    if (canonical.endsWith(decoded) || decoded.endsWith(canonical)
                            || canonical.startsWith(decoded) || decoded.startsWith(canonical)) {
                        return true;
                    }
                }
                if ((canonical.length() == 12 && decoded.length() == 13)
                        || (canonical.length() == 13 && decoded.length() == 12)) {
                    if ((canonical.length() == 13 && canonical.equals("0" + decoded))
                            || (decoded.length() == 13 && decoded.equals("0" + canonical))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean containsAlias(String header, List<String> aliases) {
        if (header == null || header.isEmpty()) {
            return false;
        }
        for (String alias : aliases) {
            if (header.contains(alias.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private record ColumnEvidence(int columnIndex, double score, int exactMatches,
                                  int variantMatches, double identifierRate,
                                  boolean identifierAlias) {
    }

    public record Detection(int headerRowNum, List<Integer> identifierColumnIndexes,
                            String resolvedColumnName, List<String> headers,
                            double score, double confidence,
                            Map<Integer, Double> columnScores) {
    }
}
