package com.yixing.customer;

import com.yixing.auth.Access;
import com.yixing.data.Store;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.yixing.common.BusinessException.require;
import static com.yixing.common.Values.*;

@Service
public class CustomerService {
    private final Store store;
    private final Access access;
    public CustomerService(Store store, Access access) { this.store = store; this.access = access; }
    @Transactional
    public Map<String, Object> save(Long id, Map<String, Object> input) {
        Map<String, Object> data = map("name", required(input, "name", 60), "identity_no", required(input, "identity_no", 30), "phone", required(input, "phone", 30), "license_no", required(input, "license_no", 80), "license_expiry", date(input, "license_expiry").toString(), "category", text(input, "category"), "restricted", flag(input, "restricted"), "notes", text(input, "notes"));
        require(text(input, "identity_no").matches("[A-Za-z0-9-]{6,30}"), "证件号须为 6 至 30 位字母、数字或连字符");
        require(text(input, "phone").matches("[0-9+ -]{7,20}"), "联系方式格式不正确");
        require(Set.of("NORMAL", "VIP").contains(text(input, "category")), "客户分类不正确");
        require(text(input, "notes").length() <= 500, "备注最多 500 字");
        if (id != null) store.lock("customer", id);
        Map<String, Object> result = id == null ? store.insert("customer", data) : store.update("customer", id, data);
        access.log(id == null ? "新增客户" : "编辑客户", text(result, "name")); return result;
    }
    public void eligible(Map<String, Object> customer, LocalDate endDate) {
        require(!flag(customer, "restricted"), "该客户已被限制租赁");
        require(!date(customer, "license_expiry").isBefore(endDate) && !date(customer, "license_expiry").isBefore(LocalDate.now()), "驾驶证已过期或无法覆盖租期");
    }
}
