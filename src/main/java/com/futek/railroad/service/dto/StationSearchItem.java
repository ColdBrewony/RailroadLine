package com.futek.railroad.service.dto;

import java.util.List;

/** F2(역 검색) 결과 한 건. */
public record StationSearchItem(Long stationId, String name, List<String> lineNames) {
}
