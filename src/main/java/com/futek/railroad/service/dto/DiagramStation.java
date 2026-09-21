package com.futek.railroad.service.dto;

import java.math.BigDecimal;

/** 위경도를 지도 평면에 투영한 위치와 외부 출처 대조 여부. */
public record DiagramStation(Long stationId, String name, BigDecimal x, BigDecimal y,
        boolean coordinateVerified) {
}
