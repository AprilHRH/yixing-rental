package com.yixing.system;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yixing.auth.Access;
import com.yixing.auth.AuthController;
import com.yixing.data.Store;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import static com.yixing.common.BusinessException.require;
import static com.yixing.common.Values.*;

@Service
public class SystemService {
    private final Store store; private final Access access; private final JdbcTemplate jdbc; private final ObjectMapper json; private final TransactionTemplate transactions;
    private static final List<String> TABLES = List.of("sys_user", "branch", "pricing_rule", "vehicle", "customer", "rental_order", "payment_record", "dispatch_task", "maintenance_record", "settings", "operation_log");
    public SystemService(Store store, Access access, JdbcTemplate jdbc, ObjectMapper json, org.springframework.transaction.PlatformTransactionManager manager) { this.store = store; this.access = access; this.jdbc = jdbc; this.json = json; this.transactions = new TransactionTemplate(manager); }
    public Map<String, Object> overview() {
        return map("users", store.list("sys_user").stream().map(AuthController::publicUser).toList(), "settings", store.get("settings", 1), "logs", store.list("operation_log").stream().limit(300).toList());
    }
    @Transactional
    public Map<String, Object> user(Long id, Map<String, Object> input) {
        access.admin();
        String role = text(input, "role"), username = required(input, "username", 50);
        new com.yixing.auth.AccountService(store).username(input, id);
        require(Set.of("ADMIN", "CUSTOMER").contains(role) && username.matches("[a-zA-Z0-9_]{3,30}"), "角色或账号格式不正确");
        Map<String, Object> data = map("username", username, "name", required(input, "name", 60), "role", role, "enabled", flag(input, "enabled"));
        String accountType = "CUSTOMER".equals(role) ? "CUSTOMER" : text(input, "account_type");
        if (accountType.isBlank()) accountType = "ADMIN";
        require(Set.of("ADMIN", "STAFF", "CUSTOMER").contains(accountType) && (!"ADMIN".equals(role) || !"CUSTOMER".equals(accountType)), "账号类型不正确");
        data.put("account_type", accountType);
        if ("CUSTOMER".equals(role)) {
            long customerId = number(input, "customer_id"); store.lock("customer", customerId);
            require(store.list("sys_user").stream().noneMatch(user -> user.get("customer_id") != null && number(user, "customer_id") == customerId && (id == null || number(user, "id") != id)), "该客户已有登录账号");
            data.put("customer_id", customerId);
        } else data.put("customer_id", null);
        if (id == null || !text(input, "password").isBlank()) {
            String password = required(input, "password", 64); require(password.length() >= 8, "密码至少 8 位"); data.put("password_hash", new BCryptPasswordEncoder().encode(password));
        }
        if (id != null) {
            store.lock("sys_user", id);
            require(id != number(access.user(), "id") || (role.equals("ADMIN") && flag(input, "enabled")), "不能禁用自己或移除自己的管理员权限");
        }
        Map<String, Object> user = id == null ? store.insert("sys_user", data) : store.update("sys_user", id, data);
        access.log("管理用户", username); return AuthController.publicUser(user);
    }
    @Transactional
    public Map<String, Object> pricing(long id, Map<String, Object> input) {
        access.admin(); store.get("pricing_rule", id); Map<String, Object> data = map("name", required(input, "name", 80));
        for (String field : List.of("daily_rate", "monthly_rate", "base_fee", "mileage_rate", "deposit")) data.put(field, money(input, field));
        require(money(input, "daily_rate").signum() > 0 && money(input, "monthly_rate").signum() > 0, "日租和月租价格必须大于零");
        access.log("修改计费规则", text(input, "name")); return store.update("pricing_rule", id, data);
    }
    @Transactional
    public Map<String, Object> branch(Long id, Map<String, Object> input) {
        access.admin(); Map<String, Object> data = map("name", required(input, "name", 80), "address", required(input, "address", 200), "phone", required(input, "phone", 30));
        Map<String, Object> result = id == null ? store.insert("branch", data) : store.update("branch", id, data); access.log("编辑门店", text(input, "name")); return result;
    }
    @Transactional
    public Map<String, Object> settings(Map<String, Object> input) {
        access.admin(); long days = number(input, "reminder_days"), km = number(input, "reminder_km");
        require(days >= 0 && days <= 365 && km >= 0 && km <= 100000, "保养提醒参数超出范围");
        access.log("修改系统参数", "保养提醒"); return store.update("settings", 1, map("reminder_days", days, "reminder_km", km));
    }
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.SERIALIZABLE)
    public byte[] backup() throws IOException {
        access.admin(); access.log("导出备份", "数据库及车辆图片");
        Map<String, Object> snapshot = map("format", "yixing-backup-v1", "created_at", now());
        for (String table : TABLES) snapshot.put(table, store.list(table));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("database.json")); zip.write(json.writeValueAsBytes(snapshot)); zip.closeEntry();
            Path uploads = Path.of("uploads");
            if (Files.isDirectory(uploads)) try (var paths = Files.list(uploads)) {
                for (Path image : paths.filter(Files::isRegularFile).toList()) {
                    if (!image.getFileName().toString().matches("[a-f0-9-]+\\.(png|jpg)")) continue;
                    zip.putNextEntry(new ZipEntry("uploads/" + image.getFileName())); Files.copy(image, zip); zip.closeEntry();
                }
            }
        }
        return output.toByteArray();
    }
    public void restore(MultipartFile file, String confirmation) throws IOException {
        access.admin(); require("RESTORE".equals(confirmation), "请输入 RESTORE 确认恢复");
        byte[] database = null; Map<String, byte[]> images = new LinkedHashMap<>(); long total = 0; int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                require(++entries <= 200, "备份文件条目过多");
                require(entry.getName().equals("database.json") || entry.getName().matches("uploads/[a-f0-9-]+\\.(png|jpg)"), "备份包含不允许的文件");
                byte[] bytes = zip.readNBytes(6 * 1024 * 1024); total += bytes.length;
                require(bytes.length < 6 * 1024 * 1024 && total <= 20 * 1024 * 1024, "备份解压大小超限");
                if (entry.getName().equals("database.json")) { require(database == null, "数据库文件重复"); database = bytes; }
                else images.put(entry.getName().substring(8), bytes);
            }
        }
        require(database != null, "备份缺少数据库文件");
        Map<String, Object> snapshot = json.readValue(database, new TypeReference<>() {});
        require("yixing-backup-v1".equals(snapshot.get("format")), "备份格式不正确");
        Map<String, List<Map<String, Object>>> restored = new LinkedHashMap<>();
        for (String table : TABLES) {
            Object rows = snapshot.get(table); require(rows instanceof List<?> && ((List<?>) rows).size() <= 10000, "备份表不完整或数据量超限");
            List<Map<String, Object>> records = json.convertValue(rows, new TypeReference<>() {});
            Set<String> columns = Set.of(jdbc.queryForRowSet("SELECT * FROM " + table + " WHERE 1=0").getMetaData().getColumnNames());
            for (Map<String, Object> row : records) require(columns.containsAll(row.keySet()) && row.containsKey("id") && number(row, "id") > 0, "备份字段不合法");
            if (table.equals("sys_user")) for (Map<String, Object> account : records) {
                if ("STAFF".equals(text(account, "role"))) { account.put("role", "ADMIN"); account.put("account_type", "STAFF"); }
                if (text(account, "account_type").isBlank()) account.put("account_type", text(account, "role"));
                require(Set.of("ADMIN", "CUSTOMER").contains(text(account, "role")), "备份账号角色不合法");
            }
            restored.put(table, records);
        }
        require(restored.get("settings").size() == 1 && number(restored.get("settings").getFirst(), "id") == 1, "备份缺少系统配置");
        require(restored.get("sys_user").stream().anyMatch(user -> "ADMIN".equals(text(user, "role")) && flag(user, "enabled")), "备份须保留有效管理员");
        Path uploads = Path.of("uploads"); Files.createDirectories(uploads);
        for (Map.Entry<String, byte[]> entry : images.entrySet()) {
            Path target = uploads.resolve(entry.getKey());
            require(!Files.exists(target) || Arrays.equals(Files.readAllBytes(target), entry.getValue()), "同名图片内容冲突，已取消恢复");
        }
        for (Map.Entry<String, byte[]> entry : images.entrySet()) if (!Files.exists(uploads.resolve(entry.getKey()))) Files.write(uploads.resolve(entry.getKey()), entry.getValue(), StandardOpenOption.CREATE_NEW);
        transactions.executeWithoutResult(status -> {
            List<String> reversed = new ArrayList<>(TABLES); Collections.reverse(reversed);
            for (String table : reversed) jdbc.update("DELETE FROM " + table);
            for (String table : TABLES) for (Map<String, Object> row : restored.get(table)) store.insert(table, new LinkedHashMap<>(row));
            require(jdbc.queryForObject("SELECT COUNT(*) FROM vehicle WHERE mileage < 0", Long.class) == 0, "备份车辆里程不合法");
            require(jdbc.queryForObject("SELECT COUNT(*) FROM payment_record WHERE amount < 0", Long.class) == 0, "备份金额不合法");
        });
        String product = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());
        if ("H2".equals(product)) {
            for (String table : TABLES) if (!table.equals("settings")) {
                Long maximum = jdbc.queryForObject("SELECT COALESCE(MAX(id),0) FROM " + table, Long.class);
                jdbc.execute("ALTER TABLE " + table + " ALTER COLUMN id RESTART WITH " + (maximum + 1));
            }
        }
        com.yixing.auth.SessionFilter.invalidateSessions();
    }
}
