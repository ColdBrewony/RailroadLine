package com.futek.railroad.web.dto;

import com.futek.railroad.service.dto.LineSummary;
import java.util.List;

public record LineListResponse(long total, List<LineSummary> items) {
    public static LineListResponse of(List<LineSummary> items) {
        return new LineListResponse(items.size(), items);
    }
}
