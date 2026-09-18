package com.futek.railroad.web;

import com.futek.railroad.web.dto.ApiError;
import com.futek.railroad.web.exception.LineNotFoundException;
import com.futek.railroad.web.exception.StationNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** docs/04-api-design.md 3장의 공통 에러 응답 포맷을 전역적으로 적용한다. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(StationNotFoundException.class)
    public ResponseEntity<ApiError> handleStationNotFound(StationNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "STATION_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(LineNotFoundException.class)
    public ResponseEntity<ApiError> handleLineNotFound(LineNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "LINE_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex) {
        return error(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", ex.getMessage());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(code, message, status.value()));
    }
}
