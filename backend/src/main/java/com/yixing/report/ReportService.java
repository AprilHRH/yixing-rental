package com.yixing.report;

import com.yixing.data.Store;
import com.yixing.maintenance.MaintenanceService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.yixing.common.BusinessException.require;
import static com.yixing.common.Values.*;

@Service
public class ReportService {
    private final Store store; private final MaintenanceService maintenance;
    public ReportService(Store store, MaintenanceService maintenance) { this.store = store; this.maintenance = maintenance; }
    public Map<String, Object> report(String from, String to) {
        LocalDate startDate, endDate;
        try { startDate = LocalDate.parse(from); endDate = LocalDate.parse(to); }
        catch (Exception exception) { throw new com.yixing.common.BusinessException("统计日期格式不正确"); }
        require(!endDate.isBefore(startDate) && Duration.between(startDate.atStartOfDay(), endDate.atStartOfDay()).toDays() <= 366, "统计区间须为 1 至 367 天");
        LocalDateTime start = startDate.atStartOfDay(), end = endDate.plusDays(1).atStartOfDay();
        List<Map<String, Object>> orders = store.list("rental_order"), vehicles = store.list("vehicle"), work = store.list("maintenance_record");
        Map<String, Map<String, Object>> trend = new LinkedHashMap<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) trend.put(date.toString(), map("date", date.toString(), "revenue", BigDecimal.ZERO, "orders", 0));
        BigDecimal revenue = BigDecimal.ZERO, costs = BigDecimal.ZERO;
        long created = 0, completed = 0, cancelled = 0;
        Map<Long, Map<String, Object>> usage = new LinkedHashMap<>();
        for (Map<String, Object> vehicle : vehicles) usage.put(number(vehicle, "id"), map("vehicle_id", vehicle.get("id"), "plate", vehicle.get("plate"), "brand", vehicle.get("brand"), "model", vehicle.get("model"), "count", 0L, "hours", 0.0, "revenue", BigDecimal.ZERO));
        for (Map<String, Object> order : orders) {
            if (inside(order, "created_at", start, end)) {
                created++; String day = text(order, "created_at").substring(0, 10); Map<String, Object> point = trend.get(day); point.put("orders", ((Number) point.get("orders")).intValue() + 1);
            }
            if ("COMPLETED".equals(text(order, "status")) && inside(order, "completed_at", start, end)) {
                completed++; BigDecimal amount = money(order, "actual_rent"); revenue = revenue.add(amount);
                Map<String, Object> vehicleUsage = usage.get(number(order, "vehicle_id"));
                vehicleUsage.put("revenue", ((BigDecimal) vehicleUsage.get("revenue")).add(amount));
                Map<String, Object> point = trend.get(text(order, "completed_at").substring(0, 10)); point.put("revenue", ((BigDecimal) point.get("revenue")).add(amount));
            }
            if ("CANCELLED".equals(text(order, "status")) && inside(order, "cancelled_at", start, end)) cancelled++;
            if (!text(order, "actual_start").isBlank()) {
                Map<String, Object> item = usage.get(number(order, "vehicle_id"));
                if (inside(order, "actual_start", start, end)) item.put("count", ((Number) item.get("count")).longValue() + 1);
                LocalDateTime pickup = time(order, "actual_start");
                LocalDateTime returned = text(order, "actual_end").isBlank() ? LocalDateTime.now() : time(order, "actual_end");
                LocalDateTime clippedStart = pickup.isAfter(start) ? pickup : start, clippedEnd = returned.isBefore(end) ? returned : end;
                if (clippedEnd.isAfter(clippedStart)) item.put("hours", ((Number) item.get("hours")).doubleValue() + Duration.between(clippedStart, clippedEnd).toSeconds() / 3600.0);
            }
        }
        for (Map<String, Object> task : work) if ("COMPLETED".equals(text(task, "status")) && inside(task, "completed_at", start, end)) costs = costs.add(money(task, "cost"));
        Map<String, Long> statuses = new LinkedHashMap<>();
        for (String status : List.of("AVAILABLE", "RENTED", "MAINTENANCE", "DISPATCHING", "DISABLED")) statuses.put(status, vehicles.stream().filter(vehicle -> status.equals(text(vehicle, "status"))).count());
        List<Map<String, Object>> ranked = usage.values().stream().sorted(Comparator.comparingDouble((Map<String, Object> value) -> ((Number) value.get("hours")).doubleValue()).reversed()).toList();
        return map("from", from, "to", to, "created", created, "completed", completed, "cancelled", cancelled, "revenue", revenue, "maintenance_cost", costs, "trend", trend.values(), "vehicle_statuses", statuses, "usage", ranked, "vehicle_count", vehicles.size(), "customer_count", store.list("customer").size(), "reminders", maintenance.reminders(), "recent_orders", orders.stream().limit(6).toList());
    }
    private boolean inside(Map<String, Object> row, String field, LocalDateTime start, LocalDateTime end) {
        if (text(row, field).isBlank()) return false; LocalDateTime value = time(row, field); return !value.isBefore(start) && value.isBefore(end);
    }
}
