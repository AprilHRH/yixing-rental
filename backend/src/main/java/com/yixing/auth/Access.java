package com.yixing.auth;

import com.yixing.data.Store;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import static com.yixing.common.BusinessException.require;
import static com.yixing.common.Values.*;

@Component
public class Access {
    private final Store store;
    public Access(Store store) { this.store = store; }
    public Map<String, Object> user() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = attributes.getRequest();
        return (Map<String, Object>) request.getAttribute("user");
    }
    public void admin() {
        if (!"ADMIN".equals(text(user(), "role"))) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "仅管理员可以执行此操作");
    }
    public void log(String action, String target) {
        store.insert("operation_log", map("operator", text(user(), "name"), "action", action, "target", target, "created_at", now()));
    }
}
