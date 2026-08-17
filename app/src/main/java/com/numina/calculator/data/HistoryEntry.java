package com.numina.calculator.data;

import java.util.Objects;

public final class HistoryEntry {
    private final String expression;
    private final String result;
    private final long createdAtEpochMillis;

    public HistoryEntry(String expression, String result, long createdAtEpochMillis) {
        this.expression = Objects.requireNonNull(expression);
        this.result = Objects.requireNonNull(result);
        this.createdAtEpochMillis = createdAtEpochMillis;
    }

    public String getExpression() {
        return expression;
    }

    public String getResult() {
        return result;
    }

    public long getCreatedAtEpochMillis() {
        return createdAtEpochMillis;
    }
}
