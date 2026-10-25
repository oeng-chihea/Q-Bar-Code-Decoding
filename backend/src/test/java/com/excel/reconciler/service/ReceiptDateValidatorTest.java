package com.excel.reconciler.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceiptDateValidatorTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 25);

    @Test
    void readsTheReceiptCreatedFormatAndDropsTheTime() {
        ReceiptDateValidator.Result result = ReceiptDateValidator.validate("Oct 18, 2026, 02:29 PM", TODAY);

        assertTrue(result.isValid());
        assertEquals(LocalDate.of(2026, 10, 18), result.date());
    }

    @Test
    void acceptsOtherUnambiguousFormats() {
        assertEquals(LocalDate.of(2026, 10, 18), ReceiptDateValidator.validate("18 October 2026 14:29", TODAY).date());
        assertEquals(LocalDate.of(2026, 9, 3), ReceiptDateValidator.validate("Sept 3, 2026", TODAY).date());
        assertEquals(LocalDate.of(2026, 10, 18), ReceiptDateValidator.validate("2026-10-18 14:29:00", TODAY).date());
    }

    @Test
    void acceptsTodayButRejectsFutureDates() {
        assertTrue(ReceiptDateValidator.validate("Oct 25, 2026, 08:00 AM", TODAY).isValid());

        ReceiptDateValidator.Result future = ReceiptDateValidator.validate("Oct 26, 2026, 08:00 AM", TODAY);
        assertFalse(future.isValid());
        assertEquals(ReceiptDateValidator.Problem.FUTURE_DATE, future.problem());
    }

    @Test
    void reportsMissingDates() {
        assertEquals(ReceiptDateValidator.Problem.NOT_FOUND, ReceiptDateValidator.validate("", TODAY).problem());
        assertEquals(ReceiptDateValidator.Problem.NOT_FOUND, ReceiptDateValidator.validate("   ", TODAY).problem());
        assertEquals(ReceiptDateValidator.Problem.NOT_FOUND, ReceiptDateValidator.validate(null, TODAY).problem());
    }

    @Test
    void reportsTextThatIsNotARealDate() {
        for (String text : new String[]{"Feb 31, 2026", "Foo 18, 2026", "Created today", "2026-13-01"}) {
            ReceiptDateValidator.Result result = ReceiptDateValidator.validate(text, TODAY);
            assertEquals(ReceiptDateValidator.Problem.UNREADABLE, result.problem(), text);
            assertEquals(text, result.rawText());
        }
    }
}
