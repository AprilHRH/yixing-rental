package com.yixing.data;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import static com.yixing.common.Values.*;

@Component
public class DemoData implements ApplicationRunner {
    private final Store store;
    public DemoData(Store store) { this.store = store; }
    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        // 仅在没有账号的空库里生成课程演示数据，正常重启不会重复添加。
        // 下方账号密码用于本地演示，正式部署前应更换；客户和车辆信息均为虚构。
        if (!store.list("sys_user").isEmpty()) return;
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        store.insert("sys_user", map("username", "admin", "password_hash", encoder.encode("Admin@12345"), "name", "林知行", "role", "ADMIN", "enabled", true));
        store.insert("sys_user", map("username", "staff", "password_hash", encoder.encode("Staff@12345"), "name", "陈小满", "role", "ADMIN", "account_type", "STAFF", "enabled", true));
        store.insert("settings", map("id", 1L, "reminder_days", 7, "reminder_km", 500, "company_name", "易行租车"));
        List<Map<String, Object>> branches = new ArrayList<>();
        for (String[] entry : List.of(new String[]{"静安城市店", "上海市静安区示范路 18 号", "021-00000001"}, new String[]{"虹桥枢纽店", "上海市闵行区示范路 66 号", "021-00000002"}, new String[]{"浦东机场店", "上海市浦东新区示范路 99 号", "021-00000003"})) branches.add(store.insert("branch", map("name", entry[0], "address", entry[1], "phone", entry[2])));
        List<Map<String, Object>> rates = new ArrayList<>();
        rates.add(store.insert("pricing_rule", map("name", "经济舒适型", "daily_rate", 188, "monthly_rate", 3980, "base_fee", 50, "mileage_rate", 2, "deposit", 1000)));
        rates.add(store.insert("pricing_rule", map("name", "城市 SUV", "daily_rate", 298, "monthly_rate", 5980, "base_fee", 80, "mileage_rate", 3, "deposit", 1500)));
        rates.add(store.insert("pricing_rule", map("name", "新能源精选", "daily_rate", 258, "monthly_rate", 5280, "base_fee", 60, "mileage_rate", 2.5, "deposit", 1200)));
        rates.add(store.insert("pricing_rule", map("name", "商务出行", "daily_rate", 468, "monthly_rate", 9980, "base_fee", 100, "mileage_rate", 4, "deposit", 2000)));
        String[][] models = {{"大众", "朗逸", "经济轿车", "银灰", "0"}, {"丰田", "RAV4 荣放", "SUV", "珍珠白", "1"}, {"比亚迪", "海豹", "新能源", "冰川蓝", "2"}, {"别克", "GL8", "商务 MPV", "曜石黑", "3"}, {"本田", "雅阁", "经济轿车", "珍珠白", "0"}, {"特斯拉", "Model 3", "新能源", "冷光银", "2"}, {"丰田", "凯美瑞", "经济轿车", "墨玉黑", "0"}, {"大众", "途观 L", "SUV", "星空灰", "1"}, {"比亚迪", "宋 PLUS", "新能源", "雪域白", "2"}, {"理想", "L7", "SUV", "深空灰", "1"}, {"本田", "奥德赛", "商务 MPV", "珍珠白", "3"}, {"日产", "轩逸", "经济轿车", "曜石黑", "0"}, {"吉利", "星越 L", "SUV", "翠羽蓝", "1"}, {"小鹏", "P7", "新能源", "星际绿", "2"}, {"丰田", "赛那", "商务 MPV", "铂金白", "3"}, {"大众", "帕萨特", "经济轿车", "玄武黑", "0"}, {"蔚来", "ET5", "新能源", "云初黄", "2"}, {"长安", "CS75 PLUS", "SUV", "原子灰", "1"}};
        List<Map<String, Object>> vehicles = new ArrayList<>();
        for (int index = 0; index < models.length; index++) {
            String[] model = models[index]; long mileage = 8000 + index * 1730L;
            vehicles.add(store.insert("vehicle", map("plate", "沪A·" + (21001 + index), "brand", model[0], "model", model[1], "category", model[2], "color", model[3], "seats", model[2].equals("商务 MPV") ? 7 : 5, "purchase_date", LocalDate.now().minusMonths(18 + index).toString(), "license_no", "DEMO-VEHICLE-" + (index + 1), "mileage", mileage, "branch_id", branches.get(index % 3).get("id"), "pricing_id", rates.get(Integer.parseInt(model[4])).get("id"), "status", "AVAILABLE", "image", "", "next_service_date", LocalDate.now().plusDays(index % 5 == 0 ? 3 : 30 + index).toString(), "next_service_mileage", mileage + (index % 5 == 0 ? 300 : 5000))));
        }
        List<Map<String, Object>> customers = new ArrayList<>(); String[] names = {"周予安", "许清禾", "沈亦舟", "顾南乔", "陆景明", "林初夏", "程远", "苏念", "测试限制客户"};
        for (int index = 0; index < names.length; index++) customers.add(store.insert("customer", map("name", names[index], "identity_no", "DEMO-ID-" + String.format("%06d", index + 1), "phone", "1380000" + String.format("%04d", index + 1), "license_no", "DEMO-DL-" + (index + 1), "license_expiry", LocalDate.now().plusYears(3).toString(), "category", index % 3 == 0 ? "VIP" : "NORMAL", "restricted", index == 8, "notes", index == 8 ? "演示数据：限制租赁场景" : "虚构客户，仅用于课程演示")));
        // 生成已结算、出租中和待取车订单，让工作台和报表有不同业务状态可展示。
        for (int index = 0; index < 24; index++) {
            Map<String, Object> vehicle = vehicles.get(index % vehicles.size()), rate = rates.get(Integer.parseInt(models[index % models.length][4]));
            LocalDateTime returned = LocalDate.now().minusDays(index % 14).atTime(9 + index % 8, 0);
            if (returned.isAfter(LocalDateTime.now())) returned = LocalDateTime.now().minusHours(1);
            LocalDateTime picked = returned.minusDays(1 + index % 3);
            BigDecimal amount = new BigDecimal(rate.get("daily_rate").toString()).multiply(BigDecimal.valueOf(1 + index % 3));
            Map<String, Object> order = seedOrder(vehicle, customers.get(index % 8), rate, picked, returned, "COMPLETED", index);
            store.update("rental_order", number(order, "id"), map("actual_start", picked.toString(), "actual_end", returned.toString(), "start_mileage", Math.max(0, number(vehicle, "mileage") - 200), "end_mileage", number(vehicle, "mileage"), "actual_rent", amount, "extra_fee", 0, "discount", 0, "completed_at", returned.toString(), "pickup_condition", "车况正常", "return_condition", "验收正常"));
            store.insert("payment_record", map("order_id", order.get("id"), "request_id", "seed-paid-" + index, "kind", "SETTLEMENT", "direction", "IN", "amount", amount, "method", "演示数据", "created_at", returned.toString(), "operator", "陈小满"));
        }
        for (int index = 0; index < 2; index++) {
            Map<String, Object> vehicle = vehicles.get(index), rate = rates.get(index);
            LocalDateTime start = LocalDateTime.now().minusHours(20 + index * 4), end = LocalDateTime.now().plusDays(index + 1);
            Map<String, Object> order = seedOrder(vehicle, customers.get(index), rate, start, end, "ACTIVE", 30 + index);
            store.update("rental_order", number(order, "id"), map("actual_start", start.toString(), "start_mileage", vehicle.get("mileage"), "pickup_condition", "车况正常，随车物品齐全"));
            store.update("vehicle", number(vehicle, "id"), map("status", "RENTED"));
            store.insert("payment_record", map("order_id", order.get("id"), "request_id", "seed-deposit-" + index, "kind", "DEPOSIT", "direction", "IN", "amount", rate.get("deposit"), "method", "演示数据", "created_at", start.toString(), "operator", "陈小满"));
        }
        for (int index = 2; index < 5; index++) {
            LocalDateTime start = index == 2 ? LocalDateTime.now().plusMinutes(2) : LocalDateTime.now().plusDays(index - 1);
            seedOrder(vehicles.get(index), customers.get(index), rates.get(Integer.parseInt(models[index][4])), start, start.plusDays(2), "RESERVED", 40 + index);
        }
        Map<String, Object> repairVehicle = vehicles.get(7); store.update("vehicle", number(repairVehicle, "id"), map("status", "MAINTENANCE"));
        store.insert("maintenance_record", map("vehicle_id", repairVehicle.get("id"), "type", "SERVICE", "project", "常规保养 / 更换机油与滤芯", "assignee", "王师傅", "cost", 680, "status", "IN_PROGRESS", "started_at", LocalDateTime.now().minusHours(3).toString()));
        store.insert("maintenance_record", map("vehicle_id", vehicles.get(8).get("id"), "type", "SERVICE", "project", "轮胎检查及四轮定位", "assignee", "李师傅", "cost", 320, "status", "PENDING"));
        store.insert("maintenance_record", map("vehicle_id", vehicles.get(12).get("id"), "type", "SERVICE", "project", "常规保养", "assignee", "王师傅", "cost", 480, "status", "COMPLETED", "started_at", LocalDateTime.now().minusDays(4).toString(), "completed_at", LocalDateTime.now().minusDays(3).toString()));
        Map<String, Object> dispatched = vehicles.get(9); store.update("vehicle", number(dispatched, "id"), map("status", "DISPATCHING"));
        store.insert("dispatch_task", map("vehicle_id", dispatched.get("id"), "from_branch", dispatched.get("branch_id"), "to_branch", branches.get(1).get("id"), "assignee", "赵启明", "planned_start", LocalDateTime.now().minusMinutes(30).toString(), "planned_end", LocalDateTime.now().plusHours(2).toString(), "actual_start", LocalDateTime.now().minusMinutes(25).toString(), "status", "IN_PROGRESS", "notes", "门店运力调配"));
        store.insert("operation_log", map("operator", "系统", "action", "初始化演示数据", "target", "车辆 / 客户 / 订单", "created_at", now()));
    }
    private Map<String, Object> seedOrder(Map<String, Object> vehicle, Map<String, Object> customer, Map<String, Object> rate, LocalDateTime start, LocalDateTime end, String status, int serial) {
        // 演示订单也保存价格快照，使其与正式创建订单的结算规则保持一致。
        long days = Math.max(1, (Duration.between(start, end).getSeconds() + 86399) / 86400);
        String createdAt = "RESERVED".equals(status) ? now() : start.minusHours(2).toString();
        Map<String, Object> data = map("order_no", "YX-DEMO-" + String.format("%04d", serial + 1), "customer_id", customer.get("id"), "vehicle_id", vehicle.get("id"), "pickup_branch", vehicle.get("branch_id"), "return_branch", vehicle.get("branch_id"), "planned_start", start.toString(), "planned_end", end.toString(), "expected_mileage", 200, "billing_mode", "DAILY", "estimated_rent", new BigDecimal(rate.get("daily_rate").toString()).multiply(BigDecimal.valueOf(days)), "status", status, "created_at", createdAt);
        for (String field : List.of("daily_rate", "monthly_rate", "base_fee", "mileage_rate", "deposit")) data.put(field, rate.get(field));
        return store.insert("rental_order", data);
    }
}
