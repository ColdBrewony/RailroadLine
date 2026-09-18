package com.futek.railroad.service;

import com.futek.railroad.domain.Station;
import com.futek.railroad.repository.LineStationRepository;
import com.futek.railroad.repository.StationRepository;
import com.futek.railroad.service.dto.StationSearchItem;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** F2(역 검색): 역명 부분 일치 검색. */
@Service
@Transactional(readOnly = true)
public class StationSearchService {

    private final StationRepository stationRepository;
    private final LineStationRepository lineStationRepository;

    public StationSearchService(StationRepository stationRepository, LineStationRepository lineStationRepository) {
        this.stationRepository = stationRepository;
        this.lineStationRepository = lineStationRepository;
    }

    public List<StationSearchItem> search(String query, int limit) {
        return stationRepository.findByNameContainingIgnoreCaseOrderByNameAsc(query).stream()
                .limit(limit)
                .map(this::toItem)
                .toList();
    }

    private StationSearchItem toItem(Station station) {
        List<String> lineNames = lineStationRepository.findByStation_IdOrderByLine_IdAsc(station.getId()).stream()
                .map(ls -> ls.getLine().getName())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        return new StationSearchItem(station.getId(), station.getName(), lineNames);
    }
}
