package com.futek.railroad.web.exception;

public class LineNotFoundException extends RuntimeException {
    public LineNotFoundException(Long lineId) {
        super("해당 노선을 찾을 수 없습니다: id=" + lineId);
    }
}
