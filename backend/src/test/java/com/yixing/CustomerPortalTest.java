package com.yixing;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static com.yixing.common.Values.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:portaltest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "spring.main.banner-mode=off"})
@AutoConfigureMockMvc
class CustomerPortalTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    MvcResult postJson(String path, MockHttpSession session, Map<String, Object> data) throws Exception {
        var builder = post(path).header("X-Requested-With", "XMLHttpRequest").contentType("application/json").content(json.writeValueAsBytes(data));
        if (session != null) builder.session(session);
        return mvc.perform(builder).andReturn();
    }
    Map<String, Object> body(MvcResult result) throws Exception { return json.readValue(result.getResponse().getContentAsByteArray(), Map.class); }
    Map<String, Object> ok(MvcResult result) throws Exception { assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString()); return body(result); }
    MockHttpSession login(String username, String password, String portal) throws Exception {
        MvcResult result = postJson("/api/auth/login", null, map("username", username, "password", password, "portal", portal)); ok(result); return (MockHttpSession) result.getRequest().getSession();
    }
    Map<String, Object> registration() { String suffix = UUID.randomUUID().toString().substring(0, 8); return map("username", "client_" + suffix, "password", "Client@12345", "name", "自动化客户", "phone", "13800009999", "identity_no", "TEST-ID-" + suffix, "license_no", "TEST-LICENSE-" + suffix, "license_expiry", LocalDate.now().plusYears(3).toString(), "role", "CUSTOMER"); }
    Map<String, Object> data(MockHttpSession session, String path) throws Exception { return ok(mvc.perform(get(path).session(session)).andReturn()); }
    @Test void registrationCannotEscalateOrClaimExistingCustomer() throws Exception {
        Map<String, Object> input = registration(); input.put("role", "ADMIN");
        assertEquals(400, postJson("/api/auth/register", null, input).getResponse().getStatus());
        input.put("role", "CUSTOMER"); Map<String, Object> created = ok(postJson("/api/auth/register", null, input));
        assertEquals("CUSTOMER", created.get("role")); assertNotNull(created.get("customer_id")); assertFalse(created.containsKey("password_hash"));
        assertEquals(400, postJson("/api/auth/register", null, input).getResponse().getStatus());
        input.put("username", "other_" + UUID.randomUUID().toString().substring(0, 8));
        assertEquals(400, postJson("/api/auth/register", null, input).getResponse().getStatus());
        assertEquals(400, postJson("/api/auth/login", null, map("username", created.get("username"), "password", "Client@12345", "portal", "ADMIN")).getResponse().getStatus());
        MockHttpSession customer = login(text(created, "username"), "Client@12345", "CUSTOMER");
        for (String path : List.of("/api/bootstrap", "/api/dashboard", "/api/system", "/api/reports?from=2026-01-01&to=2026-01-02")) assertEquals(403, mvc.perform(get(path).session(customer)).andReturn().getResponse().getStatus());
        assertEquals(403, postJson("/api/system/users", customer, map()).getResponse().getStatus());
        assertEquals(403, postJson("/api/customers", customer, map()).getResponse().getStatus());
        Map<String, Object> scoped = data(customer, "/api/portal/bootstrap");
        assertEquals(1, ((List<?>) scoped.get("customers")).size()); assertTrue(((List<?>) scoped.get("orders")).isEmpty()); assertTrue(((List<?>) scoped.get("maintenance")).isEmpty());
        Map<String, Object> car = ((List<Map<String, Object>>) scoped.get("vehicles")).getFirst(); assertFalse(car.containsKey("license_no"));
        Map<String, Object> ownProfile = ((List<Map<String, Object>>) scoped.get("customers")).getFirst();
        var profileResult = mvc.perform(put("/api/portal/profile").session(customer).header("X-Requested-With", "XMLHttpRequest").contentType("application/json").content(json.writeValueAsBytes(map("name", "更新后的客户", "phone", "13900008888", "license_no", "NEW-LICENSE", "license_expiry", LocalDate.now().plusYears(2).toString(), "role", "ADMIN", "category", "VIP", "restricted", true, "identity_no", "FORGED-IDENTITY")))).andReturn();
        Map<String, Object> profile = ok(profileResult); assertEquals("NORMAL", profile.get("category")); assertEquals(ownProfile.get("identity_no"), profile.get("identity_no")); assertEquals(false, profile.get("restricted"));
        assertEquals("CUSTOMER", data(customer, "/api/auth/me").get("role"));
    }
    @Test void bookingAndCancellationAreStrictlyOwnerScoped() throws Exception {
        Map<String, Object> firstInput = registration(), secondInput = registration();
        Map<String, Object> first = ok(postJson("/api/auth/register", null, firstInput)); ok(postJson("/api/auth/register", null, secondInput));
        MockHttpSession firstSession = login(text(firstInput, "username"), "Client@12345", "CUSTOMER"), secondSession = login(text(secondInput, "username"), "Client@12345", "CUSTOMER");
        MockHttpSession admin = login("admin", "Admin@12345", "ADMIN");
        Map<String, Object> car = ok(postJson("/api/vehicles", admin, map("plate", "沪P" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(), "brand", "测试", "model", "测试车辆", "category", "经济轿车", "color", "白", "seats", 5, "purchase_date", LocalDate.now().minusDays(1).toString(), "license_no", "TEST", "mileage", 0, "branch_id", 1, "pricing_id", 1, "image", "", "next_service_mileage", 5000)));
        Map<String, Object> order = ok(postJson("/api/portal/orders", firstSession, map("vehicle_id", car.get("id"), "customer_id", 1, "pickup_branch", 2, "return_branch", 1, "planned_start", LocalDateTime.now().plusDays(3).toString(), "planned_end", LocalDateTime.now().plusDays(5).toString(), "expected_mileage", 0, "billing_mode", "DAILY")));
        assertEquals(first.get("customer_id"), order.get("customer_id")); assertEquals(1, number(order, "pickup_branch"));
        assertEquals(1, ((List<?>) data(firstSession, "/api/portal/bootstrap").get("orders")).size());
        assertTrue(((List<?>) data(secondSession, "/api/portal/bootstrap").get("orders")).isEmpty());
        String cancel = "/api/portal/orders/" + order.get("id") + "/cancel";
        assertEquals(403, postJson(cancel, secondSession, map()).getResponse().getStatus());
        assertEquals(403, postJson("/api/orders/" + order.get("id") + "/pickup", firstSession, map()).getResponse().getStatus());
        ok(postJson("/api/orders/" + order.get("id") + "/collect", admin, map("request_id", UUID.randomUUID().toString(), "kind", "DEPOSIT", "amount", 1000)));
        assertEquals(400, postJson(cancel, firstSession, map()).getResponse().getStatus());
        Map<String, Object> payment = ((List<Map<String, Object>>) data(firstSession, "/api/portal/bootstrap").get("payments")).getFirst(); assertFalse(payment.containsKey("operator")); assertFalse(payment.containsKey("request_id"));
        assertTrue(((List<?>) data(secondSession, "/api/portal/bootstrap").get("payments")).isEmpty());
    }
    @Test void staffIsManagerAndCustomerAccountCanBeProvisioned() throws Exception {
        MockHttpSession staff = login("staff", "Staff@12345", "ADMIN");
        assertEquals("ADMIN", data(staff, "/api/auth/me").get("role")); data(staff, "/api/system");
        Map<String, Object> person = ok(postJson("/api/customers", staff, map("name", "后台新增客户", "identity_no", "TEST-" + UUID.randomUUID().toString().substring(0, 8), "phone", "13800007777", "license_no", "TEST", "license_expiry", LocalDate.now().plusYears(2).toString(), "category", "NORMAL", "restricted", false)));
        String username = "added_" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> account = ok(postJson("/api/system/users", staff, map("username", username, "name", "后台新增客户", "role", "CUSTOMER", "account_type", "CUSTOMER", "customer_id", person.get("id"), "password", "Client@12345", "enabled", true)));
        assertEquals(person.get("id"), account.get("customer_id")); login(username, "Client@12345", "CUSTOMER");
        assertEquals(400, postJson("/api/system/users", staff, map("username", username + "x", "name", "重复绑定", "role", "CUSTOMER", "customer_id", person.get("id"), "password", "Client@12345", "enabled", true)).getResponse().getStatus());
    }
    @Test void reminderRemainsPendingButDisappearsOnStart() throws Exception {
        MockHttpSession admin = login("admin", "Admin@12345", "ADMIN");
        Map<String, Object> car = ok(postJson("/api/vehicles", admin, map("plate", "沪M" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(), "brand", "测试", "model", "测试车辆", "category", "经济轿车", "color", "白", "seats", 5, "purchase_date", LocalDate.now().minusDays(1).toString(), "license_no", "TEST", "mileage", 0, "branch_id", 1, "pricing_id", 1, "image", "", "next_service_date", LocalDate.now().plusDays(1).toString(), "next_service_mileage", 100)));
        long carId = number(car, "id");
        Map<String, Object> task = ok(postJson("/api/maintenance", admin, map("vehicle_id", carId, "type", "SERVICE", "project", "保养测试", "assignee", "测试人员", "cost", 100)));
        assertTrue(((List<Map<String, Object>>) data(admin, "/api/bootstrap").get("reminders")).stream().anyMatch(item -> number(item, "id") == carId));
        ok(postJson("/api/maintenance/" + task.get("id") + "/start", admin, map()));
        assertFalse(((List<Map<String, Object>>) data(admin, "/api/bootstrap").get("reminders")).stream().anyMatch(item -> number(item, "id") == carId));
    }
}
