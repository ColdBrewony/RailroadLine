package com.futek.railroad.service;

import com.futek.railroad.domain.Line;
import com.futek.railroad.domain.LineStation;
import com.futek.railroad.domain.Station;
import com.futek.railroad.repository.LineStationRepository;
import com.futek.railroad.repository.StationRepository;
import com.futek.railroad.service.dto.NeighborStation;
import com.futek.railroad.service.dto.StationDetail;
import com.futek.railroad.service.dto.StationLineInfo;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** F3(역 상세 정보): 역이 속한 모든 노선과, 각 노선에서의 이전/다음 역. */
@Service
@Transactional(readOnly = true)
public class StationDetailService {

    private final StationRepository stationRepository;
    private final LineStationRepository lineStationRepository;

    public StationDetailService(StationRepository stationRepository, LineStationRepository lineStationRepository) {
        this.stationRepository = stationRepository;
        this.lineStationRepository = lineStationRepository;
    }

    public Optional<StationDetail> getDetail(Long stationId) {
        return stationRepository.findById(stationId).map(this::toDetail);
    }

    private StationDetail toDetail(Station station) {
        List<LineStation> memberships = lineStationRepository.findByStation_IdOrderByLine_IdAsc(station.getId());

        List<StationLineInfo> lines = memberships.stream().map(this::toLineInfo).toList();

        return new StationDetail(
                station.getId(),
                station.getName(),
                station.getStationType(),
                station.isKtxStop(),
                station.getDiagramX(),
                station.getDiagramY(),
                lines);
    }

    private StationLineInfo toLineInfo(LineStation current) {
        Line line = current.getLine();
        List<LineStation> allInLine = lineStationRepository.findByLine_IdOrderBySequenceNoAsc(line.getId());

        // sequenceNo는 시더에서 1부터 공백 없이 매겼으므로 index = sequenceNo - 1
        int index = current.getSequenceNo() - 1;
        LineStation prev = index > 0 ? allInLine.get(index - 1) : null;
        LineStation next = index < allInLine.size() - 1 ? allInLine.get(index + 1) : null;

        return new StationLineInfo(
                line.getId(),
                line.getName(),
                line.getSegmentLabel(),
                line.getStatus(),
                current.getSequenceNo(),
                current.getCumulativeKm(),
                toNeighbor(prev, current),
                toNeighbor(next, current));
    }

    private NeighborStation toNeighbor(LineStation neighbor, LineStation current) {
        if (neighbor == null) {
            return null;
        }
        BigDecimal distance = distanceBetween(current.getCumulativeKm(), neighbor.getCumulativeKm());
        return new NeighborStation(neighbor.getStation().getId(), neighbor.getStation().getName(), distance);
    }

    private BigDecimal distanceBetween(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return null;
        }
        return a.subtract(b).abs();
    }
}
