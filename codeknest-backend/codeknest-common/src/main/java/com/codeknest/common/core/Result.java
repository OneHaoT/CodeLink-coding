package com.codeknest.common.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

/**
 * 统一响应封装
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> implements Serializable {

    private int code;
    private String message;
    private T data;
    private String timestamp;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = Instant.now().toString();
    }

    public static <T> Result<T> ok() {
        return new Result<>(200, "OK", null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "OK", data);
    }

    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(200, message, data);
    }

    public static <T> Result<T> created(T data) {
        return new Result<>(201, "Created", data);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public boolean isSuccess() {
        return code >= 200 && code < 300;
    }
}
