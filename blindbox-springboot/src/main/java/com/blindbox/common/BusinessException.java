package com.blindbox.common;

public class BusinessException extends RuntimeException {
    private final int code;
    private final String errorCode;

    public BusinessException(String msg) {
        super(msg);
        this.code = 400;
        this.errorCode = null;
    }

    public BusinessException(int code, String msg) {
        super(msg);
        this.code = code;
        this.errorCode = null;
    }

    public BusinessException(int code, String msg, String errorCode) {
        super(msg);
        this.code = code;
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }

    public int getCode() {
        return code;
    }
}
