package com.yixing.customer;

import com.yixing.auth.Access;
import com.yixing.auth.AccountService;
import com.yixing.auth.AuthController;
import com.yixing.data.Store;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.yixing.common.BusinessException.require;
import static com.yixing.common.Values.*;

@Service
public class CustomerService {
    private final Store store;
    private final Access access;
    private final AccountService accounts;
    public CustomerService(Store store, Access access, AccountService accounts) { this.store = store; this.access = access; this.accounts = accounts; }
    public List<Map<String, Object>> accounts() {
        access.admin();
        return store.list("sys_user").stream().filter(user -> "CUSTOMER".equals(text(user, "role"))).map(AuthController::publicUser).toList();
    }
    @Transactional
    public Map<String, Object> create(Map<String, Object> input) {
        accounts.lockAccounts();
        Map<String, Object> profile = save(null, input);
        // 管理端建档时同时开通账号，不再出现新客户只有档案却无法登录的情况。
        String username = text(input, "username").isBlank() ? "customer" + profile.get("id") : text(input, "username");
        accounts.username(map("username", username), null);
        String password = text(input, "password").isBlank() ? "88888888" : text(input, "password");
        require(password.length() >= 8, "密码至少 8 位");
        store.insert("sys_user", map("username", username, "password_hash", new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password),
            "name", profile.get("name"), "role", "CUSTOMER", "account_type", "CUSTOMER", "customer_id", profile.get("id"), "enabled", true));
        access.log("开通客户账号", username);
        return profile;
    }
    @Transactional
    public Map<String, Object> save(Long id, Map<String, Object> input) {
        accounts.lockAccounts();
        Map<String, Object> data = map("name", required(input, "name", 60), "identity_no", required(input, "identity_no", 30), "phone", required(input, "phone", 30), "license_no", required(input, "license_no", 80), "license_expiry", date(input, "license_expiry").toString(), "category", text(input, "category"), "restricted", flag(input, "restricted"), "notes", text(input, "notes"));
        require(text(input, "identity_no").matches("[A-Za-z0-9-]{6,30}"), "证件号须为 6 至 30 位字母、数字或连字符");
        require(text(input, "phone").matches("[0-9+ -]{7,20}"), "联系方式格式不正确");
        require(Set.of("NORMAL", "VIP").contains(text(input, "category")), "客户分类不正确");
        require(text(input, "notes").length() <= 500, "备注最多 500 字");
        if (id != null) store.lock("customer", id);
        Map<String, Object> result = id == null ? store.insert("customer", data) : store.update("customer", id, data);
        if (id != null) store.list("sys_user").stream().filter(user -> user.get("customer_id") != null && number(user, "customer_id") == id)
            .forEach(user -> store.update("sys_user", number(user, "id"), map("name", result.get("name"))));
        access.log(id == null ? "新增客户" : "编辑客户", text(result, "name")); return result;
    }
    public void eligible(Map<String, Object> customer, LocalDate endDate) {
        // 驾驶证不仅今天要有效，还必须覆盖计划还车日；限制租赁的客户不能下单或取车。
        require(!flag(customer, "restricted"), "该客户已被限制租赁");
        require(!date(customer, "license_expiry").isBefore(endDate) && !date(customer, "license_expiry").isBefore(LocalDate.now()), "驾驶证已过期或无法覆盖租期");
    }
}
