package com.excel.reconciler.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * MySQL/TiDB implementation backed by the {@code unmatched_waybill} table.
 * Each statement is a plain, portable SQL statement so behaviour is identical on MySQL and TiDB.
 */
public class JdbcWaybillTrackingStore implements WaybillTrackingStore {
    private static final int BATCH_SIZE = 100;

    // The no-op update only exists to skip rows that are already tracked, without hiding other errors.
    private static final String INSERT_NEW_SQL = """
            INSERT INTO unmatched_waybill (waybill_no, first_seen_date, last_seen_date, status, last_reconciliation_id)
            VALUES (?, ?, ?, 'OPEN', ?)
            ON DUPLICATE KEY UPDATE id = id
            """;

    private static final String REFRESH_OPEN_SQL = """
            UPDATE unmatched_waybill
            SET last_seen_date = ?, last_reconciliation_id = ?
            WHERE waybill_no = ? AND status = 'OPEN'
            """;

    private static final String REOPEN_MATCHED_SQL = """
            UPDATE unmatched_waybill
            SET status = 'OPEN', first_seen_date = ?, last_seen_date = ?, matched_date = NULL,
                last_reconciliation_id = ?
            WHERE waybill_no = ? AND status = 'MATCHED'
            """;

    private static final String MARK_MATCHED_SQL = """
            UPDATE unmatched_waybill
            SET status = 'MATCHED', matched_date = ?
            WHERE waybill_no = ? AND status = 'OPEN'
            """;

    private static final String SELECT_OPEN_SQL = """
            SELECT waybill_no, first_seen_date
            FROM unmatched_waybill
            WHERE status = 'OPEN'
            ORDER BY first_seen_date, id
            LIMIT ?
            """;

    private static final RowMapper<TrackedWaybill> TRACKED_WAYBILL_MAPPER = (rs, rowNum) ->
            new TrackedWaybill(rs.getString("waybill_no"), rs.getObject("first_seen_date", LocalDate.class));

    private final JdbcTemplate jdbcTemplate;

    public JdbcWaybillTrackingStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void recordUnmatched(Collection<String> waybillNos, LocalDate today, String reconciliationId) {
        if (waybillNos.isEmpty()) {
            return;
        }
        List<String> numbers = List.copyOf(waybillNos);

        jdbcTemplate.batchUpdate(INSERT_NEW_SQL, numbers, BATCH_SIZE, (ps, waybillNo) -> {
            ps.setString(1, waybillNo);
            ps.setObject(2, today);
            ps.setObject(3, today);
            ps.setString(4, reconciliationId);
        });
        jdbcTemplate.batchUpdate(REFRESH_OPEN_SQL, numbers, BATCH_SIZE, (ps, waybillNo) -> {
            ps.setObject(1, today);
            ps.setString(2, reconciliationId);
            ps.setString(3, waybillNo);
        });
        jdbcTemplate.batchUpdate(REOPEN_MATCHED_SQL, numbers, BATCH_SIZE, (ps, waybillNo) -> {
            ps.setObject(1, today);
            ps.setObject(2, today);
            ps.setString(3, reconciliationId);
            ps.setString(4, waybillNo);
        });
    }

    @Override
    public void markMatched(Collection<String> waybillNos, LocalDate today) {
        if (waybillNos.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(MARK_MATCHED_SQL, List.copyOf(waybillNos), BATCH_SIZE, (ps, waybillNo) -> {
            ps.setObject(1, today);
            ps.setString(2, waybillNo);
        });
    }

    @Override
    public List<TrackedWaybill> findOpen(int limit) {
        return jdbcTemplate.query(SELECT_OPEN_SQL, TRACKED_WAYBILL_MAPPER, limit);
    }

    @Override
    public List<TrackedWaybill> findOpenByWaybillNos(Collection<String> waybillNos) {
        if (waybillNos.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(", ", Collections.nCopies(waybillNos.size(), "?"));
        String sql = "SELECT waybill_no, first_seen_date FROM unmatched_waybill "
                + "WHERE status = 'OPEN' AND waybill_no IN (" + placeholders + ")";
        return jdbcTemplate.query(sql, TRACKED_WAYBILL_MAPPER, waybillNos.toArray());
    }
}
