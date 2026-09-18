package com.futek.railroad.web.dto;

/** docs/04-api-design.md 3장의 공통 에러 응답 포맷. */
public record ApiError(String code, String message, int status) {
}
