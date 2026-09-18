package com.futek.railroad.web;

import com.futek.railroad.service.StationDetailService;
import com.futek.railroad.service.StationSearchService;
import com.futek.railroad.service.dto.StationDetail;
import com.futek.railroad.web.dto.StationSearchResponse;
import com.futek.railroad.web.exception.StationNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** F2(역 검색), F3(역 상세). docs/04-api-design.md 4.1~4.2. */
@RestController
public class StationController {

    private final StationSearchService stationSearchService;
    private final StationDetailService stationDetailService;

    public StationController(StationSearchService stationSearchService, StationDetailService stationDetailService) {
        this.stationSearchService = stationSearchService;
        this.stationDetailService = stationDetailService;
    }

    @GetMapping("/api/stations/search")
    public StationSearchResponse search(
            @RequestParam String q, @RequestParam(defaultValue = "20") int limit) {
        return StationSearchResponse.of(stationSearchService.search(q, limit));
    }

    @GetMapping("/api/stations/{stationId}")
    public StationDetail detail(@PathVariable Long stationId) {
        return stationDetailService.getDetail(stationId).orElseThrow(() -> new StationNotFoundException(stationId));
    }
}
