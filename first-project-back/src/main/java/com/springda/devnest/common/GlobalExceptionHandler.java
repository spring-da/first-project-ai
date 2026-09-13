package com.springda.devnest.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.springda.devnest.image.ImageStorageException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.LinkedHashMap;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ImageStorageException.class)
    ProblemDetail handleImageStorage(ImageStorageException exception, HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleUploadSize(HttpServletRequest request) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "单张图片不能超过 20 MB。", request);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ProblemDetail handleMissingUpload(HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "请选择或粘贴一张图片。", request);
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenException.class)
    ProblemDetail handleForbidden(ForbiddenException exception, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, exception.getMessage(), request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleVersionConflict(HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "内容已被更新，请保留本地草稿并重新加载。", request);
    }

    @ExceptionHandler(BadRequestException.class)
    ProblemDetail handleBadRequest(BadRequestException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    ProblemDetail handleTooManyRequests(
            TooManyRequestsException exception,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        response.setHeader("Retry-After", Long.toString(exception.getRetryAfterSeconds()));
        var detail = problem(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage(), request);
        detail.setProperty("code", exception.getCode());
        detail.setProperty("retryAfterSeconds", exception.getRetryAfterSeconds());
        return detail;
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail handleBadCredentials(HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "邮箱或密码错误", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthentication(HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "邮箱或密码错误，或账号已被禁用", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        var fields = new LinkedHashMap<String, String>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage()));

        var detail = problem(HttpStatus.BAD_REQUEST, "请求参数校验失败", request);
        detail.setProperty("fields", fields);
        return detail;
    }

    private ProblemDetail problem(HttpStatus status, String message, HttpServletRequest request) {
        var detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(status.getReasonPhrase());
        detail.setInstance(URI.create(request.getRequestURI()));
        return detail;
    }
}
