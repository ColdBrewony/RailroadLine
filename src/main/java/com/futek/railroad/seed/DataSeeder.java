package com.futek.railroad.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.futek.railroad.domain.Line;
import com.futek.railroad.domain.LineStation;
import com.futek.railroad.domain.LineStatus;
import com.futek.railroad.domain.Station;
import com.futek.railroad.domain.StationType;
import com.futek.railroad.repository.LineRepository;
import com.futek.railroad.repository.LineStationRepository;
import com.futek.railroad.repository.StationRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code data/raw/lines/*.json}(docs/03-data-source.md, 위키백과로 검증된 68개 노선 원시 데이터)을
 * 애플리케이션 시작 시 읽어 DB에 적재한다.
 *
 * <p>작업 디렉터리가 프로젝트 루트({@code data/raw/lines}가 상대경로로 보이는 위치)일 때만 동작한다.
 * 이 앱은 읽기 전용 뷰어라 DB는 JSON/CSV 원본 데이터의 파생 캐시일 뿐이다. 그래서 시작할 때마다
 * 기존 Line/LineStation/Station을 전부 지우고 파일에서 새로 적재한다 — 예전에는 "이미 적재된
 * 노선(name+segmentLabel 기준)은 건너뛴다" 방식이었는데, 그러면 역 이름 수정이나 좌표 보충 같은
 * 원본 데이터 수정 사항이 한 번 만들어진 로컬 H2 DB 파일에는 영원히 반영되지 않는 문제가 있었다
 * (재시작해도 예전 값 그대로 남음).
 *
 * <p>{@link com.futek.railroad.layout.DiagramLayoutRunner}가 이 시더 다음에 실행되어야 하므로
 * 순서를 명시한다. 매번 Station을 새로 만들어 diagramX가 null 상태로 시작하므로, 별도 처리 없이도
 * DiagramLayoutRunner가 매 시작마다 좌표를 다시 계산한다.
 */
@Component
@Order(1)
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final Path LINES_DIR = Paths.get("data", "raw", "lines");

    private final LineRepository lineRepository;
    private final StationRepository stationRepository;
    private final LineStationRepository lineStationRepository;
    private final ObjectMapper objectMapper;

    public DataSeeder(
            LineRepository lineRepository,
            StationRepository stationRepository,
            LineStationRepository lineStationRepository,
            ObjectMapper objectMapper) {
        this.lineRepository = lineRepository;
        this.stationRepository = stationRepository;
        this.lineStationRepository = lineStationRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (!Files.isDirectory(LINES_DIR)) {
            log.warn(
                    "시딩 대상 디렉터리를 찾을 수 없습니다: {} (작업 디렉터리가 프로젝트 루트인지 확인하세요)",
                    LINES_DIR.toAbsolutePath());
            return;
        }

        List<Path> files;
        try (Stream<Path> stream = Files.list(LINES_DIR)) {
            files = stream.filter(p -> p.toString().endsWith(".json")).sorted().collect(Collectors.toList());
        }

        lineRepository.deleteAll(); // cascade로 LineStation도 함께 삭제됨
        stationRepository.deleteAll();

        int seeded = 0;
        for (Path file : files) {
            LineFileDto dto = objectMapper.readValue(file.toFile(), LineFileDto.class);
            if (dto.lineName == null || dto.stations == null) {
                log.warn("건너뜀(필수 필드 없음): {}", file.getFileName());
                continue;
            }
            seedLine(dto);
            seeded++;
        }
        log.info("데이터 시딩 완료: {}개 노선 전체 재적재", seeded);
    }

    @Transactional
    void seedLine(LineFileDto dto) {
        Line line = new Line(dto.lineName, dto.segmentLabel);
        line.setOriginStationName(dto.originStation);
        line.setTerminusStationName(dto.terminusStation);
        if (dto.totalDistanceKmOfficial != null) {
            line.setTotalDistanceKmOfficial(BigDecimal.valueOf(dto.totalDistanceKmOfficial));
        }
        if (dto.status != null) {
            line.setStatus(LineStatus.valueOf(dto.status));
        }
        if (dto.region != null && !dto.region.isEmpty()) {
            line.setRegionNames(String.join(",", dto.region));
        }
        line.setRemarks(dto.sourceNotes);
        line = lineRepository.save(line);

        int sequenceNo = 1;
        for (StationEntryDto entry : dto.stations) {
            if (entry.name == null) {
                continue;
            }
            Station station = resolveStation(entry);

            BigDecimal cumulativeKm = entry.cumulativeKm != null ? BigDecimal.valueOf(entry.cumulativeKm) : null;
            LineStation lineStation = new LineStation(station, sequenceNo++, cumulativeKm);
            lineStation.setRemarks(entry.remarks);
            line.addLineStation(lineStation);
        }
        lineStationRepository.saveAll(line.getLineStations());
    }

    /** 이름 기준으로 기존 역을 재사용하고, 새로 알게 된 값(역종류/KTX여부)이 있으면 보강한다. */
    private Station resolveStation(StationEntryDto entry) {
        Station station = stationRepository.findByName(entry.name).orElseGet(() -> new Station(entry.name));

        boolean changed = station.getId() == null;
        if (station.getStationType() == null && entry.stationType != null) {
            station.setStationType(StationType.valueOf(entry.stationType));
            changed = true;
        }
        if (Boolean.TRUE.equals(entry.isKtxStop) && !station.isKtxStop()) {
            station.setKtxStop(true);
            changed = true;
        }
        return changed ? stationRepository.save(station) : station;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class LineFileDto {
        public String lineName;
        public String segmentLabel;
        public String originStation;
        public String terminusStation;
        public Double totalDistanceKmOfficial;
        public List<String> region;
        public String status;
        public List<StationEntryDto> stations;
        public String sourceNotes;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class StationEntryDto {
        public String name;
        public Double cumulativeKm;
        public String stationType;
        public Boolean isKtxStop;
        public String remarks;
    }
}
