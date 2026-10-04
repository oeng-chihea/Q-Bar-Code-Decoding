package com.excel.reconciler.controller;

import com.excel.reconciler.service.DatabaseStatusService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DatabaseHealthController {

    private final DatabaseStatusService databaseStatusService;

    public DatabaseHealthController(DatabaseStatusService databaseStatusService) {
        this.databaseStatusService = databaseStatusService;
    }

    @GetMapping("/health/database")
    public ResponseEntity<DatabaseStatusService.DatabaseStatus> database() {
        DatabaseStatusService.DatabaseStatus status = databaseStatusService.check();
        HttpStatus httpStatus = "DOWN".equals(status.status()) ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK;
        return ResponseEntity.status(httpStatus).body(status);
    }
}
