package com.yixing;

import com.yixing.pricing.PricingService;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static com.yixing.common.Values.*;
import static org.junit.jupiter.api.Assertions.*;

class PricingTest {
    final PricingService service = new PricingService();
    final Map<String, Object> rates = map("daily_rate", 200, "monthly_rate", 4000, "base_fee", 50, "mileage_rate", 2, "deposit", 1000);
    final LocalDateTime start = LocalDateTime.of(2026, 10, 1, 10, 0);
    double rent(String mode, LocalDateTime end, long mileage) { return ((Number) service.calculate(rates, mode, start, end, mileage).get("rent")).doubleValue(); }
    @Test void dailyBoundaries() { assertEquals(200, rent("DAILY", start.plusHours(24), 0)); assertEquals(400, rent("DAILY", start.plusHours(24).plusSeconds(1), 0)); assertEquals(600, rent("DAILY", start.plusHours(49), 0)); }
    @Test void mileageAndMonthly() { assertEquals(290, rent("MILEAGE", start.plusDays(1), 120)); assertEquals(5000, rent("MONTHLY", start.plusDays(35), 0)); assertEquals(4000, rent("MONTHLY", start.plusDays(30), 0)); }
    @Test void invalidInputsRejected() { assertThrows(RuntimeException.class, () -> rent("DAILY", start, 0)); assertThrows(RuntimeException.class, () -> rent("MILEAGE", start.plusHours(1), -1)); assertThrows(RuntimeException.class, () -> rent("UNKNOWN", start.plusDays(1), 0)); }
}
