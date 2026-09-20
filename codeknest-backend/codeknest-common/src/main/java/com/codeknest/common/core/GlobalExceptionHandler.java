package com.codeknest.common.core;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        log.warn("[BusinessException] code={}, msg={}, uri={}", ex.getCode(), ex.getMessage(), request.getRequestURI());
        return Result.fail(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return Result.fail(ErrorCode.PARAM_VALID_FAIL.getCode(), msg);
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException ex) {
        FieldError fe = ex.getBindingResult().getFieldError();
        String msg = fe != null ? fe.getField() + ": " + fe.getDefaultMessage() : "参数校验失败";
        return Result.fail(ErrorCode.PARAM_VALID_FAIL.getCode(), msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException ex) {
        return Result.fail(ErrorCode.PARAM_VALID_FAIL.getCode(), ex.getMessage());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, HttpMessageNotReadableException.class})
    public Result<Void> handleBadRequest(Exception ex) {
        return Result.fail(ErrorCode.BAD_REQUEST.getCode(), "请求参数格式错误");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public Result<Void> handleBadCredentials(BadCredentialsException ex) {
        return Result.fail(ErrorCode.LOGIN_FAIL);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDenied(AccessDeniedException ex) {
        return Result.fail(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex, HttpServletRequest request) {
        log.error("[UnhandledException] uri={}", request.getRequestURI(), ex);
        return Result.fail(ErrorCode.INTERNAL_ERROR);
    }
}
