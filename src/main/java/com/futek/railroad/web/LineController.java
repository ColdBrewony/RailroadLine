package com.futek.railroad.web;

import com.futek.railroad.service.LineService;
import com.futek.railroad.service.dto.LineDetail;
import com.futek.railroad.web.dto.LineListResponse;
import com.futek.railroad.web.exception.LineNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** F4(노선 목록/상세). docs/04-api-design.md 4.3~4.4. */
@RestController
public class LineController {

    private final LineService lineService;

    public LineController(LineService lineService) {
        this.lineService = lineService;
    }

    @GetMapping("/api/lines")
    public LineListResponse list(
            @RequestParam(required = false) String q,
            @RequestParam(name = "all", defaultValue = "false") boolean includeAll) {
        return LineListResponse.of(lineService.list(q, includeAll));
    }

    @GetMapping("/api/lines/{lineId}")
    public LineDetail detail(@PathVariable Long lineId) {
        return lineService.getDetail(lineId).orElseThrow(() -> new LineNotFoundException(lineId));
    }
}
