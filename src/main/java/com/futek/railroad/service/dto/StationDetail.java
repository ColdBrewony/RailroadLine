package com.futek.railroad.service.dto;

import com.futek.railroad.domain.StationType;
import java.math.BigDecimal;
import java.util.List;

/** F3(역 상세 정보) 응답. */
public record StationDetail(
        Long stationId,
        String name,
        StationType stationType,
        boolean ktxStop,
        BigDecimal diagramX,
        BigDecimal diagramY,
        List<StationLineInfo> lines) {
}
