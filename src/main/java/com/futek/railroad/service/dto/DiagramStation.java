package com.futek.railroad.service.dto;

import java.math.BigDecimal;

/** 전체 노선도(F1) 한 역의 좌표. 좌표는 force-directed 자동 배치 결과이며 지리 좌표가 아니다. */
public record DiagramStation(Long stationId, String name, BigDecimal x, BigDecimal y) {
}
