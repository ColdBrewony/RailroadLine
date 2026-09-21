package com.futek.railroad.layout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Source-reviewed coordinates. Missing entries remain explicitly unverified. */
@Component
public class VerifiedStationCoordinates {
    public record Coordinate(double lat, double lng) {}

    private final Map<String, Coordinate> coordinates;

    public VerifiedStationCoordinates() {
        Map<String, Coordinate> loaded = new HashMap<>();
        Path file = Path.of("data/raw/station-coordinates-verified.csv");
        try {
            var rows = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String row : rows.subList(1, rows.size())) {
                if (row.isBlank()) continue;
                String[] fields = row.split(",", -1);
                double lat = Double.parseDouble(fields[1]);
                double lng = Double.parseDouble(fields[2]);
                if (fields.length < 5 || fields[3].isBlank()
                        || !Double.isFinite(lat) || !Double.isFinite(lng)
                        || lat < 33 || lat > 38.65 || lng < 124.5 || lng > 129.6
                        || loaded.putIfAbsent(fields[0], new Coordinate(lat, lng)) != null) {
                    throw new IllegalStateException("Invalid/duplicate reviewed coordinate: " + row);
                }
            }
        } catch (IOException | NumberFormatException e) {
            throw new IllegalStateException("Cannot load reviewed station coordinates: " + file, e);
        }
        coordinates = Map.copyOf(loaded);
    }

    public boolean contains(String name) { return coordinates.containsKey(name); }

    public Map<String, Coordinate> all() { return coordinates; }
}
