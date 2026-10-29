package com.yixing.web;

import com.yixing.auth.Access;
import com.yixing.customer.CustomerService;
import com.yixing.data.Store;
import com.yixing.dispatch.DispatchService;
import com.yixing.maintenance.MaintenanceService;
import com.yixing.order.RentalService;
import com.yixing.report.ReportService;
import com.yixing.system.SystemService;
import java.time.LocalDate;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import static com.yixing.common.Values.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final Store store; private final com.yixing.vehicle.VehicleService vehicles; private final CustomerService customers; private final RentalService rentals; private final DispatchService dispatch; private final MaintenanceService maintenance; private final ReportService reports; private final SystemService system; private final Access access;
    public ApiController(Store store, com.yixing.vehicle.VehicleService vehicles, CustomerService customers, RentalService rentals, DispatchService dispatch, MaintenanceService maintenance, ReportService reports, SystemService system, Access access) {
        this.store = store; this.vehicles = vehicles; this.customers = customers; this.rentals = rentals; this.dispatch = dispatch; this.maintenance = maintenance; this.reports = reports; this.system = system; this.access = access;
    }
    @GetMapping("/bootstrap") public Map<String, Object> bootstrap() {
        return map("vehicles", store.list("vehicle"), "customers", store.list("customer"), "orders", store.list("rental_order"), "payments", store.list("payment_record"), "branches", store.list("branch"), "pricing", store.list("pricing_rule"), "dispatch", store.list("dispatch_task"), "maintenance", store.list("maintenance_record"), "reminders", maintenance.reminders(), "settings", store.get("settings", 1));
    }
    @GetMapping("/dashboard") public Map<String, Object> dashboard() {
        Map<String, Object> result = reports.report(LocalDate.now().withDayOfMonth(1).toString(), LocalDate.now().toString());
        if (!"ADMIN".equals(text(access.user(), "role"))) { result.remove("revenue"); result.remove("maintenance_cost"); result.remove("trend"); }
        return result;
    }
    @PostMapping("/vehicles") public Map<String, Object> vehicle(@RequestBody Map<String, Object> input) { return vehicles.save(null, input); }
    @PutMapping("/vehicles/{id}") public Map<String, Object> vehicle(@PathVariable long id, @RequestBody Map<String, Object> input) { return vehicles.save(id, input); }
    @PostMapping("/vehicles/{id}/toggle") public Map<String, Object> toggle(@PathVariable long id) { return vehicles.toggle(id); }
    @PostMapping("/customers") public Map<String, Object> customer(@RequestBody Map<String, Object> input) { return customers.save(null, input); }
    @PutMapping("/customers/{id}") public Map<String, Object> customer(@PathVariable long id, @RequestBody Map<String, Object> input) { return customers.save(id, input); }
    @PostMapping("/pricing/quote") public Map<String, Object> quote(@RequestBody Map<String, Object> input) { return rentals.quote(input); }
    @PostMapping("/orders") public Map<String, Object> order(@RequestBody Map<String, Object> input) { return rentals.save(null, input); }
    @PutMapping("/orders/{id}") public Map<String, Object> order(@PathVariable long id, @RequestBody Map<String, Object> input) { return rentals.save(id, input); }
    @PostMapping("/orders/{id}/{action}") public Map<String, Object> orderAction(@PathVariable long id, @PathVariable String action, @RequestBody Map<String, Object> input) {
        return switch (action) { case "collect" -> rentals.collect(id, input); case "pickup" -> rentals.pickup(id, input); case "return" -> rentals.returned(id, input); case "settle" -> rentals.settle(id, input); case "cancel" -> rentals.cancel(id, input); default -> throw new com.yixing.common.BusinessException("不支持的订单操作"); };
    }
    @PostMapping("/dispatch") public Map<String, Object> dispatch(@RequestBody Map<String, Object> input) { return dispatch.create(input); }
    @PostMapping("/dispatch/{id}/{action}") public Map<String, Object> dispatch(@PathVariable long id, @PathVariable String action) { return dispatch.action(id, action); }
    @PostMapping("/maintenance") public Map<String, Object> maintenance(@RequestBody Map<String, Object> input) { return maintenance.create(input); }
    @PostMapping("/maintenance/{id}/{action}") public Map<String, Object> maintenance(@PathVariable long id, @PathVariable String action, @RequestBody Map<String, Object> input) { return maintenance.action(id, action, input); }
    @GetMapping("/reports") public Map<String, Object> reports(@RequestParam String from, @RequestParam String to) { return reports.report(from, to); }
    @GetMapping("/system") public Map<String, Object> system() { return system.overview(); }
    @PostMapping("/system/users") public Map<String, Object> user(@RequestBody Map<String, Object> input) { return system.user(null, input); }
    @PutMapping("/system/users/{id}") public Map<String, Object> user(@PathVariable long id, @RequestBody Map<String, Object> input) { return system.user(id, input); }
    @PutMapping("/system/pricing/{id}") public Map<String, Object> pricing(@PathVariable long id, @RequestBody Map<String, Object> input) { return system.pricing(id, input); }
    @PostMapping("/system/branches") public Map<String, Object> branch(@RequestBody Map<String, Object> input) { return system.branch(null, input); }
    @PutMapping("/system/branches/{id}") public Map<String, Object> branch(@PathVariable long id, @RequestBody Map<String, Object> input) { return system.branch(id, input); }
    @PutMapping("/system/settings") public Map<String, Object> settings(@RequestBody Map<String, Object> input) { return system.settings(input); }
}
