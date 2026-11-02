package com.yixing.common;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import static com.yixing.common.BusinessException.require;

public final class Values {
    private Values() {}
    public static Map<String, Object> map(Object... entries) {
        // 交替传入键和值，简化业务代码中创建 JSON 对象和数据库字段集合的写法。
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) result.put(entries[index].toString(), entries[index + 1]);
        return result;
    }
    public static String text(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value == null ? "" : value.toString().trim();
    }
    public static String required(Map<String, Object> data, String key, int maximum) {
        String value = text(data, key);
        require(!value.isBlank() && value.length() <= maximum, "字段 " + key + " 必填且长度不得超过 " + maximum);
        return value;
    }
    public static long number(Map<String, Object> data, String key) {
        // 必须是完整整数，不能把 1.5 静默截成 1，防止编号或里程被错误转换。
        try { return new BigDecimal(text(data, key)).longValueExact(); }
        catch (Exception exception) { throw new BusinessException("字段 " + key + " 必须为整数"); }
    }
    public static BigDecimal money(Map<String, Object> data, String key) {
        // 统一限制金额范围、非负和最多两位小数，收款还是退款由独立方向字段表示。
        try {
            BigDecimal value = new BigDecimal(text(data, key));
            require(value.scale() <= 2 && value.signum() >= 0 && value.compareTo(new BigDecimal("99999999")) <= 0, "金额须为非负数且最多两位小数");
            return value;
        } catch (NumberFormatException exception) { throw new BusinessException("字段 " + key + " 金额格式不正确"); }
    }
    public static boolean flag(Map<String, Object> data, String key) {
        return "true".equalsIgnoreCase(text(data, key)) || "1".equals(text(data, key));
    }
    public static LocalDateTime time(Map<String, Object> data, String key) {
        try { return LocalDateTime.parse(required(data, key, 30)); }
        catch (java.time.format.DateTimeParseException exception) { throw new BusinessException("字段 " + key + " 时间格式不正确"); }
    }
    public static LocalDate date(Map<String, Object> data, String key) {
        try { return LocalDate.parse(required(data, key, 10)); }
        catch (java.time.format.DateTimeParseException exception) { throw new BusinessException("字段 " + key + " 日期格式不正确"); }
    }
    public static String now() { return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME); }
}
