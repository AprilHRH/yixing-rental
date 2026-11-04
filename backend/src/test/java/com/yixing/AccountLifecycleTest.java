package com.yixing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yixing.data.Store;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static com.yixing.common.Values.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

// 每轮使用单独的测试库，不修改日常演示库中的账号。
@SpringBootTest(properties = {"spring.datasource.url=${TEST_ACCOUNT_DATABASE_URL:jdbc:h2:mem:accounttest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}", "spring.main.banner-mode=off", "app.demo-history=true"})
@AutoConfigureMockMvc
class AccountLifecycleTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired Store store;
    static final String PASSWORD = "Client@12345";

    MvcResult send(MockHttpServletRequestBuilder builder, MockHttpSession session, Map<String, Object> data) throws Exception {
        builder.header("X-Requested-With", "XMLHttpRequest").contentType("application/json").content(json.writeValueAsBytes(data));
        if (session != null) builder.session(session);
        return mvc.perform(builder).andReturn();
    }
    Map<String, Object> ok(MvcResult result) throws Exception {
        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return json.readValue(result.getResponse().getContentAsByteArray(), Map.class);
    }
    void rejects(MvcResult result, int status) throws Exception { assertEquals(status, result.getResponse().getStatus(), result.getResponse().getContentAsString()); }
    MockHttpSession login(String username, String password, String role) throws Exception {
        MvcResult result = send(post("/api/auth/login"), null, map("username", username, "password", password, "portal", role));
        Map<String, Object> account = ok(result);
        if (role.equals("CUSTOMER") && !flag(account, "security_configured")) {
            ok(send(post("/api/auth/security"), (MockHttpSession) result.getRequest().getSession(),
                map("password", password, "security_question", "birth_city", "security_answer", "测试城市")));
        }
        return (MockHttpSession) result.getRequest().getSession();
    }
    MockHttpSession admin() throws Exception { return login("admin", "88888888", "ADMIN"); }
    String suffix() { return UUID.randomUUID().toString().substring(0, 8); }
    Map<String, Object> input(String type) {
        String token = suffix();
        return map("username", "acct_" + token, "name", "账号测试", "password", PASSWORD,
            "role", type.equals("CUSTOMER") ? "CUSTOMER" : "ADMIN", "account_type", type, "enabled", true,
            "phone", "13800009999", "identity_no", "TEST-ID-" + token, "license_no", "TEST-LICENSE-" + token,
            "license_expiry", LocalDate.now().plusYears(3).toString());
    }
    String path(long id) { return ("CUSTOMER".equals(text(store.get("sys_user", id), "role")) ? "/api/customers/accounts/" : "/api/system/users/") + id; }
    Map<String, Object> create(MockHttpSession admin, Map<String, Object> input) throws Exception { return ok(send(post("CUSTOMER".equals(text(input, "role")) ? "/api/customers/accounts" : "/api/system/users"), admin, input)); }
    MvcResult remove(MockHttpSession session, long id) throws Exception { return send(delete(path(id)), session, map("confirmation", "DELETE")); }
    MvcResult close(MockHttpSession session, String password) throws Exception { return send(delete("/api/auth/account"), session, map("confirmation", "CLOSE", "password", password)); }
    MvcResult me(MockHttpSession session) throws Exception { return mvc.perform(get("/api/auth/me").session(session)).andReturn(); }

    @Test void allThreeTypesCanBeCreatedEditedAndDeleted() throws Exception {
        MockHttpSession manager = admin();
        for (String type : List.of("ADMIN", "STAFF", "CUSTOMER")) {
            Map<String, Object> draft = input(type), account = create(manager, draft);
            long id = number(account, "id");
            assertEquals(type, account.get("account_type"));
            assertFalse(account.containsKey("password_hash"));
            MockHttpSession oldSession = login(text(draft, "username"), PASSWORD, text(draft, "role"));
            Map<String, Object> edit = new LinkedHashMap<>(account);
            edit.put("username", "renamed_" + suffix()); edit.put("name", "修改后的姓名"); edit.put("password", "");
            Map<String, Object> updated = ok(send(put(path(id)), manager, edit));
            assertEquals("修改后的姓名", updated.get("name")); ok(me(oldSession));
            if (type.equals("CUSTOMER")) assertEquals("修改后的姓名", store.get("customer", number(account, "customer_id")).get("name"));
            ok(remove(manager, id));
            rejects(me(oldSession), 401);
            rejects(send(post("/api/auth/login"), null, map("username", edit.get("username"), "password", PASSWORD)), 400);
            if (type.equals("CUSTOMER")) assertNotNull(store.get("customer", number(account, "customer_id")));
        }
    }
    @Test void closureRequiresPasswordConfirmationAndCustomerIdentity() throws Exception {
        MockHttpSession manager = admin();
        Map<String, Object> account = create(manager, input("CUSTOMER"));
        MockHttpSession first = login(text(account, "username"), PASSWORD, "CUSTOMER");
        MockHttpSession second = login(text(account, "username"), PASSWORD, "CUSTOMER");
        rejects(send(delete("/api/auth/account"), first, map("password", PASSWORD)), 400);
        rejects(close(first, "WrongPassword"), 400);
        ok(me(first));
        rejects(close(manager, "88888888"), 400);
        ok(close(first, PASSWORD));
        rejects(me(second), 401);
        assertTrue(first.isInvalid());
        assertNotNull(store.get("customer", number(account, "customer_id")));
        assertTrue(store.list("operation_log").stream().anyMatch(log -> "客户注销账号".equals(text(log, "action")) && account.get("username").equals(log.get("target"))));
    }
    @Test void customerCannotDeleteAccountsAndAnonymousCannotClose() throws Exception {
        Map<String, Object> account = create(admin(), input("CUSTOMER"));
        MockHttpSession customer = login(text(account, "username"), PASSWORD, "CUSTOMER");
        rejects(remove(customer, number(account, "id")), 403);
        rejects(send(delete("/api/auth/account"), null, map("password", PASSWORD, "confirmation", "CLOSE")), 401);
        ok(close(customer, PASSWORD));
    }
    @Test void deletionRequiresConfirmationAndCannotDeleteCurrentLogin() throws Exception {
        MockHttpSession manager = admin();
        Map<String, Object> target = create(manager, input("STAFF"));
        rejects(send(delete("/api/system/users/" + target.get("id")), manager, map()), 400);
        rejects(remove(manager, number(ok(me(manager)), "id")), 400);
        ok(remove(manager, number(target, "id")));
    }
    @Test void resetPasswordAndDisableInvalidateOldSessions() throws Exception {
        MockHttpSession manager = admin();
        Map<String, Object> target = create(manager, input("STAFF"));
        long id = number(target, "id");
        MockHttpSession old = login(text(target, "username"), PASSWORD, "ADMIN");
        Map<String, Object> edit = new LinkedHashMap<>(target);
        edit.put("password", "Changed@12345");
        ok(send(put("/api/system/users/" + id), manager, edit));
        rejects(me(old), 401);
        MockHttpSession fresh = login(text(target, "username"), "Changed@12345", "ADMIN");
        edit.put("password", ""); edit.put("enabled", false);
        ok(send(put("/api/system/users/" + id), manager, edit));
        rejects(me(fresh), 401);
        ok(remove(manager, id));
    }
    @Test void unfinishedOrdersBlockBothClosureAndManagedDeletion() throws Exception {
        MockHttpSession manager = admin();
        Map<String, Object> account = create(manager, input("CUSTOMER"));
        MockHttpSession customer = login(text(account, "username"), PASSWORD, "CUSTOMER");
        Map<String, Object> car = ok(send(post("/api/vehicles"), manager, map("plate", "沪A" + suffix().substring(0, 6).toUpperCase(), "brand", "测试", "model", "账号测试车", "category", "经济轿车", "color", "白", "seats", 5, "purchase_date", LocalDate.now().minusDays(1).toString(), "license_no", "TEST", "mileage", 0, "branch_id", 1, "pricing_id", 1, "image", "", "next_service_mileage", 5000)));
        Map<String, Object> order = ok(send(post("/api/portal/orders"), customer, map("vehicle_id", car.get("id"), "return_branch", 1, "planned_start", LocalDateTime.now().plusDays(3).toString(), "planned_end", LocalDateTime.now().plusDays(5).toString(), "billing_mode", "DAILY", "expected_mileage", 0)));
        long orderId = number(order, "id");
        // 三种未结清状态使用同一条独立测试订单，分别验证账号规则。
        for (String status : List.of("RESERVED", "ACTIVE", "RETURNED")) {
            store.update("rental_order", orderId, map("status", status));
            rejects(close(customer, PASSWORD), 400);
            rejects(remove(manager, number(account, "id")), 400);
            ok(me(customer));
        }
        store.update("rental_order", orderId, map("status", "RESERVED"));
        ok(send(post("/api/portal/orders/" + orderId + "/cancel"), customer, map()));
        ok(close(customer, PASSWORD));
        assertEquals("CANCELLED", store.get("rental_order", orderId).get("status"));
        assertNotNull(store.get("customer", number(account, "customer_id")));
    }
    @Test void completedOrdersAndPaymentsSurviveManagedDeletion() throws Exception {
        MockHttpSession manager = admin();
        Map<String, Object> account = create(manager, input("CUSTOMER"));
        // 复制一条已结算的样例订单和流水，只替换本测试的归属和唯一编号。
        Map<String, Object> order = new LinkedHashMap<>(store.list("rental_order").stream().filter(row -> "COMPLETED".equals(text(row, "status"))).findFirst().orElseThrow());
        long sourceId = number(order, "id");
        order.remove("id"); order.put("customer_id", account.get("customer_id")); order.put("order_no", "ACCT-" + suffix());
        long orderId = number(store.insert("rental_order", order), "id");
        Map<String, Object> payment = new LinkedHashMap<>(store.list("payment_record").stream().filter(row -> number(row, "order_id") == sourceId).findFirst().orElseThrow());
        payment.remove("id"); payment.put("order_id", orderId); payment.put("request_id", UUID.randomUUID().toString());
        long paymentId = number(store.insert("payment_record", payment), "id");
        ok(remove(manager, number(account, "id")));
        assertEquals(number(account, "customer_id"), number(store.get("rental_order", orderId), "customer_id"));
        assertEquals(orderId, number(store.get("payment_record", paymentId), "order_id"));
    }
    @Test void failedInlineCreationRollsBackAndCustomerCannotBeRebound() throws Exception {
        MockHttpSession manager = admin();
        Map<String, Object> draft = input("CUSTOMER");
        draft.put("password", "short");
        rejects(send(post("/api/customers/accounts"), manager, draft), 400);
        assertTrue(store.list("customer").stream().noneMatch(row -> draft.get("identity_no").equals(row.get("identity_no"))));
        draft.put("password", PASSWORD);
        Map<String, Object> account = create(manager, draft), duplicate = input("CUSTOMER");
        duplicate.put("customer_id", account.get("customer_id"));
        rejects(send(post("/api/customers/accounts"), manager, duplicate), 400);
        Map<String, Object> edit = new LinkedHashMap<>(account);
        edit.put("customer_id", 1);
        rejects(send(put(path(number(account, "id"))), manager, edit), 400);
        edit.put("customer_id", account.get("customer_id")); edit.put("role", "ADMIN"); edit.put("account_type", "ADMIN");
        rejects(send(put(path(number(account, "id"))), manager, edit), 400);
        ok(remove(manager, number(account, "id")));
    }
    @Test void staffCannotRemoveDisableOrDemoteLastTrueAdministrator() throws Exception {
        MockHttpSession manager = admin(), staff = login("staff", "88888888", "ADMIN");
        Map<String, Object> original = ok(me(manager)), edit = new LinkedHashMap<>(original);
        long id = number(original, "id");
        rejects(remove(staff, id), 400);
        edit.put("enabled", false);
        rejects(send(put("/api/system/users/" + id), staff, edit), 400);
        edit.put("enabled", true); edit.put("account_type", "STAFF");
        rejects(send(put("/api/system/users/" + id), staff, edit), 400);
        ok(me(manager));
    }
    @Test void concurrentReservationAndClosureCannotBothSucceed() throws Exception {
        MockHttpSession manager = admin();
        Map<String, Object> account = create(manager, input("CUSTOMER"));
        MockHttpSession bookingSession = login(text(account, "username"), PASSWORD, "CUSTOMER");
        MockHttpSession closingSession = login(text(account, "username"), PASSWORD, "CUSTOMER");
        Map<String, Object> car = ok(send(post("/api/vehicles"), manager, map("plate", "沪C" + suffix().substring(0, 6).toUpperCase(), "brand", "测试", "model", "并发测试车", "category", "经济轿车", "color", "白", "seats", 5, "purchase_date", LocalDate.now().minusDays(1).toString(), "license_no", "TEST", "mileage", 0, "branch_id", 1, "pricing_id", 1, "image", "", "next_service_mileage", 5000)));
        Map<String, Object> reservation = map("vehicle_id", car.get("id"), "return_branch", 1, "planned_start", LocalDateTime.now().plusDays(3).toString(), "planned_end", LocalDateTime.now().plusDays(5).toString(), "billing_mode", "DAILY", "expected_mileage", 0);
        // 同时发出两台设备的请求，必须只有预约或注销中的一个成功。
        try (var workers = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            var booking = workers.submit(() -> { start.await(); return send(post("/api/portal/orders"), bookingSession, reservation); });
            var closing = workers.submit(() -> { start.await(); return close(closingSession, PASSWORD); });
            start.countDown();
            MvcResult booked = booking.get(20, TimeUnit.SECONDS), closed = closing.get(20, TimeUnit.SECONDS);
            boolean bookingWon = booked.getResponse().getStatus() == 200;
            assertNotEquals(bookingWon, closed.getResponse().getStatus() == 200);
            if (bookingWon) {
                rejects(closed, 400);
                long orderId = number(ok(booked), "id");
                ok(send(post("/api/portal/orders/" + orderId + "/cancel"), bookingSession, map()));
                ok(close(bookingSession, PASSWORD));
            } else {
                assertTrue(Set.of(400, 401).contains(booked.getResponse().getStatus()));
                assertTrue(store.list("rental_order").stream().noneMatch(row -> number(row, "customer_id") == number(account, "customer_id")));
            }
        }
    }
}
