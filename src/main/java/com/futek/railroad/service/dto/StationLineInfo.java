package com.futek.railroad.service.dto;

import com.futek.railroad.domain.LineStatus;
import java.math.BigDecimal;

/** 역 상세(F3)에서, 이 역이 속한 노선 하나에 대한 정보. */
public record StationLineInfo(
        Long lineId,
        String lineName,
        String segmentLabel,
        LineStatus lineStatus,
        int sequenceNo,
        BigDecimal cumulativeKm,
        NeighborStation prevStation,
        NeighborStation nextStation) {
}
