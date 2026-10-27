package com.excel.reconciler.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

public interface ReceiptDateStore {

    record SavedDate(LocalDate date, String text) {
    }

    Map<String, SavedDate> findDates(Collection<String> waybillNos);

    void saveDates(Map<String, SavedDate> datesByWaybillNo);
}
