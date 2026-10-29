package com.yixing.vehicle;

import com.yixing.auth.Access;
import com.yixing.data.Store;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.yixing.common.BusinessException.require;
import static com.yixing.common.Values.*;

@Service
public class VehicleService {
    private final Store store;
    private final Access access;
    public VehicleService(Store store, Access access) { this.store = store; this.access = access; }
    @Transactional
    public Map<String, Object> save(Long id, Map<String, Object> input) {
        Map<String, Object> data = map();
        for (String key : List.of("plate", "brand", "model", "category", "color", "license_no")) data.put(key, required(input, key, 60));
        require(text(input, "plate").matches("[\\p{IsHan}A-Z0-9·-]{5,12}"), "车牌号格式不正确");
        long seats = number(input, "seats");
        long mileage = number(input, "mileage");
        require(seats >= 1 && seats <= 50 && mileage >= 0 && mileage <= 2000000, "座位数或里程超出范围");
        require(!date(input, "purchase_date").isAfter(LocalDate.now()), "购置日期不能晚于今天");
        store.get("branch", number(input, "branch_id")); store.get("pricing_rule", number(input, "pricing_id"));
        data.putAll(map("seats", seats, "mileage", mileage, "purchase_date", text(input, "purchase_date"), "branch_id", number(input, "branch_id"), "pricing_id", number(input, "pricing_id")));
        if (!text(input, "next_service_date").isBlank()) { date(input, "next_service_date"); data.put("next_service_date", text(input, "next_service_date")); }
        else data.put("next_service_date", null);
        long nextMileage = number(input, "next_service_mileage");
        require(nextMileage >= 0, "保养里程不能为负"); data.put("next_service_mileage", nextMileage);
        String image = text(input, "image");
        require(image.isBlank() || image.matches("/uploads/[a-f0-9-]+\\.(png|jpg)"), "图片地址不正确");
        data.put("image", image);
        if (id == null) data.put("status", "AVAILABLE");
        else {
            Map<String, Object> previous = store.lock("vehicle", id);
            require(mileage >= number(previous, "mileage"), "车辆里程不能回退");
            require(number(input, "branch_id") == number(previous, "branch_id"), "请通过调度业务变更车辆门店");
            require("AVAILABLE".equals(text(previous, "status")) || mileage == number(previous, "mileage"), "非可用车辆不能手动修改里程");
        }
        Map<String, Object> result = id == null ? store.insert("vehicle", data) : store.update("vehicle", id, data);
        access.log(id == null ? "新增车辆" : "编辑车辆", text(result, "plate")); return result;
    }
    @Transactional
    public Map<String, Object> toggle(long id) {
        access.admin(); Map<String, Object> vehicle = store.lock("vehicle", id);
        String status = text(vehicle, "status");
        require(Set.of("AVAILABLE", "DISABLED").contains(status), "只能停用空闲车辆或启用已停用车辆");
        if (status.equals("AVAILABLE")) {
            require(store.list("rental_order").stream().noneMatch(order -> number(order, "vehicle_id") == id && Set.of("RESERVED", "ACTIVE", "RETURNED").contains(text(order, "status"))), "请先处理关联订单");
            require(store.list("dispatch_task").stream().noneMatch(task -> number(task, "vehicle_id") == id && Set.of("PENDING", "IN_PROGRESS").contains(text(task, "status"))), "请先处理调度任务");
            require(store.list("maintenance_record").stream().noneMatch(task -> number(task, "vehicle_id") == id && Set.of("PENDING", "IN_PROGRESS").contains(text(task, "status"))), "请先处理维修工单");
        }
        access.log("变更车辆启用状态", text(vehicle, "plate"));
        return store.update("vehicle", id, map("status", status.equals("AVAILABLE") ? "DISABLED" : "AVAILABLE"));
    }
}
