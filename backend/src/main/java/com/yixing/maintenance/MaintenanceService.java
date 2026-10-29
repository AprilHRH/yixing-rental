package com.yixing.maintenance;

import com.yixing.auth.Access;
import com.yixing.data.Store;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.yixing.common.BusinessException.require;
import static com.yixing.common.Values.*;

@Service
public class MaintenanceService {
    private final Store store; private final Access access;
    public MaintenanceService(Store store, Access access) { this.store = store; this.access = access; }
    @Transactional
    public Map<String, Object> create(Map<String, Object> input) {
        long vehicleId = number(input, "vehicle_id"); Map<String, Object> vehicle = store.lock("vehicle", vehicleId);
        require("AVAILABLE".equals(text(vehicle, "status")), "仅可用车辆可新建维修保养工单");
        require(Set.of("REPAIR", "SERVICE").contains(text(input, "type")), "工单类型不正确");
        require(store.list("maintenance_record").stream().noneMatch(task -> number(task, "vehicle_id") == vehicleId && Set.of("PENDING", "IN_PROGRESS").contains(text(task, "status"))), "该车辆已有未完成工单");
        Map<String, Object> result = store.insert("maintenance_record", map("vehicle_id", vehicleId, "type", text(input, "type"), "project", required(input, "project", 200), "assignee", required(input, "assignee", 60), "cost", money(input, "cost"), "status", "PENDING"));
        access.log("新增维修工单", text(vehicle, "plate")); return result;
    }
    @Transactional
    public Map<String, Object> action(long id, String action, Map<String, Object> input) {
        Map<String, Object> task = store.get("maintenance_record", id); long vehicleId = number(task, "vehicle_id");
        Map<String, Object> vehicle = store.lock("vehicle", vehicleId); task = store.lock("maintenance_record", id);
        switch (action) {
            case "start" -> {
                require("PENDING".equals(text(task, "status")) && "AVAILABLE".equals(text(vehicle, "status")), "车辆或工单状态不允许开始");
                require(store.list("rental_order").stream().noneMatch(order -> number(order, "vehicle_id") == vehicleId && Set.of("RESERVED", "ACTIVE").contains(text(order, "status"))), "该车辆存在有效预约，请先处理订单");
                require(store.list("dispatch_task").stream().noneMatch(dispatch -> number(dispatch, "vehicle_id") == vehicleId && Set.of("PENDING", "IN_PROGRESS").contains(text(dispatch, "status"))), "请先处理未完成调度任务");
                store.update("vehicle", vehicleId, map("status", "MAINTENANCE"));
                store.update("maintenance_record", id, map("status", "IN_PROGRESS", "started_at", now()));
            }
            case "complete" -> {
                require("IN_PROGRESS".equals(text(task, "status")) && "MAINTENANCE".equals(text(vehicle, "status")), "仅维修中的工单可以完成");
                LocalDate nextDate = date(input, "next_date"); long nextMileage = number(input, "next_mileage");
                require(nextDate.isAfter(LocalDate.now()) && nextMileage > number(vehicle, "mileage"), "下次保养日期和里程必须晚于当前值");
                store.update("maintenance_record", id, map("status", "COMPLETED", "completed_at", now(), "cost", money(input, "cost"), "next_date", nextDate.toString(), "next_mileage", nextMileage));
                store.update("vehicle", vehicleId, map("status", "AVAILABLE", "next_service_date", nextDate.toString(), "next_service_mileage", nextMileage));
            }
            case "cancel" -> {
                require("PENDING".equals(text(task, "status")), "仅待处理工单可取消"); store.update("maintenance_record", id, map("status", "CANCELLED"));
            }
            default -> throw new com.yixing.common.BusinessException("不支持的维修操作");
        }
        access.log("维修操作 " + action, text(vehicle, "plate")); return store.get("maintenance_record", id);
    }
    public List<Map<String, Object>> reminders() {
        Map<String, Object> settings = store.get("settings", 1);
        Set<Long> activeWork = new HashSet<>();
        for (Map<String, Object> task : store.list("maintenance_record")) if ("IN_PROGRESS".equals(text(task, "status"))) activeWork.add(number(task, "vehicle_id"));
        return store.list("vehicle").stream().filter(vehicle -> !"DISABLED".equals(text(vehicle, "status")) && !activeWork.contains(number(vehicle, "id"))).filter(vehicle -> {
            boolean dateDue = !text(vehicle, "next_service_date").isBlank() && !date(vehicle, "next_service_date").isAfter(LocalDate.now().plusDays(number(settings, "reminder_days")));
            boolean mileageDue = vehicle.get("next_service_mileage") != null && number(vehicle, "next_service_mileage") - number(vehicle, "mileage") <= number(settings, "reminder_km");
            return dateDue || mileageDue;
        }).toList();
    }
}
