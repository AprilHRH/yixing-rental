package com.yixing.common;

public class BusinessException extends RuntimeException {
    public BusinessException(String message) { super(message); }
    public static void require(boolean condition, String message) {
        if (!condition) throw new BusinessException(message);
    }
}
