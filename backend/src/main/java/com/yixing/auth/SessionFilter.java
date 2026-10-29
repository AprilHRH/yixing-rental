package com.yixing.auth;

import com.yixing.data.Store;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import static com.yixing.common.Values.*;

@Component
public class SessionFilter extends OncePerRequestFilter {
    private static final java.util.concurrent.atomic.AtomicLong EPOCH = new java.util.concurrent.atomic.AtomicLong(1);
    @org.springframework.beans.factory.annotation.Value("${app.allowed-origins:http://127.0.0.1:5173,http://localhost:5173,http://127.0.0.1:8080,http://localhost:8080}")
    private String allowedOrigins;
    public static long epoch() { return EPOCH.get(); }
    public static void invalidateSessions() { EPOCH.incrementAndGet(); }
    private final Store store;
    public SessionFilter(Store store) { this.store = store; }
    private void fail(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) { chain.doFilter(request, response); return; }
        if (!"GET".equals(request.getMethod()) && !"XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            fail(response, 403, "请求来源不可信"); return;
        }
        String origin = request.getHeader("Origin");
        if (origin != null && !java.util.Arrays.asList(allowedOrigins.split(",")).contains(origin)) {
            fail(response, 403, "不允许的请求来源"); return;
        }
        if (path.equals("/api/auth/login") || path.equals("/api/auth/register")) { chain.doFilter(request, response); return; }
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null || !Long.valueOf(epoch()).equals(session.getAttribute("epoch"))) { fail(response, 401, "请先登录"); return; }
        Map<String, Object> user;
        try { user = store.get("sys_user", ((Number) session.getAttribute("userId")).longValue()); }
        catch (Exception exception) { fail(response, 401, "登录状态已失效"); return; }
        if (!flag(user, "enabled")) { session.invalidate(); fail(response, 401, "账号已停用"); return; }
        request.setAttribute("user", user);
        if ("CUSTOMER".equals(text(user, "role")) && !path.startsWith("/api/portal/") && !java.util.Set.of("/api/auth/me", "/api/auth/logout", "/api/auth/password").contains(path)) {
            fail(response, 403, "客户账号不能访问管理端数据"); return;
        }
        if ((path.startsWith("/api/system") || path.startsWith("/api/reports")) && !"ADMIN".equals(text(user, "role"))) {
            fail(response, 403, "该功能仅对管理员开放"); return;
        }
        chain.doFilter(request, response);
    }
}
