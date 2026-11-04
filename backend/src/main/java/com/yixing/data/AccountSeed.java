package com.yixing.data;

import java.time.LocalDate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import static com.yixing.common.Values.*;

@Component
public class AccountSeed {
    private final Store store;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    public AccountSeed(Store store) { this.store = store; }
    public void team() {
        account("admin", "系统管理员", "ADMIN", null);
        account("staff", "业务人员", "STAFF", null);
    }
    public void account(String username, String name, String type, Object customerId) {
        store.insert("sys_user", map("username", username, "name", name, "password_hash", encoder.encode("88888888"),
            "role", type.equals("CUSTOMER") ? "CUSTOMER" : "ADMIN", "account_type", type, "customer_id", customerId, "enabled", true));
    }
    public void customers() {
        String[] names = {"周予安", "许清禾", "沈亦舟", "顾南乔", "陆景明"};
        for (int i = 0; i < names.length; i++) {
            var customer = store.insert("customer", map("name", names[i], "identity_no", "DEMO-ID-" + String.format("%06d", i + 1),
                "phone", "1380000" + String.format("%04d", i + 1), "license_no", "DEMO-DL-" + (i + 1),
                "license_expiry", LocalDate.now().plusYears(3).toString(), "category", i == 0 ? "VIP" : "NORMAL",
                "restricted", false, "notes", "虚构客户，仅用于课程演示"));
            account("customer" + String.format("%02d", i + 1), names[i], "CUSTOMER", customer.get("id"));
        }
    }
}
