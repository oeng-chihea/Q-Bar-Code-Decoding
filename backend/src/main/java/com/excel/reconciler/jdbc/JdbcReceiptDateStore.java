package com.excel.reconciler.jdbc;

import com.excel.reconciler.service.ReceiptDateStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JdbcReceiptDateStore implements ReceiptDateStore {
    private static final int BATCH_SIZE = 100;

    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS receipt_created_date (
                waybill_no VARCHAR(64) NOT NULL PRIMARY KEY,
                created_date DATE NOT NULL,
                created_text VARCHAR(100) NOT NULL,
                saved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """;

    private static final String INSERT_SQL = """
            INSERT INTO receipt_created_date (waybill_no, created_date, created_text)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE waybill_no = waybill_no
            """;

    private final JdbcTemplate jdbcTemplate;
    private volatile boolean tableReady;

    public JdbcReceiptDateStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, SavedDate> findDates(Collection<String> waybillNos) {
        if (waybillNos.isEmpty()) {
            return Map.of();
        }
        ensureTable();
        String placeholders = String.join(", ", Collections.nCopies(waybillNos.size(), "?"));
        String sql = "SELECT waybill_no, created_date, created_text FROM receipt_created_date "
                + "WHERE waybill_no IN (" + placeholders + ")";
        Map<String, SavedDate> dates = new LinkedHashMap<>();
        jdbcTemplate.query(sql, rs -> {
            dates.put(rs.getString("waybill_no"), new SavedDate(
                    rs.getObject("created_date", LocalDate.class), rs.getString("created_text")));
        }, waybillNos.toArray());
        return dates;
    }

    @Override
    public void saveDates(Map<String, SavedDate> datesByWaybillNo) {
        if (datesByWaybillNo.isEmpty()) {
            return;
        }
        ensureTable();
        List<Map.Entry<String, SavedDate>> entries = List.copyOf(datesByWaybillNo.entrySet());
        jdbcTemplate.batchUpdate(INSERT_SQL, entries, BATCH_SIZE, (ps, entry) -> {
            ps.setString(1, entry.getKey());
            ps.setObject(2, entry.getValue().date());
            ps.setString(3, truncate(entry.getValue().text(), 100));
        });
    }

    private void ensureTable() {
        if (!tableReady) {
            jdbcTemplate.execute(CREATE_TABLE_SQL);
            tableReady = true;
        }
    }

    private static String truncate(String text, int max) {
        String value = text == null ? "" : text;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
