package com.yixing.customer;

import com.yixing.auth.Access;
import com.yixing.data.Store;
import com.yixing.order.RentalService;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static com.yixing.common.BusinessException.require;
import static com.yixing.common.Values.*;

@RestController
@RequestMapping("/api/portal")
public class PortalController {
    private final Store store;
    private final Access access;
    private final RentalService rentals;
    public PortalController(Store store, Access access, RentalService rentals) { this.store = store; this.access = access; this.rentals = rentals; }
    private long customerId() {
        if (!"CUSTOMER".equals(text(access.user(), "role")) || access.user().get("customer_id") == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅已关联档案的客户账号可访问");
        return number(access.user(), "customer_id");
    }
    private Map<String, Object> publicVehicle(Map<String, Object> vehicle) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : List.of("id", "plate", "brand", "model", "category", "color", "seats", "branch_id", "pricing_id", "status", "image")) result.put(key, vehicle.get(key));
        return result;
    }
    private Map<String, Object> profile() {
        Map<String, Object> customer = new LinkedHashMap<>(store.get("customer", customerId()));
        customer.remove("notes");
        return customer;
    }
    @GetMapping("/bootstrap") public Map<String, Object> bootstrap() {
        long customerId = customerId();
        List<Map<String, Object>> orders = store.list("rental_order").stream().filter(order -> number(order, "customer_id") == customerId).toList();
        Set<Long> orderIds = new HashSet<>(), vehicleIds = new HashSet<>();
        for (Map<String, Object> order : orders) { orderIds.add(number(order, "id")); vehicleIds.add(number(order, "vehicle_id")); }
        return map("vehicles", store.list("vehicle").stream().filter(vehicle -> Set.of("AVAILABLE", "RENTED").contains(text(vehicle, "status")) || vehicleIds.contains(number(vehicle, "id"))).map(this::publicVehicle).toList(), "customers", List.of(profile()), "orders", orders, "payments", store.list("payment_record").stream().filter(payment -> orderIds.contains(number(payment, "order_id"))).map(payment -> { Map<String, Object> safe = new LinkedHashMap<>(payment); safe.remove("operator"); safe.remove("request_id"); return safe; }).toList(), "branches", store.list("branch"), "pricing", store.list("pricing_rule"), "dispatch", List.of(), "maintenance", List.of(), "reminders", List.of(), "settings", map());
    }
    @PutMapping("/profile") @Transactional public Map<String, Object> updateProfile(@RequestBody Map<String, Object> input) {
        long customerId = customerId(); store.lock("customer", customerId);
        String phone = required(input, "phone", 30), name = required(input, "name", 60);
        require(phone.matches("[0-9+ -]{7,20}"), "联系方式格式不正确");
        store.update("customer", customerId, map("name", name, "phone", phone, "license_no", required(input, "license_no", 80), "license_expiry", date(input, "license_expiry").toString()));
        store.update("sys_user", number(access.user(), "id"), map("name", name));
        access.log("客户更新本人资料", text(access.user(), "username")); return profile();
    }
    @PostMapping("/quote") public Map<String, Object> quote(@RequestBody Map<String, Object> input) { customerId(); return rentals.quote(input); }
    @PostMapping("/orders") @Transactional public Map<String, Object> reserve(@RequestBody Map<String, Object> input) {
        Map<String, Object> safe = new LinkedHashMap<>();
        for (String key : List.of("vehicle_id", "planned_start", "planned_end", "expected_mileage", "billing_mode", "return_branch")) safe.put(key, input.get(key));
        safe.put("customer_id", customerId());
        safe.put("pickup_branch", store.get("vehicle", number(input, "vehicle_id")).get("branch_id"));
        return rentals.save(null, safe);
    }
    @PostMapping("/orders/{id}/cancel") @Transactional public Map<String, Object> cancel(@PathVariable long id) {
        Map<String, Object> order = store.get("rental_order", id);
        if (number(order, "customer_id") != customerId()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能操作其他客户的订单");
        store.lock("vehicle", number(order, "vehicle_id"));
        order = store.lock("rental_order", id);
        require("RESERVED".equals(text(order, "status")), "仅待取车预约可取消");
        require(rentals.netPaid(id).signum() == 0, "该订单已有收款，请联系管理员办理退款与取消");
        return rentals.cancel(id, map("request_id", UUID.randomUUID().toString()));
    }
}
