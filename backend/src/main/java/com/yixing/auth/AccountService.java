package com.yixing.auth;

import com.yixing.data.Store;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.yixing.common.BusinessException.require;
import static com.yixing.common.Values.*;

@Service
public class AccountService {
    private final Store store;
    public AccountService(Store store) { this.store = store; }
    public void username(Map<String, Object> input, Long excludedId) {
        String username = required(input, "username", 30);
        require(username.matches("[a-zA-Z0-9_]{3,30}"), "账号须为 3 至 30 位字母、数字或下划线");
        require(store.list("sys_user").stream().noneMatch(user -> username.equalsIgnoreCase(text(user, "username")) && (excludedId == null || number(user, "id") != excludedId)), "该账号已被使用");
    }
    @Transactional
    public Map<String, Object> register(Map<String, Object> input) {
        require(text(input, "role").isBlank() || "CUSTOMER".equals(text(input, "role")), "公开注册仅支持客户账号");
        username(input, null);
        String password = required(input, "password", 64);
        require(password.length() >= 8, "密码至少 8 位");
        String identity = required(input, "identity_no", 30), phone = required(input, "phone", 30);
        require(identity.matches("[A-Za-z0-9-]{6,30}"), "证件号须为 6 至 30 位字母、数字或连字符");
        require(phone.matches("[0-9+ -]{7,20}"), "联系方式格式不正确");
        require(!date(input, "license_expiry").isBefore(LocalDate.now()), "驾驶证已过期");
        require(store.list("customer").stream().noneMatch(customer -> identity.equalsIgnoreCase(text(customer, "identity_no"))), "该证件已建档，请联系管理员开通或找回客户账号");
        Map<String, Object> customer = store.insert("customer", map("name", required(input, "name", 60), "identity_no", identity, "phone", phone, "license_no", required(input, "license_no", 80), "license_expiry", date(input, "license_expiry").toString(), "category", "NORMAL", "restricted", false, "notes", ""));
        Map<String, Object> user = store.insert("sys_user", map("username", text(input, "username"), "password_hash", new BCryptPasswordEncoder().encode(password), "name", customer.get("name"), "role", "CUSTOMER", "account_type", "CUSTOMER", "customer_id", customer.get("id"), "enabled", true));
        store.insert("operation_log", map("operator", "客户自主注册", "action", "开通客户账号", "target", text(user, "username"), "created_at", now()));
        return AuthController.publicUser(user);
    }
}
