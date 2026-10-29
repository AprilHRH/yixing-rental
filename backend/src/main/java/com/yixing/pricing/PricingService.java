package com.yixing.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import static com.yixing.common.BusinessException.require;
import static com.yixing.common.Values.*;

@Service
public class PricingService {
    public Map<String, Object> calculate(Map<String, Object> rates, String mode, LocalDateTime start, LocalDateTime end, long mileage) {
        require(end.isAfter(start), "还车时间必须晚于取车时间");
        require(mileage >= 0 && mileage <= 1000000, "里程必须在 0 至 1000000 公里之间");
        long days = Math.max(1, (Duration.between(start, end).getSeconds() + 86399) / 86400);
        require(days <= 3650, "租期不得超过 10 年");
        BigDecimal rent;
        String formula;
        switch (mode) {
            case "DAILY" -> { rent = money(rates, "daily_rate").multiply(BigDecimal.valueOf(days)); formula = days + " 天 × ¥" + rates.get("daily_rate"); }
            case "MILEAGE" -> { rent = money(rates, "base_fee").add(money(rates, "mileage_rate").multiply(BigDecimal.valueOf(mileage))); formula = "起租 ¥" + rates.get("base_fee") + " + " + mileage + " km × ¥" + rates.get("mileage_rate"); }
            case "MONTHLY" -> { rent = money(rates, "monthly_rate").multiply(BigDecimal.valueOf(days / 30)).add(money(rates, "daily_rate").multiply(BigDecimal.valueOf(days % 30))); formula = days / 30 + " 月 × ¥" + rates.get("monthly_rate") + " + " + days % 30 + " 天 × ¥" + rates.get("daily_rate"); }
            default -> throw new com.yixing.common.BusinessException("不支持的计费方式");
        }
        return map("days", days, "mileage", mileage, "rent", rent.setScale(2, RoundingMode.HALF_UP), "deposit", money(rates, "deposit"), "formula", formula);
    }
}
