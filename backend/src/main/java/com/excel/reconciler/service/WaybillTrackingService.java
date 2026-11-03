package com.excel.reconciler.service;

import com.excel.reconciler.model.UnmatchedWaybill;
import com.excel.reconciler.model.WaybillStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Works out how long an unmatched serial number has been open: today minus the CREATED date printed on its
 * receipt. It becomes overdue once that is more than the configured number of days.
 */
@Service
public class WaybillTrackingService {
    private final int overdueAfterDays;
    private final Clock clock;

    @Autowired
    public WaybillTrackingService(@Value("${app.waybill.overdue-days:6}") int overdueAfterDays,
                                  @Value("${app.waybill.zone:Asia/Phnom_Penh}") String zone) {
        this(overdueAfterDays, Clock.system(ZoneId.of(zone)));
    }

    WaybillTrackingService(int overdueAfterDays, Clock clock) {
        this.overdueAfterDays = Math.max(0, overdueAfterDays);
        this.clock = clock;
    }

    public int overdueAfterDays() {
        return overdueAfterDays;
    }

    /** @param createDate the receipt's CREATED date, or null when it could not be read */
    public UnmatchedWaybill evaluate(String waybillNo, LocalDate createDate, String customerName) {
        if (createDate == null) {
            return new UnmatchedWaybill(waybillNo, null, null, 0, 0, 0, WaybillStatus.NO_DATE, customerName);
        }
        long daysOpen = Math.max(0, ChronoUnit.DAYS.between(createDate, LocalDate.now(clock)));
        WaybillStatus status;
        if (daysOpen > overdueAfterDays) {
            status = WaybillStatus.OVERDUE;
        } else if (daysOpen == overdueAfterDays) {
            status = WaybillStatus.DUE_TODAY;
        } else if (daysOpen == 0) {
            status = WaybillStatus.NEW;
        } else {
            status = WaybillStatus.ON_TRACK;
        }
        return new UnmatchedWaybill(
                waybillNo,
                createDate,
                createDate.plusDays(overdueAfterDays),
                daysOpen,
                Math.max(0, overdueAfterDays - daysOpen),
                Math.max(0, daysOpen - overdueAfterDays),
                status,
                customerName);
    }
}
