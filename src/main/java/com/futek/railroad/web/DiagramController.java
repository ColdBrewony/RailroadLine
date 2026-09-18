package com.futek.railroad.web;

import com.futek.railroad.service.DiagramService;
import com.futek.railroad.service.dto.DiagramResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** F1(전체 노선도 시각화). docs/04-api-design.md 4.5. */
@RestController
public class DiagramController {

    private final DiagramService diagramService;

    public DiagramController(DiagramService diagramService) {
        this.diagramService = diagramService;
    }

    @GetMapping("/api/diagram")
    public DiagramResponse diagram() {
        return diagramService.getFullDiagram();
    }
}
