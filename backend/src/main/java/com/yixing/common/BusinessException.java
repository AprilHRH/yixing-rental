package com.yixing.common;

public class BusinessException extends RuntimeException {
    public BusinessException(String message) { super(message); }
    public static void require(boolean condition, String message) {
        // 条件不满足就中止业务；运行时异常也会让外层 Spring 事务回滚。
        if (!condition) throw new BusinessException(message);
    }
}
