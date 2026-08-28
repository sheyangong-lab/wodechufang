package com.sharedkitchen.common;

/** 业务异常：message 面向用户展示，code 进 ApiResponse.code。 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        this(400, message);
    }

    public int getCode() {
        return code;
    }
}
