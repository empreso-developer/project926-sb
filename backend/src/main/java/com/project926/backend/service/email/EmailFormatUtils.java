package com.project926.backend.service.email;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Java port of the three lib/utils.ts functions the existing ticket
 * confirmation email template actually uses: formatCurrency, formatDate,
 * formatTime. Ported field-for-field rather than approximated, since these
 * values appear verbatim in the email a customer receives.
 */
public final class EmailFormatUtils {

    private static final Map<String, String> CURRENCY_SYMBOLS = Map.of(
        "INR", "₹",
        "USD", "$",
        "EUR", "€",
        "GBP", "£"
    );

    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.forLanguageTag("en-IN"));

    private EmailFormatUtils() {
    }

    /** Mirrors formatCurrency(amount, currency): symbol + en-IN grouped, 2 decimal places. */
    public static String formatCurrency(BigDecimal amount, String currency) {
        String symbol = CURRENCY_SYMBOLS.getOrDefault(currency, "");
        NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"));
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return symbol + format.format(amount);
    }

    /** Mirrors formatDate(date): en-IN, "EEE, d MMM yyyy" e.g. "Tue, 1 Dec 2026". */
    public static String formatDate(LocalDate date) {
        return date.format(DATE_FORMATTER);
    }

    /**
     * Mirrors formatTime(time) exactly — a manual 12-hour conversion, not a
     * locale-formatted one, since the existing JS builds the "AM"/"PM"
     * suffix as a literal uppercase string regardless of locale.
     */
    public static String formatTime(LocalTime time) {
        int hour = time.getHour();
        String period = hour >= 12 ? "PM" : "AM";
        int displayHour = hour % 12 == 0 ? 12 : hour % 12;
        return String.format("%d:%02d %s", displayHour, time.getMinute(), period);
    }
}
