package com.futek.railroad.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** 노선 상세(F4)에서, 노선에 속한 역 하나. */
public record LineStationItem(
        Long stationId,
        String name,
        int sequenceNo,
        BigDecimal cumulativeKm,
        @JsonProperty("isTransfer") boolean transfer) {
}
