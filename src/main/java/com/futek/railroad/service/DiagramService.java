package com.futek.railroad.service;

import com.futek.railroad.domain.Line;
import com.futek.railroad.domain.LineStation;
import com.futek.railroad.domain.LineStatus;
import com.futek.railroad.layout.VerifiedStationCoordinates;
import com.futek.railroad.repository.LineRepository;
import com.futek.railroad.repository.LineStationRepository;
import com.futek.railroad.service.dto.DiagramLine;
import com.futek.railroad.service.dto.DiagramResponse;
import com.futek.railroad.service.dto.DiagramStation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** F1(전체 노선도): 정상 운행 중인 모든 노선을 좌표와 함께 한 번에 반환한다. */
@Service
@Transactional(readOnly = true)
public class DiagramService {

    private final LineRepository lineRepository;
    private final LineStationRepository lineStationRepository;
    private final VerifiedStationCoordinates coordinates;

    public DiagramService(LineRepository lineRepository, LineStationRepository lineStationRepository,
            VerifiedStationCoordinates coordinates) {
        this.lineRepository = lineRepository;
        this.lineStationRepository = lineStationRepository;
        this.coordinates = coordinates;
    }

    public DiagramResponse getFullDiagram() {
        List<Line> lines = lineRepository.findByStatusOrderByNameAsc(LineStatus.OPERATING);

        List<DiagramLine> diagramLines = new ArrayList<>();
        Map<Long, Integer> lineCountByStation = new HashMap<>();

        for (Line line : lines) {
            List<LineStation> stations = lineStationRepository.findByLine_IdOrderBySequenceNoAsc(line.getId());
            List<DiagramStation> diagramStations = new ArrayList<>(stations.size());
            for (LineStation ls : stations) {
                lineCountByStation.merge(ls.getStation().getId(), 1, Integer::sum);
                diagramStations.add(new DiagramStation(
                        ls.getStation().getId(),
                        ls.getStation().getName(),
                        ls.getStation().getDiagramX(),
                        ls.getStation().getDiagramY(),
                        coordinates.contains(ls.getStation().getName())));
            }
            diagramLines.add(new DiagramLine(
                    line.getId(), line.getName(), line.getSegmentLabel(), colorForLine(line.getId()), diagramStations));
        }

        List<Long> transferStationIds = lineCountByStation.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .toList();

        return new DiagramResponse(diagramLines, transferStationIds);
    }

    /** 저장된 색상 데이터가 없어 노선 id 기반으로 결정적으로 배정한다(같은 노선은 항상 같은 색). */
    private String colorForLine(Long lineId) {
        long hue = (lineId * 47) % 360;
        return "hsl(" + hue + ", 62%, 40%)";
    }
}
