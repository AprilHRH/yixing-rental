package com.yixing.order;

import com.yixing.auth.Access;
import com.yixing.customer.CustomerService;
import com.yixing.data.Store;
import com.yixing.pricing.PricingService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.yixing.common.BusinessException.require;
import static com.yixing.common.Values.*;

@Service
public class RentalService {
    private final Store store;
    private final Access access;
    private final CustomerService customers;
    private final PricingService pricing;
    public RentalService(Store store, Access access, CustomerService customers, PricingService pricing) { this.store = store; this.access = access; this.customers = customers; this.pricing = pricing; }
    public boolean overlap(LocalDateTime start, LocalDateTime end, LocalDateTime otherStart, LocalDateTime otherEnd) { return start.isBefore(otherEnd) && end.isAfter(otherStart); }
    public void available(long vehicleId, LocalDateTime start, LocalDateTime end, long excludedOrder) {
        require(end.isAfter(start), "计划还车时间必须晚于取车时间");
        Map<String, Object> vehicle = store.get("vehicle", vehicleId);
        require(Set.of("AVAILABLE", "RENTED").contains(text(vehicle, "status")), "车辆正在维修、调度或已停用");
        for (Map<String, Object> order : store.list("rental_order")) {
            if (number(order, "vehicle_id") != vehicleId || number(order, "id") == excludedOrder || !Set.of("RESERVED", "ACTIVE").contains(text(order, "status"))) continue;
            require(!(text(order, "status").equals("ACTIVE") && time(order, "planned_end").isBefore(LocalDateTime.now())), "车辆逾期未还，暂不可预约");
            require(!overlap(start, end, time(order, "planned_start"), time(order, "planned_end")), "该车辆在所选时间已被预约");
        }
        for (Map<String, Object> task : store.list("dispatch_task")) {
            if (number(task, "vehicle_id") == vehicleId && Set.of("PENDING", "IN_PROGRESS").contains(text(task, "status")))
                require(!overlap(start, end, time(task, "planned_start"), time(task, "planned_end")), "租期与调度任务冲突");
        }
    }
    public Map<String, Object> quote(Map<String, Object> input) {
        Map<String, Object> vehicle = store.get("vehicle", number(input, "vehicle_id"));
        Map<String, Object> rates = store.get("pricing_rule", number(vehicle, "pricing_id"));
        return pricing.calculate(rates, text(input, "billing_mode"), time(input, "planned_start"), time(input, "planned_end"), number(input, "expected_mileage"));
    }
    @Transactional
    public Map<String, Object> save(Long id, Map<String, Object> input) {
        long vehicleId = number(input, "vehicle_id");
        Map<String, Object> vehicle = store.lock("vehicle", vehicleId);
        if (id != null) {
            Map<String, Object> existing = store.lock("rental_order", id);
            require("RESERVED".equals(text(existing, "status")), "仅待取车订单可以修改");
            require(number(existing, "vehicle_id") == vehicleId && number(existing, "customer_id") == number(input, "customer_id"), "修改订单不可变更客户和车辆，请取消后重新创建");
            require(netPaid(id).signum() == 0, "已收款订单请先取消并退款，再创建新订单");
        }
        LocalDateTime start = time(input, "planned_start"), end = time(input, "planned_end");
        require(!start.isBefore(LocalDateTime.now().minusMinutes(5)), "计划取车时间不能早于当前时间");
        Map<String, Object> customer = store.lock("customer", number(input, "customer_id"));
        customers.eligible(customer, end.toLocalDate());
        available(vehicleId, start, end, id == null ? -1 : id);
        long pickup = number(input, "pickup_branch"), returned = number(input, "return_branch");
        store.get("branch", pickup); store.get("branch", returned);
        require(pickup == number(vehicle, "branch_id"), "预约取车门店须为车辆当前门店，请先完成调度");
        Map<String, Object> rates = store.get("pricing_rule", number(vehicle, "pricing_id"));
        Map<String, Object> quote = pricing.calculate(rates, text(input, "billing_mode"), start, end, number(input, "expected_mileage"));
        Map<String, Object> data = map("customer_id", number(input, "customer_id"), "vehicle_id", vehicleId, "pickup_branch", pickup, "return_branch", returned, "planned_start", start.toString(), "planned_end", end.toString(), "expected_mileage", number(input, "expected_mileage"), "billing_mode", text(input, "billing_mode"), "estimated_rent", quote.get("rent"));
        for (String field : List.of("daily_rate", "monthly_rate", "base_fee", "mileage_rate", "deposit")) data.put(field, rates.get(field));
        if (id == null) data.putAll(map("order_no", "YX" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + UUID.randomUUID().toString().substring(0, 6).toUpperCase(), "status", "RESERVED", "created_at", now()));
        Map<String, Object> result = id == null ? store.insert("rental_order", data) : store.update("rental_order", id, data);
        access.log(id == null ? "创建订单" : "修改预约", text(result, "order_no")); return result;
    }
    private Map<String, Object> lockedOrder(long id) {
        Map<String, Object> initial = store.get("rental_order", id);
        store.lock("vehicle", number(initial, "vehicle_id"));
        return store.lock("rental_order", id);
    }
    public BigDecimal netPaid(long id) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> payment : store.list("payment_record")) {
            if (number(payment, "order_id") == id) total = total.add(money(payment, "amount").multiply("IN".equals(text(payment, "direction")) ? BigDecimal.ONE : BigDecimal.ONE.negate()));
        }
        return total;
    }
    private void record(long orderId, String requestId, String kind, String direction, BigDecimal amount) {
        require(!requestId.isBlank() && requestId.length() <= 80, "缺少有效的操作请求编号");
        if (amount.signum() == 0) return;
        store.insert("payment_record", map("order_id", orderId, "request_id", requestId, "kind", kind, "direction", direction, "amount", amount, "method", "线下登记", "created_at", now(), "operator", text(access.user(), "name")));
    }
    @Transactional
    public Map<String, Object> collect(long id, Map<String, Object> input) {
        Map<String, Object> order = lockedOrder(id);
        require(Set.of("RESERVED", "ACTIVE").contains(text(order, "status")), "当前订单不允许收取预付款");
        String requestId = required(input, "request_id", 80);
        require(store.list("payment_record").stream().noneMatch(payment -> requestId.equals(text(payment, "request_id"))), "该收款操作已经登记，请勿重复提交");
        BigDecimal amount = money(input, "amount"); require(amount.signum() > 0, "收款金额必须大于零");
        String kind = text(input, "kind"); require(Set.of("DEPOSIT", "RENT").contains(kind), "款项类型不正确");
        BigDecimal collected = store.list("payment_record").stream().filter(payment -> number(payment, "order_id") == id && kind.equals(text(payment, "kind"))).map(payment -> money(payment, "amount")).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cap = money(order, kind.equals("DEPOSIT") ? "deposit" : "estimated_rent");
        require(collected.add(amount).compareTo(cap) <= 0, "累计收款超过该项应收金额");
        record(id, requestId, kind, "IN", amount); access.log("登记收款", text(order, "order_no")); return map("net_paid", netPaid(id));
    }
    @Transactional
    public Map<String, Object> pickup(long id, Map<String, Object> input) {
        Map<String, Object> order = lockedOrder(id);
        require("RESERVED".equals(text(order, "status")), "仅待取车订单可以交车");
        Map<String, Object> vehicle = store.get("vehicle", number(order, "vehicle_id"));
        require("AVAILABLE".equals(text(vehicle, "status")), "车辆尚不可交付，请确认上笔订单已还车");
        require(number(vehicle, "branch_id") == number(order, "pickup_branch"), "车辆不在取车门店");
        require(!LocalDateTime.now().isBefore(time(order, "planned_start").minusMinutes(10)) && LocalDateTime.now().isBefore(time(order, "planned_end")), "请在预约取车时间前 10 分钟至计划还车时间内交车");
        customers.eligible(store.lock("customer", number(order, "customer_id")), time(order, "planned_end").toLocalDate());
        BigDecimal depositPaid = store.list("payment_record").stream().filter(payment -> number(payment, "order_id") == id && "DEPOSIT".equals(text(payment, "kind"))).map(payment -> money(payment, "amount")).reduce(BigDecimal.ZERO, BigDecimal::add);
        require(depositPaid.compareTo(money(order, "deposit")) >= 0, "请先收齐押金");
        long mileage = number(input, "start_mileage"); require(mileage >= number(vehicle, "mileage") && mileage <= 2000000, "取车里程不能低于车辆当前里程");
        store.update("vehicle", number(vehicle, "id"), map("status", "RENTED", "mileage", mileage));
        Map<String, Object> result = store.update("rental_order", id, map("status", "ACTIVE", "actual_start", now(), "start_mileage", mileage, "pickup_condition", required(input, "condition", 500)));
        access.log("办理交车", text(order, "order_no")); return result;
    }
    @Transactional
    public Map<String, Object> returned(long id, Map<String, Object> input) {
        Map<String, Object> order = lockedOrder(id);
        require("ACTIVE".equals(text(order, "status")), "仅租赁中订单可以还车");
        long mileage = number(input, "end_mileage");
        require(mileage >= number(order, "start_mileage") && mileage <= 2000000, "还车里程不能低于取车里程");
        BigDecimal extra = money(input, "extra_fee"), discount = money(input, "discount");
        String notes = text(input, "fee_notes"); require(notes.length() <= 500 && (extra.signum() == 0 || !notes.isBlank()), "附加费用须填写明细说明且不超过 500 字");
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = time(order, "actual_start");
        if (!end.isAfter(start)) end = start.plusSeconds(1);
        Map<String, Object> quote = pricing.calculate(order, text(order, "billing_mode"), start, end, mileage - number(order, "start_mileage"));
        BigDecimal rent = ((BigDecimal) quote.get("rent")).add(extra).subtract(discount); require(rent.signum() >= 0, "优惠金额不能超过总费用");
        long returnedBranch = number(input, "return_branch"); store.get("branch", returnedBranch);
        boolean damage = flag(input, "damage");
        store.update("vehicle", number(order, "vehicle_id"), map("status", damage ? "MAINTENANCE" : "AVAILABLE", "mileage", mileage, "branch_id", returnedBranch));
        Map<String, Object> result = store.update("rental_order", id, map("status", "RETURNED", "actual_end", end.toString(), "end_mileage", mileage, "return_branch", returnedBranch, "extra_fee", extra, "discount", discount, "fee_notes", notes, "actual_rent", rent, "return_condition", required(input, "condition", 500), "damage", damage));
        if (damage) store.insert("maintenance_record", map("vehicle_id", number(order, "vehicle_id"), "type", "REPAIR", "project", "还车异常检修：" + text(input, "condition"), "assignee", text(access.user(), "name"), "cost", 0, "status", "IN_PROGRESS", "started_at", now()));
        access.log("办理还车", text(order, "order_no")); return result;
    }
    @Transactional
    public Map<String, Object> settle(long id, Map<String, Object> input) {
        Map<String, Object> order = lockedOrder(id); require("RETURNED".equals(text(order, "status")), "仅待结算订单可以结算");
        BigDecimal difference = money(order, "actual_rent").subtract(netPaid(id));
        record(id, required(input, "request_id", 80), "SETTLEMENT", difference.signum() >= 0 ? "IN" : "OUT", difference.abs());
        Map<String, Object> result = store.update("rental_order", id, map("status", "COMPLETED", "completed_at", now()));
        access.log("完成结算", text(order, "order_no")); return result;
    }
    @Transactional
    public Map<String, Object> cancel(long id, Map<String, Object> input) {
        Map<String, Object> order = lockedOrder(id); require("RESERVED".equals(text(order, "status")), "仅待取车订单可以取消");
        record(id, required(input, "request_id", 80), "CANCEL_REFUND", "OUT", netPaid(id));
        Map<String, Object> result = store.update("rental_order", id, map("status", "CANCELLED", "cancelled_at", now()));
        access.log("取消订单", text(order, "order_no")); return result;
    }
}
