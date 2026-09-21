package com.futek.railroad.service.dto;

import com.futek.railroad.domain.LineStatus;
import java.math.BigDecimal;
import java.util.List;

/** F4(노선 목록) 결과 한 건. */
public record LineSummary(
        Long lineId,
        String name,
        String segmentLabel,
        LineStatus status,
        BigDecimal totalDistanceKm,
        long stationCount,
        List<String> regionNames) {
}
