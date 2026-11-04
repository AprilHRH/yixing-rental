package com.yixing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yixing.auth.RecoveryThrottle;
import com.yixing.data.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.yixing.common.Values.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties = {"spring.datasource.url=${TEST_RECOVERY_DATABASE_URL:jdbc:h2:mem:recoverytest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}", "spring.main.banner-mode=off"})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AccountRecoveryTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired Store store;
    @Autowired AccountSeed seed;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired com.yixing.auth.AccountService accounts;
    MvcResult send(MockHttpServletRequestBuilder builder, MockHttpSession session, Map<String, Object> data) throws Exception {
        builder.header("X-Requested-With", "XMLHttpRequest").contentType("application/json").content(json.writeValueAsBytes(data));
        if (session != null) builder.session(session);
        return mvc.perform(builder).andReturn();
    }
    Map<String, Object> ok(MvcResult result) throws Exception {
        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return json.readValue(result.getResponse().getContentAsByteArray(), Map.class);
    }
    void status(int status, MvcResult result) throws Exception { assertEquals(status, result.getResponse().getStatus(), result.getResponse().getContentAsString()); }
    MockHttpSession login(String username, String password, String role) throws Exception {
        var result = send(post("/api/auth/login"), null, map("username", username, "password", password, "portal", role));
        ok(result); return (MockHttpSession) result.getRequest().getSession();
    }
    MockHttpSession admin() throws Exception { return login("admin", "88888888", "ADMIN"); }
    Map<String, Object> draft() {
        String token = UUID.randomUUID().toString().substring(0, 8);
        return map("username", "recover_" + token, "password", "Original@12345", "name", "密保测试", "phone", "13800006666",
            "identity_no", "TEST-ID-" + token, "license_no", "TEST-DL", "license_expiry", LocalDate.now().plusYears(3).toString(),
            "role", "CUSTOMER", "security_question", "birth_city", "security_answer", "南京测试");
    }
    Map<String, Object> register(Map<String, Object> input) throws Exception { return ok(send(post("/api/auth/register"), null, input)); }
    List<Map<String, Object>> list(String path, MockHttpSession session) throws Exception {
        var result = mvc.perform(get(path).session(session)).andReturn();
        status(200, result); return json.readValue(result.getResponse().getContentAsByteArray(), List.class);
    }
    Map<String, Object> ticket(Map<String, Object> draft) { return map("username", draft.get("username"), "phone", draft.get("phone"), "identity_no", draft.get("identity_no"), "reason", "忘记密保答案，申请到店核验"); }
    Map<String, Object> pending(Map<String, Object> draft) throws Exception {
        return list("/api/customers/reset-tickets", admin()).stream().filter(row -> text(row, "username").equals(text(draft, "username")) && "PENDING".equals(text(row, "status"))).findFirst().orElseThrow();
    }
    @Test @Order(1) void freshDatabaseHasExactlySevenLinkedAccountsAndNoOrders() throws Exception {
        assertEquals(7, store.list("sys_user").size()); assertEquals(5, store.list("customer").size());
        assertTrue(store.list("rental_order").isEmpty()); assertTrue(store.list("payment_record").isEmpty());
        for (String username : List.of("admin", "staff", "customer01", "customer02", "customer03", "customer04", "customer05")) {
            var session = login(username, "88888888", username.startsWith("customer") ? "CUSTOMER" : "ADMIN");
            var user = ok(mvc.perform(get("/api/auth/me").session(session)).andReturn());
            assertFalse(user.containsKey("password_hash")); assertFalse(user.containsKey("security_answer_hash"));
        }
    }
    @Test void managementListsAreSeparatedAndCustomerRoutesCannotManageTeam() throws Exception {
        var session = admin();
        var overview = ok(mvc.perform(get("/api/system").session(session)).andReturn());
        assertTrue(((List<Map<String, Object>>) overview.get("users")).stream().allMatch(user -> "ADMIN".equals(user.get("role"))));
        assertTrue(list("/api/customers/accounts", session).stream().allMatch(user -> "CUSTOMER".equals(user.get("role"))));
        var input = draft(); input.put("account_type", "CUSTOMER"); input.put("enabled", true);
        status(400, send(post("/api/system/users"), session, input));
        var adminUser = store.list("sys_user").stream().filter(user -> "admin".equals(user.get("username"))).findFirst().orElseThrow();
        status(400, send(delete("/api/customers/accounts/" + adminUser.get("id")), session, map("confirmation", "DELETE")));
        var registered = register(draft());
        var customer = login(text(registered, "username"), "Original@12345", "CUSTOMER");
        for (String path : List.of("/api/customers/accounts", "/api/customers/reset-tickets", "/api/system"))
            status(403, mvc.perform(get(path).session(customer)).andReturn());
    }
    @Test void customerCreationAndAccountAreAtomicAndNamesStayInSync() throws Exception {
        var session = admin(); var input = draft();
        input.put("category", "NORMAL"); input.put("restricted", false);
        var profile = ok(send(post("/api/customers"), session, input));
        var user = list("/api/customers/accounts", session).stream().filter(row -> number(row, "customer_id") == number(profile, "id")).findFirst().orElseThrow();
        login(text(user, "username"), "Original@12345", "CUSTOMER");
        input.put("identity_no", "TEST-DUP-" + UUID.randomUUID().toString().substring(0, 8));
        long count = store.list("customer").size();
        status(400, send(post("/api/customers"), session, input)); assertEquals(count, store.list("customer").size());
        profile.put("name", "更正后的客户姓名");
        ok(send(put("/api/customers/" + profile.get("id")), session, profile));
        assertEquals("更正后的客户姓名", store.get("sys_user", number(user, "id")).get("name"));
    }
    @Test void registrationRequiresValidSecurityAndNeverExposesAnswer() throws Exception {
        var input = draft(); input.remove("security_answer");
        status(400, send(post("/api/auth/register"), null, input));
        input.put("security_answer", "南京测试"); input.put("security_question", "made_up");
        status(400, send(post("/api/auth/register"), null, input)); input.put("security_question", "birth_city");
        var user = register(input);
        assertTrue(flag(user, "security_configured")); assertFalse(user.containsKey("security_answer_hash"));
        assertTrue(new BCryptPasswordEncoder().matches("南京测试", text(store.get("sys_user", number(user, "id")), "security_answer_hash")));
        assertFalse(json.writeValueAsString(list("/api/customers/accounts", admin())).contains("security_answer_hash"));
    }
    @Test void firstLoginMustSetSecurityAndCannotReplaceWithoutOldAnswer() throws Exception {
        var admin = admin(); var input = draft(); input.put("category", "NORMAL"); input.put("restricted", false);
        var profile = ok(send(post("/api/customers"), admin, input));
        var session = login(text(input, "username"), "Original@12345", "CUSTOMER");
        status(400, send(post("/api/auth/password"), session, map("old_password", "Original@12345", "new_password", "Changed@12345")));
        status(403, send(post("/api/portal/orders"), session, map()));
        var setup = map("password", "bad", "security_question", "first_school", "security_answer", "测试小学");
        status(400, send(post("/api/auth/security"), session, setup));
        setup.put("password", "Original@12345"); ok(send(post("/api/auth/security"), session, setup));
        status(400, send(post("/api/auth/security"), session, setup));
        setup.put("old_security_answer", "测试小学"); setup.put("security_answer", "新测试小学");
        ok(send(post("/api/auth/security"), session, setup));
        assertNotNull(profile.get("id"));
    }
    @Test void selfPasswordChangeChecksOldPasswordAndInvalidatesOtherSessions() throws Exception {
        var input = draft(); register(input);
        var first = login(text(input, "username"), "Original@12345", "CUSTOMER");
        var second = login(text(input, "username"), "Original@12345", "CUSTOMER");
        status(400, send(post("/api/auth/password"), first, map("old_password", "bad", "new_password", "Changed@12345")));
        ok(send(post("/api/auth/password"), first, map("old_password", "Original@12345", "new_password", "Changed@12345")));
        assertTrue(first.isInvalid()); status(401, mvc.perform(get("/api/auth/me").session(second)).andReturn());
        login(text(input, "username"), "Changed@12345", "CUSTOMER");
    }
    @Test void answerRecoveryRejectsWrongAnswersAndInvalidatesSessions() throws Exception {
        var input = draft(); register(input); var old = login(text(input, "username"), "Original@12345", "CUSTOMER");
        assertEquals("你出生的城市是哪里？", ok(send(post("/api/auth/recovery/question"), null, map("username", input.get("username")))).get("question"));
        var recovery = map("username", input.get("username"), "security_answer", "错误答案", "new_password", "Recovered@12345");
        status(400, send(post("/api/auth/recovery/password"), null, recovery));
        recovery.put("security_answer", " 南京测试 "); ok(send(post("/api/auth/recovery/password"), null, recovery));
        status(401, mvc.perform(get("/api/auth/me").session(old)).andReturn()); login(text(input, "username"), "Recovered@12345", "CUSTOMER");
        status(400, send(post("/api/auth/recovery/password"), null, map("username", "admin", "security_answer", "南京测试", "new_password", "Hacked@12345")));
    }
    @Test void wrongOrUnknownTicketDetailsReturnSameGenericResultAndNoReset() throws Exception {
        var input = draft(); register(input); var wrong = ticket(input); wrong.put("phone", "13900000000");
        var invalid = ok(send(post("/api/auth/recovery/tickets"), null, wrong));
        wrong.put("username", "unknown_" + UUID.randomUUID().toString().substring(0, 8));
        assertEquals(invalid, ok(send(post("/api/auth/recovery/tickets"), null, wrong)));
        assertTrue(store.list("password_reset_ticket").stream().noneMatch(row -> input.get("username").equals(row.get("username"))));
        login(text(input, "username"), "Original@12345", "CUSTOMER");
    }
    @Test void approvalRequiresHumanVerificationResetsDefaultAndClearsSecurity() throws Exception {
        var input = draft(); register(input); var old = login(text(input, "username"), "Original@12345", "CUSTOMER");
        ok(send(post("/api/auth/recovery/tickets"), null, ticket(input))); ok(send(post("/api/auth/recovery/tickets"), null, ticket(input)));
        var ticket = pending(input); String route = "/api/customers/reset-tickets/" + ticket.get("id") + "/review";
        assertEquals(1, store.list("password_reset_ticket").stream().filter(row -> input.get("username").equals(row.get("username"))).count());
        var review = map("action", "APPROVE", "verified", false, "review_notes", "到店核验演示证件");
        status(403, send(post(route), old, review)); status(401, send(post(route), null, review)); status(400, send(post(route), admin(), review));
        review.put("verified", true); assertEquals("RESOLVED", ok(send(post(route), admin(), review)).get("status"));
        status(400, send(post(route), admin(), review)); status(401, mvc.perform(get("/api/auth/me").session(old)).andReturn());
        var session = login(text(input, "username"), "88888888", "CUSTOMER");
        assertFalse(flag(ok(mvc.perform(get("/api/auth/me").session(session)).andReturn()), "security_configured"));
        status(403, send(post("/api/portal/orders"), session, map()));
        ok(send(post("/api/auth/security"), session, map("password", "88888888", "security_question", "favorite_food", "security_answer", "新的答案")));
    }
    @Test void rejectionDoesNotChangePasswordAndAllowsNewTicket() throws Exception {
        var input = draft(); register(input); ok(send(post("/api/auth/recovery/tickets"), null, ticket(input)));
        var ticket = pending(input);
        var review = map("action", "REJECT", "review_notes", "未能完成到店身份核验");
        assertEquals("REJECTED", ok(send(post("/api/customers/reset-tickets/" + ticket.get("id") + "/review"), admin(), review)).get("status"));
        login(text(input, "username"), "Original@12345", "CUSTOMER");
        ok(send(post("/api/auth/recovery/tickets"), null, ticket(input))); assertNotEquals(ticket.get("id"), pending(input).get("id"));
    }
    @Test void renamedOrDisabledAccountCannotBeResetByStaleTicket() throws Exception {
        var input = draft(); var user = register(input); ok(send(post("/api/auth/recovery/tickets"), null, ticket(input)));
        var ticket = pending(input);
        user.put("enabled", false); user.put("password", "");
        ok(send(put("/api/customers/accounts/" + user.get("id")), admin(), user));
        status(400, send(post("/api/customers/reset-tickets/" + ticket.get("id") + "/review"), admin(),
            map("action", "APPROVE", "verified", true, "review_notes", "测试停用账号保护")));
    }
    @Test void recoveryAttemptsAreBoundedPerAccountAndSource() throws Exception {
        var input = draft(); register(input);
        for (int i = 0; i < 5; i++) status(400, send(post("/api/auth/recovery/password"), null,
            map("username", input.get("username"), "security_answer", "错误", "new_password", "Changed@12345")));
        status(429, send(post("/api/auth/recovery/password"), null,
            map("username", input.get("username"), "security_answer", "南京测试", "new_password", "Changed@12345")));
        RecoveryThrottle limiter = new RecoveryThrottle();
        for (int i = 0; i < 60; i++) assertTrue(limiter.allow("source"));
        assertFalse(limiter.allow("source"));
    }
    @Test void requestAlreadyPastFilterCannotWriteAfterPasswordResetWinsLock() throws Exception {
        var input = draft(); var created = register(input);
        var oldIdentity = new LinkedHashMap<>(store.get("sys_user", number(created, "id")));
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setAttribute("user", oldIdentity);
        var previous = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        try {
            org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
            store.update("sys_user", number(created, "id"), map("password_hash", new BCryptPasswordEncoder().encode("88888888")));
            assertThrows(com.yixing.common.BusinessException.class, () ->
                new TransactionTemplate(manager).execute(transaction -> accounts.lockCustomerAccount(number(created, "id"))));
        } finally {
            org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(previous);
        }
    }
    @Test @Order(99) void explicitResetPreservesFleetAndCannotRepeatDuringNormalStartup() throws Exception {
        var reset = new AccountReset(jdbc, store, seed);
        assertThrows(IllegalStateException.class, () -> reset.run(new DefaultApplicationArguments()));
        long vehicles = store.list("vehicle").size(), maintenance = store.list("maintenance_record").size();
        var session = admin(); var input = draft(); input.put("category", "NORMAL"); input.put("restricted", false);
        var profile = ok(send(post("/api/customers"), session, input));
        var car = store.list("vehicle").stream().filter(row -> "AVAILABLE".equals(row.get("status"))).findFirst().orElseThrow();
        var order = ok(send(post("/api/orders"), session, map("customer_id", profile.get("id"), "vehicle_id", car.get("id"),
            "pickup_branch", car.get("branch_id"), "return_branch", car.get("branch_id"), "billing_mode", "DAILY", "expected_mileage", 0,
            "planned_start", LocalDateTime.now().plusDays(3).toString(), "planned_end", LocalDateTime.now().plusDays(4).toString())));
        ok(send(post("/api/orders/" + order.get("id") + "/collect"), session, map("kind", "DEPOSIT", "amount", order.get("deposit"), "request_id", UUID.randomUUID().toString())));
        new TransactionTemplate(manager).executeWithoutResult(transaction -> reset.run(new DefaultApplicationArguments("--spring.main.web-application-type=none")));
        assertEquals(7, store.list("sys_user").size()); assertEquals(5, store.list("customer").size());
        assertTrue(store.list("rental_order").isEmpty()); assertTrue(store.list("payment_record").isEmpty()); assertTrue(store.list("password_reset_ticket").isEmpty());
        assertEquals(vehicles, store.list("vehicle").size()); assertEquals(maintenance, store.list("maintenance_record").size());
        new DemoData(store, seed).run(new DefaultApplicationArguments());
        assertEquals(7, store.list("sys_user").size()); assertEquals(5, store.list("customer").size());
    }
}
