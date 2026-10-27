package com.excel.reconciler.jdbc;

import com.excel.reconciler.service.ReceiptDetailStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JdbcReceiptDetailStore implements ReceiptDetailStore {
    private static final int BATCH_SIZE = 100;
    private static final int MAX_CUSTOMER_NAME_LENGTH = 255;
    private static final int MAX_CREATED_TEXT_LENGTH = 100;

    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS receipt_detail (
                serial_number VARCHAR(64) NOT NULL PRIMARY KEY,
                customer_name VARCHAR(255) NULL,
                created_date DATE NULL,
                created_text VARCHAR(100) NULL,
                saved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            ) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
            """;

    private static final String OLD_TABLE_EXISTS_SQL = """
            SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema = DATABASE() AND table_name = 'receipt_created_date'
            """;

    // Dates saved before customer names were read; their names stay null so they are read once more
    private static final String COPY_OLD_DATES_SQL = """
            INSERT INTO receipt_detail (serial_number, created_date, created_text, saved_at)
            SELECT waybill_no, created_date, created_text, saved_at FROM receipt_created_date
            ON DUPLICATE KEY UPDATE serial_number = serial_number
            """;

    // The first value saved wins; a later read only fills in what is still missing
    private static final String UPSERT_SQL = """
            INSERT INTO receipt_detail (serial_number, customer_name, created_date, created_text)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                customer_name = COALESCE(customer_name, VALUES(customer_name)),
                created_text = IF(created_date IS NULL, VALUES(created_text), created_text),
                created_date = COALESCE(created_date, VALUES(created_date))
            """;

    private final JdbcTemplate jdbcTemplate;
    private volatile boolean tableReady;

    public JdbcReceiptDetailStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, SavedReceipt> findReceipts(Collection<String> serialNumbers) {
        if (serialNumbers.isEmpty()) {
            return Map.of();
        }
        ensureTable();
        String placeholders = String.join(", ", Collections.nCopies(serialNumbers.size(), "?"));
        String sql = "SELECT serial_number, customer_name, created_date, created_text FROM receipt_detail "
                + "WHERE serial_number IN (" + placeholders + ")";
        Map<String, SavedReceipt> receipts = new LinkedHashMap<>();
        jdbcTemplate.query(sql, rs -> {
            receipts.put(rs.getString("serial_number"), new SavedReceipt(
                    rs.getObject("created_date", LocalDate.class),
                    rs.getString("created_text"),
                    rs.getString("customer_name")));
        }, serialNumbers.toArray());
        return receipts;
    }

    @Override
    public void saveReceipts(Map<String, SavedReceipt> receiptsBySerialNumber) {
        if (receiptsBySerialNumber.isEmpty()) {
            return;
        }
        ensureTable();
        List<Map.Entry<String, SavedReceipt>> entries = List.copyOf(receiptsBySerialNumber.entrySet());
        jdbcTemplate.batchUpdate(UPSERT_SQL, entries, BATCH_SIZE, (ps, entry) -> {
            SavedReceipt receipt = entry.getValue();
            ps.setString(1, entry.getKey());
            ps.setString(2, truncate(receipt.customerName(), MAX_CUSTOMER_NAME_LENGTH));
            ps.setObject(3, receipt.date());
            ps.setString(4, receipt.date() == null ? null : truncate(receipt.text(), MAX_CREATED_TEXT_LENGTH));
        });
    }

    private void ensureTable() {
        if (!tableReady) {
            jdbcTemplate.execute(CREATE_TABLE_SQL);
            Integer oldTables = jdbcTemplate.queryForObject(OLD_TABLE_EXISTS_SQL, Integer.class);
            if (oldTables != null && oldTables > 0) {
                jdbcTemplate.update(COPY_OLD_DATES_SQL);
            }
            tableReady = true;
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
