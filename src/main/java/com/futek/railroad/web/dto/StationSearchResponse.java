package com.futek.railroad.web.dto;

import com.futek.railroad.service.dto.StationSearchItem;
import java.util.List;

public record StationSearchResponse(long total, List<StationSearchItem> items) {
    public static StationSearchResponse of(List<StationSearchItem> items) {
        return new StationSearchResponse(items.size(), items);
    }
}
