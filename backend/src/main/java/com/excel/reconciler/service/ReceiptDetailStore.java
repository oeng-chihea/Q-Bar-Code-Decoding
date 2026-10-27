package com.excel.reconciler.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/** What was read from each receipt, keyed by its serial number (the waybill number). */
public interface ReceiptDetailStore {

    /**
     * @param date         the CREATED date, or null when the receipt had no valid one
     * @param customerName the customer's name; "" when the receipt prints none, null when it was never read
     */
    record SavedReceipt(LocalDate date, String text, String customerName) {

        /** Both details are known, so the receipt does not need to be read again. */
        public boolean isComplete() {
            return date != null && customerName != null;
        }
    }

    Map<String, SavedReceipt> findReceipts(Collection<String> serialNumbers);

    void saveReceipts(Map<String, SavedReceipt> receiptsBySerialNumber);
}
