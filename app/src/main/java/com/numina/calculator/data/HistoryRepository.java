package com.numina.calculator.data;

import java.util.List;

public interface HistoryRepository {
    List<HistoryEntry> load();

    void add(HistoryEntry entry);

    void clear();
}
