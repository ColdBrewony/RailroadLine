package com.futek.railroad.web.exception;

public class StationNotFoundException extends RuntimeException {
    public StationNotFoundException(Long stationId) {
        super("해당 역을 찾을 수 없습니다: id=" + stationId);
    }
}
