package com.blindbox.common;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonInclude;

@Data
public class Result<T> {
    private int code;
    private String msg;
    private T data;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String errorCode;

    private Result() {}

    private Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> success(String msg, T data) {
        return new Result<>(200, msg, data);
    }

    public static <T> Result<T> error(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    public static <T> Result<T> error(int code, String msg, String errorCode) {
        Result<T> result = new Result<>(code, msg, null);
        result.errorCode = errorCode;
        return result;
    }

    public static <T> Result<T> error(String msg) {
        return new Result<>(400, msg, null);
    }
}
