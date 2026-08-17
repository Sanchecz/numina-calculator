package com.numina.calculator.data;

import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PreferencesHistoryRepository implements HistoryRepository {
    private static final String HISTORY_KEY = "calculation_history_v1";
    private static final int MAX_ENTRIES = 50;
    private final SharedPreferences preferences;

    public PreferencesHistoryRepository(SharedPreferences preferences) {
        if (preferences == null) {
            throw new IllegalArgumentException("SharedPreferences is required");
        }
        this.preferences = preferences;
    }

    @Override
    public List<HistoryEntry> load() {
        String serialized = preferences.getString(HISTORY_KEY, "");
        if (serialized == null || serialized.isEmpty()) {
            return Collections.emptyList();
        }
        List<HistoryEntry> entries = new ArrayList<>();
        String[] records = serialized.split("\\n", -1);
        for (String record : records) {
            HistoryEntry decoded = decode(record);
            if (decoded != null) {
                entries.add(decoded);
            }
        }
        return Collections.unmodifiableList(entries);
    }

    @Override
    public void add(HistoryEntry entry) {
        if (entry == null || entry.getExpression().isBlank() || entry.getResult().isBlank()) {
            return;
        }
        List<HistoryEntry> entries = new ArrayList<>(load());
        entries.add(0, entry);
        if (entries.size() > MAX_ENTRIES) {
            entries = new ArrayList<>(entries.subList(0, MAX_ENTRIES));
        }
        save(entries);
    }

    @Override
    public void clear() {
        preferences.edit().remove(HISTORY_KEY).apply();
    }

    private void save(List<HistoryEntry> entries) {
        StringBuilder output = new StringBuilder();
        for (HistoryEntry entry : entries) {
            if (output.length() > 0) {
                output.append('\n');
            }
            output.append(encode(entry));
        }
        preferences.edit().putString(HISTORY_KEY, output.toString()).apply();
    }

    private static String encode(HistoryEntry entry) {
        return encodeField(entry.getExpression()) + "." + encodeField(entry.getResult()) + "." +
            entry.getCreatedAtEpochMillis();
    }

    private static HistoryEntry decode(String record) {
        try {
            String[] fields = record.split("\\.", 3);
            if (fields.length != 3) {
                return null;
            }
            String expression = decodeField(fields[0]);
            String result = decodeField(fields[1]);
            long timestamp = Long.parseLong(fields[2]);
            if (expression.isBlank() || result.isBlank() || timestamp < 0L) {
                return null;
            }
            return new HistoryEntry(expression, result, timestamp);
        } catch (IllegalArgumentException ignored) {
            // Corrupt local history is skipped record-by-record without affecting calculations.
            return null;
        }
    }

    private static String encodeField(String value) {
        return Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP);
    }

    private static String decodeField(String value) {
        return new String(Base64.decode(value, Base64.URL_SAFE | Base64.NO_WRAP), StandardCharsets.UTF_8);
    }
}
