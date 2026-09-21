package com.futek.railroad.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.futek.railroad.domain.Line;
import com.futek.railroad.domain.LineStation;
import com.futek.railroad.domain.Station;
import com.futek.railroad.repository.LineRepository;
import com.futek.railroad.repository.LineStationRepository;
import com.futek.railroad.repository.StationRepository;
import com.futek.railroad.service.StationDetailService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class GeoAnchorCalculatorTest {
    @Autowired
    private StationRepository stations;

    @Autowired
    private StationDetailService stationDetails;

    @Test
    void oldAndNewMungyeongStationsAreSeparatePhysicalLocations() {
        Station current = stations.findByName("문경").orElseThrow();
        Station old = stations.findByName("문경(구역)").orElseThrow();
        assertThat(current.getId()).isNotEqualTo(old.getId());
        assertThat(stationDetails.getDetail(current.getId()).orElseThrow().lines())
                .extracting(item -> item.lineName()).containsExactly("중부내륙선");
        assertThat(stationDetails.getDetail(old.getId()).orElseThrow().lines())
                .extracting(item -> item.lineName()).containsExactly("문경선");
        assertThat(current.getDiagramY()).isNotEqualTo(old.getDiagramY());
    }

    @Test
    void auditedAdditionsAndCorrectionsReachThePersistedDiagram() throws Exception {
        // Source ledger is independent of the runtime coordinate loader. Validate the final
        // database positions after seeding/layout, including stations shared by several lines.
        List<String> rows = Files.readAllLines(Path.of("data/raw/station-coordinates-verified.csv"),
                StandardCharsets.UTF_8);
        for (String row : rows.subList(1, rows.size())) {
            String[] fields = row.replace("\"", "").split(",");
            Station station = stations.findByName(fields[0]).orElseThrow();
            double[] actual = unproject(station.getDiagramX().doubleValue(), station.getDiagramY().doubleValue());
            assertThat(actual[0]).as("%s latitude", fields[0])
                    .isCloseTo(Double.parseDouble(fields[1]), within(0.00002));
            assertThat(actual[1]).as("%s longitude", fields[0])
                    .isCloseTo(Double.parseDouble(fields[2]), within(0.00002));
        }
    }

    @ParameterizedTest
    @CsvSource({"김천", "사상"})
    void existingRealCoordinatesRemainFixedAcrossLines(String name) {
        var expected = new VerifiedStationCoordinates().all().get(name);
        double lat = expected.lat();
        double lng = expected.lng();
        Station station = stations.findByName(name).orElseThrow();
        double[] actual = unproject(station.getDiagramX().doubleValue(), station.getDiagramY().doubleValue());
        assertThat(actual[0]).isCloseTo(lat, within(0.00002));
        assertThat(actual[1]).isCloseTo(lng, within(0.00002));
    }

    @Test
    void aRejectedCoordinateOnAnotherLineCannotPullARealStationAway() {
        LineRepository lines = mock(LineRepository.class);
        LineStationRepository links = mock(LineStationRepository.class);
        Station gimcheon = mock(Station.class);
        when(gimcheon.getId()).thenReturn(1L);
        when(gimcheon.getName()).thenReturn("김천");
        Station busan = mock(Station.class);
        when(busan.getId()).thenReturn(2L);
        when(busan.getName()).thenReturn("부산");
        Line accepted = mock(Line.class);
        when(accepted.getId()).thenReturn(1L);
        when(accepted.getRegionNames()).thenReturn("경북본부");
        Line rejected = mock(Line.class);
        when(rejected.getId()).thenReturn(2L);
        when(rejected.getRegionNames()).thenReturn("부산경남본부");
        when(links.findByLine_IdOrderBySequenceNoAsc(1L))
                .thenReturn(List.of(new LineStation(gimcheon, 1, null), new LineStation(busan, 2, null)));
        when(links.findByLine_IdOrderBySequenceNoAsc(2L))
                .thenReturn(List.of(new LineStation(gimcheon, 1, null), new LineStation(busan, 2, null)));
        VerifiedStationCoordinates reviewed = new VerifiedStationCoordinates();
        GeoAnchorCalculator calculator = new GeoAnchorCalculator(lines, links, reviewed);
        for (List<Line> order : List.of(List.of(accepted, rejected), List.of(rejected, accepted))) {
            when(lines.findAll()).thenReturn(order);
            double[][] xy = calculator.compute(Map.of(1L, 0, 2L, 1), 2);
            double[] point = unproject(xy[0][0], xy[1][0]);
            assertThat(point[0]).isCloseTo(reviewed.all().get("김천").lat(), within(1e-9));
            assertThat(point[1]).isCloseTo(reviewed.all().get("김천").lng(), within(1e-9));
        }
    }

    private static double[] unproject(double x, double y) {
        // Public map geometry: 4000px canvas, 8% margin, 33..38.65N / 124.5..129.6E.
        double scale = 3360.0 / 5.65;
        double longitudeScale = Math.cos(Math.toRadians(35.825));
        double offsetX = 320 + (3360 - 5.1 * longitudeScale * scale) / 2;
        return new double[] {38.65 - (y - 320) / scale,
                124.5 + (x - offsetX) / (longitudeScale * scale)};
    }
}
