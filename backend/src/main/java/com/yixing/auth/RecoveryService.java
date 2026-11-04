package com.yixing.auth;

import com.yixing.data.Store;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.yixing.common.BusinessException.require;
import static com.yixing.common.Values.*;

@Service
public class RecoveryService {
    private final Store store;
    private final AccountService accounts;
    private final Access access;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final String dummyHash = encoder.encode(UUID.randomUUID().toString());
    public RecoveryService(Store store, AccountService accounts, Access access) {
        this.store = store; this.accounts = accounts; this.access = access;
    }
    private Map<String, Object> customer(String username) {
        return store.list("sys_user").stream().filter(user -> "CUSTOMER".equals(text(user, "role"))
            && flag(user, "enabled") && user.get("customer_id") != null
            && username.equalsIgnoreCase(text(user, "username"))).findFirst().orElse(null);
    }
    public Map<String, Object> question(Map<String, Object> input) {
        Map<String, Object> user = customer(required(input, "username", 30));
        String key = user != null && SecurityQuestions.configured(user) ? text(user, "security_question") : "";
        return map("security_question", key, "question", SecurityQuestions.OPTIONS.getOrDefault(key, ""));
    }
    @Transactional
    public Map<String, Object> security(long id, Map<String, Object> input) {
        accounts.lockAccounts();
        Map<String, Object> user = store.lock("sys_user", id);
        accounts.currentSession(user);
        require("CUSTOMER".equals(text(user, "role")) && flag(user, "enabled"), "仅客户账号可设置密保");
        require(encoder.matches(text(input, "password"), text(user, "password_hash")), "当前密码不正确");
        if (SecurityQuestions.configured(user)) {
            require(encoder.matches(text(input, "old_security_answer").trim(), text(user, "security_answer_hash")), "原密保答案不正确；忘记答案请提交重置工单");
        }
        Map<String, Object> saved = store.update("sys_user", id, map("security_question", SecurityQuestions.question(input),
            "security_answer_hash", encoder.encode(SecurityQuestions.answer(input, "security_answer"))));
        access.log("设置客户密保", text(user, "username"));
        return AuthController.publicUser(saved);
    }
    @Transactional
    public Map<String, Object> password(long id, Map<String, Object> input) {
        accounts.lockAccounts();
        Map<String, Object> user = store.lock("sys_user", id);
        accounts.currentSession(user);
        require(flag(user, "enabled") && encoder.matches(text(input, "old_password"), text(user, "password_hash")), "原密码不正确或账号已停用");
        if ("CUSTOMER".equals(text(user, "role"))) require(SecurityQuestions.configured(user), "请先设置密保问题");
        store.update("sys_user", id, map("password_hash", encoder.encode(password(input))));
        access.log("修改密码", text(user, "username"));
        return map("success", true);
    }
    private String password(Map<String, Object> input) {
        String password = required(input, "new_password", 64);
        require(password.length() >= 8 && password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72, "新密码至少 8 位，且不超过 72 字节");
        return password;
    }
    @Transactional
    public Map<String, Object> recover(Map<String, Object> input) {
        accounts.lockAccounts();
        Map<String, Object> user = customer(required(input, "username", 30));
        // 账号不存在时仍执行一次哈希校验，不向外返回账号是否存在。
        boolean matches = encoder.matches(text(input, "security_answer").trim(),
            user != null && SecurityQuestions.configured(user) ? text(user, "security_answer_hash") : dummyHash);
        require(user != null && SecurityQuestions.configured(user) && matches, "账号或密保答案不正确，或尚未设置密保");
        store.update("sys_user", number(user, "id"), map("password_hash", encoder.encode(password(input))));
        store.insert("operation_log", map("operator", "客户密保验证", "action", "密保找回密码", "target", user.get("username"), "created_at", now()));
        return map("success", true);
    }
    @Transactional
    public Map<String, Object> submitTicket(Map<String, Object> input) {
        String username = required(input, "username", 30), phone = required(input, "phone", 30),
            identity = required(input, "identity_no", 30), reason = required(input, "reason", 500);
        accounts.lockAccounts();
        Map<String, Object> user = customer(username);
        // 档案信息只是提交资格，不是批准凭证；必须由门店另外核验本人身份。
        if (user != null) {
            Map<String, Object> profile = store.get("customer", number(user, "customer_id"));
            boolean pending = store.list("password_reset_ticket").stream().anyMatch(ticket -> number(ticket, "user_id") == number(user, "id") && "PENDING".equals(text(ticket, "status")));
            if (phone.equals(text(profile, "phone")) && identity.equalsIgnoreCase(text(profile, "identity_no")) && !pending) {
                store.insert("password_reset_ticket", map("user_id", user.get("id"), "customer_id", profile.get("id"),
                    "username", user.get("username"), "contact", profile.get("phone"), "reason", reason, "status", "PENDING", "created_at", now()));
            }
        }
        return map("success", true, "message", "若账号与档案信息匹配，申请已提交；请联系门店核验身份，勿重复提交");
    }
    public List<Map<String, Object>> tickets() { access.admin(); return store.list("password_reset_ticket"); }
    @Transactional
    public Map<String, Object> review(long id, Map<String, Object> input) {
        access.admin(); accounts.lockAccounts();
        Map<String, Object> ticket = store.lock("password_reset_ticket", id);
        require("PENDING".equals(text(ticket, "status")), "该工单已处理，不能重复重置");
        String action = required(input, "action", 20), notes = required(input, "review_notes", 500);
        require(Set.of("APPROVE", "REJECT").contains(action), "工单处理方式不正确");
        if ("APPROVE".equals(action)) {
            require(flag(input, "verified"), "请先通过门店预留联系方式或到店证件核验客户身份");
            Map<String, Object> user = store.lock("sys_user", number(ticket, "user_id"));
            require("CUSTOMER".equals(text(user, "role")) && flag(user, "enabled")
                && number(user, "customer_id") == number(ticket, "customer_id")
                && text(user, "username").equals(text(ticket, "username")), "原客户账号已删除、停用或变更，请驳回此工单");
            // 重置密码同时清除旧密保，客户再次登录必须自行设置新答案。
            store.update("sys_user", number(user, "id"), map("password_hash", encoder.encode("88888888"),
                "security_question", null, "security_answer_hash", null));
        }
        Map<String, Object> saved = store.update("password_reset_ticket", id, map("status", "APPROVE".equals(action) ? "RESOLVED" : "REJECTED",
            "review_notes", notes, "reviewer", text(access.user(), "name"), "reviewed_at", now()));
        access.log("APPROVE".equals(action) ? "工单重置客户密码" : "驳回密码重置工单", text(ticket, "username"));
        return saved;
    }
}
