package com.futek.railroad.service.dto;

import java.util.List;

public record DiagramLine(
        Long lineId, String name, String segmentLabel, String color, List<DiagramStation> stations) {
}
