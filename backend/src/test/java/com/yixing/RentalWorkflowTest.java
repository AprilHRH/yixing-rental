package com.yixing;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static com.yixing.common.Values.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:rentaltest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "spring.main.banner-mode=off"})
@AutoConfigureMockMvc
class RentalWorkflowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    MockHttpSession session;
    @BeforeEach void login() throws Exception { session = loginAs("admin", "Admin@12345"); }
    MockHttpSession loginAs(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").header("X-Requested-With", "XMLHttpRequest").contentType("application/json").content(json.writeValueAsBytes(map("username", username, "password", password)))).andReturn();
        assertEquals(200, result.getResponse().getStatus()); return (MockHttpSession) result.getRequest().getSession();
    }
    MvcResult request(String method, String path, Map<String, Object> body) throws Exception {
        var builder = method.equals("PUT") ? put(path) : post(path);
        return mvc.perform(builder.session(session).header("X-Requested-With", "XMLHttpRequest").contentType("application/json").content(json.writeValueAsBytes(body))).andReturn();
    }
    Map<String, Object> body(MvcResult result) throws Exception { return json.readValue(result.getResponse().getContentAsByteArray(), Map.class); }
    Map<String, Object> success(String path, Map<String, Object> body) throws Exception {
        MvcResult result = request("POST", path, body); assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString()); return body(result);
    }
    Map<String, Object> bootstrap() throws Exception { return body(mvc.perform(get("/api/bootstrap").session(session)).andReturn()); }
    Map<String, Object> vehicle() throws Exception {
        return success("/api/vehicles", map("plate", "沪T" + UUID.randomUUID().toString().substring(0,8).toUpperCase(), "brand", "测试", "model", "测试轿车", "category", "经济轿车", "color", "白", "seats", 5, "purchase_date", LocalDate.now().minusDays(1).toString(), "license_no", "TEST-LICENSE", "mileage", 100, "branch_id", 1, "pricing_id", 1, "image", "", "next_service_mileage", 5000));
    }
    Map<String, Object> customer() throws Exception {
        return success("/api/customers", map("name", "虚构测试客户", "identity_no", "TEST-" + UUID.randomUUID().toString().substring(0,12), "phone", "13800009999", "license_no", "TEST-DRIVER", "license_expiry", LocalDate.now().plusYears(2).toString(), "category", "NORMAL", "restricted", false));
    }
    Map<String, Object> reservation(Map<String, Object> vehicle, Map<String, Object> customer) {
        return map("vehicle_id", vehicle.get("id"), "customer_id", customer.get("id"), "pickup_branch", 1, "return_branch", 1, "planned_start", LocalDateTime.now().plusSeconds(30).toString(), "planned_end", LocalDateTime.now().plusDays(2).toString(), "billing_mode", "DAILY", "expected_mileage", 200);
    }
    @Test void authenticationAndRoleProtection() throws Exception {
        assertEquals(401, mvc.perform(get("/api/bootstrap")).andReturn().getResponse().getStatus());
        MockHttpSession staff = loginAs("staff", "Staff@12345");
        assertEquals(200, mvc.perform(get("/api/system").session(staff)).andReturn().getResponse().getStatus());
        assertEquals(200, mvc.perform(get("/api/reports?from=2026-01-01&to=2026-01-10").session(staff)).andReturn().getResponse().getStatus());
        Map<String, Object> staffVehicle = vehicle();
        assertEquals(200, mvc.perform(post("/api/vehicles/" + staffVehicle.get("id") + "/toggle").session(staff).header("X-Requested-With", "XMLHttpRequest")).andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(post("/api/orders").session(session).contentType("application/json").content("{}")).andReturn().getResponse().getStatus());
    }
    @Test void completeRentalAndRefund() throws Exception {
        Map<String, Object> car = vehicle(), person = customer(), order = success("/api/orders", reservation(car, person));
        String base = "/api/orders/" + order.get("id");
        assertEquals(400, request("POST", base + "/pickup", map("start_mileage", 100, "condition", "正常")).getResponse().getStatus());
        success(base + "/collect", map("request_id", UUID.randomUUID().toString(), "kind", "DEPOSIT", "amount", 1000));
        success(base + "/pickup", map("start_mileage", 100, "condition", "正常"));
        assertEquals(400, request("POST", base + "/return", map("end_mileage", 99, "return_branch", 1, "extra_fee", 0, "discount", 0, "condition", "正常")).getResponse().getStatus());
        Map<String, Object> returned = success(base + "/return", map("end_mileage", 220, "return_branch", 2, "extra_fee", 10, "discount", 5, "fee_notes", "清洁 10 元", "condition", "正常", "damage", false));
        assertEquals(193.0, ((Number) returned.get("actual_rent")).doubleValue(), .001);
        success(base + "/settle", map("request_id", UUID.randomUUID().toString()));
        assertEquals(400, request("POST", base + "/settle", map("request_id", UUID.randomUUID().toString())).getResponse().getStatus());
        Map<String, Object> data = bootstrap();
        Map<String, Object> updatedCar = ((List<Map<String, Object>>) data.get("vehicles")).stream().filter(value -> number(value, "id") == number(car, "id")).findFirst().orElseThrow();
        assertEquals("AVAILABLE", updatedCar.get("status")); assertEquals(2, number(updatedCar, "branch_id"));
        double net = ((List<Map<String, Object>>) data.get("payments")).stream().filter(value -> number(value, "order_id") == number(order, "id")).mapToDouble(value -> ((Number) value.get("amount")).doubleValue() * ("IN".equals(value.get("direction")) ? 1 : -1)).sum();
        assertEquals(193, net, .001);
    }
    @Test void overlappingOrdersRejectedAndCancellationRefunded() throws Exception {
        Map<String, Object> input = reservation(vehicle(), customer()), order = success("/api/orders", input);
        assertEquals(400, request("POST", "/api/orders", input).getResponse().getStatus());
        String base = "/api/orders/" + order.get("id"); String requestId = UUID.randomUUID().toString();
        success(base + "/collect", map("request_id", requestId, "kind", "DEPOSIT", "amount", 1000));
        assertEquals(400, request("POST", base + "/collect", map("request_id", requestId, "kind", "DEPOSIT", "amount", 1000)).getResponse().getStatus());
        assertEquals("CANCELLED", success(base + "/cancel", map("request_id", UUID.randomUUID().toString())).get("status"));
        success("/api/orders", input);
    }
    @Test void concurrentReservationsOnlyOneSucceeds() throws Exception {
        Map<String, Object> input = reservation(vehicle(), customer());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> call = () -> request("POST", "/api/orders", input).getResponse().getStatus();
            List<Future<Integer>> results = executor.invokeAll(List.of(call, call));
            List<Integer> statuses = new ArrayList<>(); for (Future<Integer> result : results) statuses.add(result.get()); Collections.sort(statuses);
            assertEquals(List.of(200, 400), statuses);
        } finally { executor.shutdownNow(); }
    }
    @Test void adjacentReservationsAndInvalidCustomer() throws Exception {
        Map<String, Object> car = vehicle(), person = customer(), input = reservation(car, person);
        input.put("planned_start", LocalDateTime.now().plusDays(1).withNano(0).toString()); input.put("planned_end", LocalDateTime.now().plusDays(2).withNano(0).toString());
        success("/api/orders", input); input.put("planned_start", input.get("planned_end")); input.put("planned_end", LocalDateTime.now().plusDays(3).toString()); success("/api/orders", input);
        person.put("restricted", true); assertEquals(200, request("PUT", "/api/customers/" + person.get("id"), person).getResponse().getStatus());
        assertEquals(400, request("POST", "/api/orders", reservation(vehicle(), person)).getResponse().getStatus());
    }
    @Test void dispatchAndMaintenanceStateTransitions() throws Exception {
        Map<String, Object> car = vehicle();
        Map<String, Object> task = success("/api/dispatch", map("vehicle_id", car.get("id"), "to_branch", 2, "assignee", "测试负责人", "planned_start", LocalDateTime.now().plusSeconds(30).toString(), "planned_end", LocalDateTime.now().plusHours(2).toString()));
        success("/api/dispatch/" + task.get("id") + "/start", map()); success("/api/dispatch/" + task.get("id") + "/complete", map());
        Map<String, Object> work = success("/api/maintenance", map("vehicle_id", car.get("id"), "type", "SERVICE", "project", "测试保养", "assignee", "测试人员", "cost", 100));
        success("/api/maintenance/" + work.get("id") + "/start", map());
        Map<String, Object> booking = reservation(car, customer()); booking.put("pickup_branch", 2);
        assertEquals(400, request("POST", "/api/orders", booking).getResponse().getStatus());
        success("/api/maintenance/" + work.get("id") + "/complete", map("cost", 120, "next_date", LocalDate.now().plusMonths(3).toString(), "next_mileage", 6000));
        success("/api/orders", booking);
    }
    @Test void priceSnapshotSurvivesRuleChange() throws Exception {
        Map<String, Object> order = success("/api/orders", reservation(vehicle(), customer()));
        double original = ((Number) order.get("daily_rate")).doubleValue();
        Map<String, Object> rate = ((List<Map<String, Object>>) bootstrap().get("pricing")).stream().filter(value -> number(value, "id") == 1).findFirst().orElseThrow();
        Map<String, Object> updated = new LinkedHashMap<>(rate); updated.put("daily_rate", 777);
        assertEquals(200, request("PUT", "/api/system/pricing/1", updated).getResponse().getStatus());
        Map<String, Object> persisted = ((List<Map<String, Object>>) bootstrap().get("orders")).stream().filter(value -> number(value, "id") == number(order, "id")).findFirst().orElseThrow();
        assertEquals(original, ((Number) persisted.get("daily_rate")).doubleValue());
        request("PUT", "/api/system/pricing/1", rate);
    }
    @Test void backupRestoreAndInvalidArchiveRollback() throws Exception {
        byte[] backup = mvc.perform(get("/api/system/backup").session(session)).andReturn().getResponse().getContentAsByteArray();
        assertTrue(backup.length > 100);
        int before = ((List<?>) bootstrap().get("customers")).size(); customer();
        MvcResult restored = mvc.perform(multipart("/api/system/restore").file(new MockMultipartFile("file", "backup.zip", "application/zip", backup)).param("confirmation", "RESTORE").session(session).header("X-Requested-With", "XMLHttpRequest")).andReturn();
        assertEquals(200, restored.getResponse().getStatus(), restored.getResponse().getContentAsString());
        session = loginAs("admin", "Admin@12345"); assertEquals(before, ((List<?>) bootstrap().get("customers")).size());
        customer();
        MvcResult invalid = mvc.perform(multipart("/api/system/restore").file(new MockMultipartFile("file", "bad.zip", "application/zip", "bad".getBytes())).param("confirmation", "RESTORE").session(session).header("X-Requested-With", "XMLHttpRequest")).andReturn();
        assertEquals(400, invalid.getResponse().getStatus());
    }
}
