package com.yixing.auth;

import com.yixing.data.Store;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import static com.yixing.common.BusinessException.require;
import static com.yixing.common.Values.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final Store store;
    private final Access access;
    private final AccountService accounts;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    public AuthController(Store store, Access access, AccountService accounts) { this.store = store; this.access = access; this.accounts = accounts; }
    public static Map<String, Object> publicUser(Map<String, Object> user) {
        // 返回登录所需信息，不返回密码哈希，避免把整条账号记录暴露给浏览器。
        return map("id", user.get("id"), "name", user.get("name"), "username", user.get("username"), "role", user.get("role"), "enabled", user.get("enabled"), "account_type", user.get("account_type"), "customer_id", user.get("customer_id"));
    }
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> data, HttpServletRequest request) {
        String username = required(data, "username", 50);
        Map<String, Object> user = store.list("sys_user").stream().filter(entry -> username.equals(text(entry, "username"))).findFirst().orElse(null);
        // BCrypt 校验输入密码与数据库哈希，不保存明文密码，也不能直接比较哈希字符串。
        require(user != null && flag(user, "enabled") && encoder.matches(text(data, "password"), text(user, "password_hash")), "账号、密码不正确或账号已停用");
        require(text(data, "portal").isBlank() || text(data, "portal").equals(text(user, "role")), "请选择正确的登录身份（管理员或客户）");
        // 登录成功后换一个新会话，防止沿用登录前的会话编号。
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        request.getSession(true).setAttribute("userId", user.get("id"));
        request.getSession().setAttribute("epoch", SessionFilter.epoch());
        request.getSession().setAttribute("passwordHash", user.get("password_hash"));
        return publicUser(user);
    }
    @PostMapping("/register") public Map<String, Object> register(@RequestBody Map<String, Object> data) { return accounts.register(data); }
    @GetMapping("/me") public Map<String, Object> me() { return publicUser(access.user()); }
    @PostMapping("/logout") public Map<String, Object> logout(HttpServletRequest request) { request.getSession().invalidate(); return map("success", true); }
    @DeleteMapping("/account")
    public Map<String, Object> close(@RequestBody Map<String, Object> data, HttpServletRequest request) {
        require("CUSTOMER".equals(text(access.user(), "role")), "仅客户账号可以自行注销");
        require("CLOSE".equals(text(data, "confirmation")), "请确认注销账号");
        accounts.close(number(access.user(), "id"), required(data, "password", 64));
        request.getSession().invalidate();
        return map("success", true);
    }
    @PostMapping("/password")
    public Map<String, Object> password(@RequestBody Map<String, Object> data, HttpServletRequest request) {
        // 改密需验证原密码，保存新哈希后注销当前会话，要求重新登录。
        Map<String, Object> user = access.user();
        require(encoder.matches(text(data, "old_password"), text(user, "password_hash")), "原密码不正确");
        String password = required(data, "new_password", 64);
        require(password.length() >= 8, "新密码至少 8 位");
        store.update("sys_user", number(user, "id"), map("password_hash", encoder.encode(password)));
        access.log("修改密码", text(user, "username"));
        request.getSession().invalidate();
        return map("success", true);
    }
}
