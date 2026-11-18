package com.rentaltracker.logging;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Records entries in memory so tests can assert on them. */
public class CapturingLogger implements AppLogger {

    public record Entry(Level level, String event, Map<String, String> fields) {}

    private final List<Entry> entries = new ArrayList<>();

    @Override
    public void log(Level level, String event, Object... fields) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < fields.length; i += 2) {
            map.put(String.valueOf(fields[i]), String.valueOf(fields[i + 1]));
        }
        entries.add(new Entry(level, event, map));
    }

    public List<Entry> entries() { return List.copyOf(entries); }

    public boolean has(Level level, String event) {
        return entries.stream().anyMatch(e -> e.level() == level && e.event().equals(event));
    }
}
