package com.futek.railroad.service;

import com.futek.railroad.domain.Line;
import com.futek.railroad.domain.LineStation;
import com.futek.railroad.domain.LineStatus;
import com.futek.railroad.repository.LineRepository;
import com.futek.railroad.repository.LineStationRepository;
import com.futek.railroad.service.dto.LineDetail;
import com.futek.railroad.service.dto.LineStationItem;
import com.futek.railroad.service.dto.LineSummary;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** F4(노선 목록/상세). */
@Service
@Transactional(readOnly = true)
public class LineService {

    private final LineRepository lineRepository;
    private final LineStationRepository lineStationRepository;

    public LineService(LineRepository lineRepository, LineStationRepository lineStationRepository) {
        this.lineRepository = lineRepository;
        this.lineStationRepository = lineStationRepository;
    }

    /** status가 null이면 정상 운행(OPERATING) 노선만, includeAll=true면 전체 상태 포함. */
    public List<LineSummary> list(String nameQuery, boolean includeAll) {
        List<Line> lines = (nameQuery == null || nameQuery.isBlank())
                ? lineRepository.findAll()
                : lineRepository.findByNameContainingIgnoreCaseOrderByNameAsc(nameQuery);

        return lines.stream()
                .filter(line -> includeAll || line.getStatus() == LineStatus.OPERATING)
                .sorted((a, b) -> a.getName().compareTo(b.getName()))
                .map(this::toSummary)
                .toList();
    }

    public Optional<LineDetail> getDetail(Long lineId) {
        return lineRepository.findById(lineId).map(this::toDetail);
    }

    private LineSummary toSummary(Line line) {
        long stationCount = lineStationRepository.countByLine_Id(line.getId());
        return new LineSummary(
                line.getId(),
                line.getName(),
                line.getSegmentLabel(),
                line.getStatus(),
                line.getTotalDistanceKmOfficial(),
                stationCount);
    }

    private LineDetail toDetail(Line line) {
        List<LineStation> stations = lineStationRepository.findByLine_IdOrderBySequenceNoAsc(line.getId());

        List<LineStationItem> items = stations.stream()
                .map(ls -> new LineStationItem(
                        ls.getStation().getId(),
                        ls.getStation().getName(),
                        ls.getSequenceNo(),
                        ls.getCumulativeKm(),
                        lineStationRepository.countByStation_Id(ls.getStation().getId()) > 1))
                .toList();

        List<String> regionNames = line.getRegionNames() == null || line.getRegionNames().isBlank()
                ? List.of()
                : Arrays.asList(line.getRegionNames().split(","));

        return new LineDetail(
                line.getId(),
                line.getName(),
                line.getSegmentLabel(),
                line.getStatus(),
                regionNames,
                line.getTotalDistanceKmOfficial(),
                items);
    }
}
