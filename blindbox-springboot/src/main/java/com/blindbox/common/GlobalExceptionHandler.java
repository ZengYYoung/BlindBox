package com.blindbox.common;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log=LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(BusinessException.class)
    public Result<?> business(BusinessException e,HttpServletResponse response) {
        response.setStatus(e.getCode());if(e.getCode()==503) response.setHeader("Retry-After","1");return Result.error(e.getCode(),e.getMessage());
    }
    @ExceptionHandler({MethodArgumentNotValidException.class,ConstraintViolationException.class,
        HandlerMethodValidationException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class,
        org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    public Result<?> validation(Exception e,HttpServletResponse response) {
        response.setStatus(400);return Result.error(400,"参数格式、长度或范围不正确");
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<?> size(Exception e,HttpServletResponse response) {
        response.setStatus(413);return Result.error(413,"图片不能超过 5MB");
    }
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<?> duplicate(Exception e,HttpServletResponse response) {
        response.setStatus(409);return Result.error(409,"记录已存在，请使用原请求 ID 查询结果");
    }
    @ExceptionHandler({CannotAcquireLockException.class,QueryTimeoutException.class,DataAccessResourceFailureException.class,
        org.springframework.transaction.CannotCreateTransactionException.class,
        org.springframework.transaction.TransactionTimedOutException.class})
    public Result<?> busy(Exception e,HttpServletResponse response) {
        response.setStatus(503);response.setHeader("Retry-After","1");return Result.error(503,"系统繁忙，请使用同一请求 ID 重试");
    }
    @ExceptionHandler(Exception.class)
    public Result<?> other(Exception e,HttpServletResponse response) {
        log.error("Unhandled request error type={}",e.getClass().getSimpleName());
        response.setStatus(500);return Result.error(500,"服务器内部错误，请保留原请求 ID");
    }
}
