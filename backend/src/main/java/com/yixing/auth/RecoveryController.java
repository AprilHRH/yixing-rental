package com.yixing.auth;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import static com.yixing.common.Values.*;

@RestController
@RequestMapping("/api")
public class RecoveryController {
    private final RecoveryService recovery;
    private final Access access;
    private final RecoveryThrottle throttle;
    public RecoveryController(RecoveryService recovery, Access access, RecoveryThrottle throttle) { this.recovery = recovery; this.access = access; this.throttle = throttle; }
    private void attempt(Map<String, Object> data) {
        if (!throttle.answer(required(data, "username", 30))) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "验证次数过多，请 15 分钟后再试或联系门店");
    }
    @PostMapping("/auth/security") public Map<String, Object> security(@RequestBody Map<String, Object> data) { return recovery.security(number(access.user(), "id"), data); }
    @PostMapping("/auth/recovery/question") public Map<String, Object> question(@RequestBody Map<String, Object> data) { return recovery.question(data); }
    @PostMapping("/auth/recovery/password") public Map<String, Object> password(@RequestBody Map<String, Object> data) { attempt(data); return recovery.recover(data); }
    @PostMapping("/auth/recovery/tickets") public Map<String, Object> ticket(@RequestBody Map<String, Object> data) { attempt(data); return recovery.submitTicket(data); }
    @GetMapping("/customers/reset-tickets") public List<Map<String, Object>> tickets() { return recovery.tickets(); }
    @PostMapping("/customers/reset-tickets/{id}/review") public Map<String, Object> review(@PathVariable long id, @RequestBody Map<String, Object> data) { return recovery.review(id, data); }
}
