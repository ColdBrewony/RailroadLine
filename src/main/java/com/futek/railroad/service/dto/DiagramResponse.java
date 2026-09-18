package com.futek.railroad.service.dto;

import java.util.List;

/** F1(노선도 시각화): 전체(정상 운행) 노선을 한 번에 그리기 위한 데이터. */
public record DiagramResponse(List<DiagramLine> lines, List<Long> transferStationIds) {
}
