package com.futek.railroad.service.dto;

import com.futek.railroad.domain.LineStatus;
import java.math.BigDecimal;
import java.util.List;

/** F4(노선 상세 정보) 응답. */
public record LineDetail(
        Long lineId,
        String name,
        String segmentLabel,
        LineStatus status,
        List<String> regionNames,
        BigDecimal totalDistanceKm,
        List<LineStationItem> stations) {
}
