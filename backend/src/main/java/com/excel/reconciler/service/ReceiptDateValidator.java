package com.excel.reconciler.service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the text printed next to "CREATED" on a receipt (for example "Oct 18, 2026, 02:29 PM") into a
 * calendar date and rejects anything that cannot be trusted. The time of day is ignored.
 */
public final class ReceiptDateValidator {
    public enum Problem {
        /** The receipt has no CREATED date, or the model could not read one. */
        NOT_FOUND,
        /** Text was read but it is not a real calendar date. */
        UNREADABLE,
        /** The date is after today, which a receipt cannot be. */
        FUTURE_DATE
    }

    public record Result(String rawText, LocalDate date, Problem problem) {
        public boolean isValid() {
            return problem == null && date != null;
        }
    }

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("jan", 1), Map.entry("feb", 2), Map.entry("mar", 3), Map.entry("apr", 4),
            Map.entry("may", 5), Map.entry("jun", 6), Map.entry("jul", 7), Map.entry("aug", 8),
            Map.entry("sep", 9), Map.entry("oct", 10), Map.entry("nov", 11), Map.entry("dec", 12));

    // Oct 18, 2026 / October 18 2026
    private static final Pattern MONTH_FIRST = Pattern.compile("\\b([A-Za-z]{3,9})\\.?\\s+(\\d{1,2}),?\\s+(\\d{4})\\b");
    // 18 Oct 2026
    private static final Pattern DAY_FIRST = Pattern.compile("\\b(\\d{1,2})\\s+([A-Za-z]{3,9})\\.?,?\\s+(\\d{4})\\b");
    // 2026-10-18
    private static final Pattern ISO = Pattern.compile("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b");

    private ReceiptDateValidator() {
    }

    public static Result validate(String rawText, LocalDate today) {
        String text = rawText == null ? "" : rawText.replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) {
            return new Result("", null, Problem.NOT_FOUND);
        }

        LocalDate date;
        try {
            date = parse(text);
        } catch (DateTimeException e) {
            return new Result(text, null, Problem.UNREADABLE);
        }
        if (date == null) {
            return new Result(text, null, Problem.UNREADABLE);
        }
        if (date.isAfter(today)) {
            return new Result(text, date, Problem.FUTURE_DATE);
        }
        return new Result(text, date, null);
    }

    private static LocalDate parse(String text) {
        Matcher m = MONTH_FIRST.matcher(text);
        if (m.find()) {
            return build(m.group(3), month(m.group(1)), m.group(2));
        }
        m = DAY_FIRST.matcher(text);
        if (m.find()) {
            return build(m.group(3), month(m.group(2)), m.group(1));
        }
        m = ISO.matcher(text);
        if (m.find()) {
            return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        }
        return null;
    }

    private static Integer month(String name) {
        return MONTHS.get(name.substring(0, 3).toLowerCase(Locale.ROOT));
    }

    private static LocalDate build(String year, Integer month, String day) {
        if (month == null) {
            return null;
        }
        return LocalDate.of(Integer.parseInt(year), month, Integer.parseInt(day));
    }
}
