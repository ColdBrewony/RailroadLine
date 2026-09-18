package com.futek.railroad.service.dto;

import java.math.BigDecimal;

/** 역 상세(F3)에서 같은 노선의 이전/다음 역. 기점/종점이면 null. */
public record NeighborStation(Long stationId, String name, BigDecimal distanceKm) {
}
