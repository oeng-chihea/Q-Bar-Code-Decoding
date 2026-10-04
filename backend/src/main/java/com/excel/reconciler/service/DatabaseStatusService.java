package com.excel.reconciler.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

@Service
public class DatabaseStatusService {
    private static final Logger log = LoggerFactory.getLogger(DatabaseStatusService.class);
    private static final int VALIDATION_TIMEOUT_SECONDS = 3;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DatabaseStatus(String status, String product, String version, String database, String message) {
        public static DatabaseStatus notConfigured() {
            return new DatabaseStatus("NOT_CONFIGURED", null, null, null, "MYSQL_URL is not set");
        }

        public static DatabaseStatus down(String message) {
            return new DatabaseStatus("DOWN", null, null, null, message);
        }

        @JsonIgnore
        public boolean isUp() {
            return "UP".equals(status);
        }
    }

    private final ObjectProvider<DataSource> dataSourceProvider;

    public DatabaseStatusService(ObjectProvider<DataSource> dataSourceProvider) {
        this.dataSourceProvider = dataSourceProvider;
    }

    public DatabaseStatus check() {
        DataSource dataSource = dataSourceProvider.getIfAvailable();
        if (dataSource == null) {
            return DatabaseStatus.notConfigured();
        }

        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(VALIDATION_TIMEOUT_SECONDS)) {
                return DatabaseStatus.down("Connection validation failed");
            }
            DatabaseMetaData metaData = connection.getMetaData();
            return new DatabaseStatus("UP", metaData.getDatabaseProductName(),
                    metaData.getDatabaseProductVersion(), connection.getCatalog(), null);
        } catch (Exception e) {
            return DatabaseStatus.down(rootCauseMessage(e));
        }
    }

    // Hikari wraps the real driver error (e.g. "Access denied") in a generic pool timeout
    private static String rootCauseMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logStatusOnStartup() {
        DatabaseStatus status = check();
        if (status.isUp()) {
            log.info("Connected to {} {} (database '{}')", status.product(), status.version(), status.database());
        } else if ("NOT_CONFIGURED".equals(status.status())) {
            log.info("MySQL is not configured (MYSQL_URL is empty); database features are disabled");
        } else {
            log.warn("MySQL connection failed: {}", status.message());
        }
    }
}
