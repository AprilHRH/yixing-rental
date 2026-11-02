package com.yixing.data;

import java.util.Map;
import java.util.stream.Collectors;

public class Statements {
    // 按业务层选定的字段生成 SQL。这里拼接的是已检查的名称，实际值仍用 #{} 绑定。
    @SuppressWarnings("unchecked")
    public String insert(Map<String, Object> parameters) {
        Map<String, Object> data = (Map<String, Object>) parameters.get("data");
        String columns = String.join(",", data.keySet());
        String values = data.keySet().stream().map(key -> "#{data." + key + "}").collect(Collectors.joining(","));
        return "INSERT INTO " + parameters.get("table") + " (" + columns + ") VALUES (" + values + ")";
    }
    @SuppressWarnings("unchecked")
    public String update(Map<String, Object> parameters) {
        Map<String, Object> data = (Map<String, Object>) parameters.get("data");
        String assignments = data.keySet().stream().map(key -> key + "=#{data." + key + "}").collect(Collectors.joining(","));
        return "UPDATE " + parameters.get("table") + " SET " + assignments + " WHERE id=#{id}";
    }
}
