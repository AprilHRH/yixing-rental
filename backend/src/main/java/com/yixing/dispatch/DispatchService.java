package com.yixing.dispatch;

import com.yixing.auth.Access;
import com.yixing.data.Store;
import com.yixing.order.RentalService;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.yixing.common.BusinessException.require;
import static com.yixing.common.Values.*;

@Service
public class DispatchService {
    private final Store store; private final Access access; private final RentalService rentals;
    public DispatchService(Store store, Access access, RentalService rentals) { this.store = store; this.access = access; this.rentals = rentals; }
    private void reservations(long vehicleId, LocalDateTime start, LocalDateTime end) {
        for (Map<String, Object> order : store.list("rental_order")) {
            if (number(order, "vehicle_id") == vehicleId && Set.of("RESERVED", "ACTIVE").contains(text(order, "status"))) {
                require(!rentals.overlap(start, end, time(order, "planned_start"), time(order, "planned_end")), "调度时间与租赁预约冲突");
                require(!time(order, "planned_start").isAfter(end), "该车辆已有后续预约，变更门店前请先调整订单");
            }
        }
    }
    @Transactional
    public Map<String, Object> create(Map<String, Object> input) {
        long vehicleId = number(input, "vehicle_id"); Map<String, Object> vehicle = store.lock("vehicle", vehicleId);
        require("AVAILABLE".equals(text(vehicle, "status")), "仅可用车辆可安排调度");
        long target = number(input, "to_branch"); store.get("branch", target);
        require(target != number(vehicle, "branch_id"), "目标门店不能与当前门店相同");
        LocalDateTime start = time(input, "planned_start"), end = time(input, "planned_end");
        require(end.isAfter(start) && !start.isBefore(LocalDateTime.now().minusMinutes(5)), "调度起止时间不正确");
        reservations(vehicleId, start, end);
        require(store.list("dispatch_task").stream().noneMatch(task -> number(task, "vehicle_id") == vehicleId && Set.of("PENDING", "IN_PROGRESS").contains(text(task, "status"))), "该车辆已有未完成调度");
        Map<String, Object> result = store.insert("dispatch_task", map("vehicle_id", vehicleId, "from_branch", vehicle.get("branch_id"), "to_branch", target, "assignee", required(input, "assignee", 60), "planned_start", start.toString(), "planned_end", end.toString(), "status", "PENDING", "notes", text(input, "notes")));
        access.log("创建调度", text(vehicle, "plate")); return result;
    }
    @Transactional
    public Map<String, Object> action(long id, String action) {
        Map<String, Object> task = store.get("dispatch_task", id); long vehicleId = number(task, "vehicle_id");
        Map<String, Object> vehicle = store.lock("vehicle", vehicleId); task = store.lock("dispatch_task", id);
        switch (action) {
            case "start" -> {
                require("PENDING".equals(text(task, "status")) && "AVAILABLE".equals(text(vehicle, "status")), "任务或车辆状态不允许开始调度");
                require(number(vehicle, "branch_id") == number(task, "from_branch"), "车辆已不在出发门店");
                require(LocalDateTime.now().isBefore(time(task, "planned_end")), "调度计划已过期，请取消后重建");
                reservations(vehicleId, LocalDateTime.now(), time(task, "planned_end"));
                store.update("vehicle", vehicleId, map("status", "DISPATCHING"));
                store.update("dispatch_task", id, map("status", "IN_PROGRESS", "actual_start", now()));
            }
            case "complete" -> {
                require("IN_PROGRESS".equals(text(task, "status")) && "DISPATCHING".equals(text(vehicle, "status")), "仅进行中的调度可完成");
                store.update("vehicle", vehicleId, map("status", "AVAILABLE", "branch_id", task.get("to_branch")));
                store.update("dispatch_task", id, map("status", "COMPLETED", "actual_end", now()));
            }
            case "cancel" -> {
                require("PENDING".equals(text(task, "status")), "仅待执行调度可以取消");
                store.update("dispatch_task", id, map("status", "CANCELLED"));
            }
            default -> throw new com.yixing.common.BusinessException("不支持的调度操作");
        }
        access.log("调度操作 " + action, text(vehicle, "plate")); return store.get("dispatch_task", id);
    }
}
