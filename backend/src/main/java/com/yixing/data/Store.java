package com.yixing.data;

import java.util.*;
import org.springframework.stereotype.Repository;
import static com.yixing.common.BusinessException.require;

@Repository
// 业务层通过这里读写数据，不直接拼 SQL；表名必须在允许列表中。
public class Store {
    private final DataMapper mapper;
    private static final Set<String> TABLES = Set.of("sys_user", "branch", "vehicle", "customer", "pricing_rule", "rental_order", "payment_record", "dispatch_task", "maintenance_record", "settings", "operation_log");
    public Store(DataMapper mapper) { this.mapper = mapper; }
    private void check(String table) { require(TABLES.contains(table), "不支持的数据类型"); }
    public List<Map<String, Object>> list(String table) { check(table); return mapper.list(table); }
    public Map<String, Object> get(String table, long id) {
        check(table);
        Map<String, Object> data = mapper.get(table, id);
        require(data != null, "记录不存在");
        return data;
    }
    public Map<String, Object> lock(String table, long id) {
        // 配合业务层事务使用 FOR UPDATE：事务结束前，其他事务不能同时修改这一行。
        check(table);
        Map<String, Object> data = mapper.lock(table, id);
        require(data != null, "记录不存在");
        return data;
    }
    public Map<String, Object> insert(String table, Map<String, Object> data) {
        // MyBatis 会把数据库生成的主键写回 data.id，再查一次返回完整记录。
        check(table); checkColumns(data); mapper.insert(table, data); return get(table, ((Number) data.get("id")).longValue());
    }
    public Map<String, Object> update(String table, long id, Map<String, Object> data) {
        check(table); checkColumns(data); require(!data.containsKey("id"), "不可更新编号");
        require(mapper.update(table, id, data) == 1, "记录不存在"); return get(table, id);
    }
    private void checkColumns(Map<String, Object> data) {
        // 字段名只能含小写字母和下划线。这是名称格式检查，不是业务字段权限检查；
        // 哪些字段允许修改，仍由各业务服务挑选，不能直接把请求原样传进来。
        require(!data.isEmpty() && data.keySet().stream().allMatch(key -> key.matches("[a-z_]+")), "字段不正确");
    }
}
