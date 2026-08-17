package com.numina.calculator.domain;

import java.util.Objects;

public final class EvaluationResult {
    private final double value;
    private final EvaluationError error;
    private final int errorPosition;

    private EvaluationResult(double value, EvaluationError error, int errorPosition) {
        this.value = value;
        this.error = Objects.requireNonNull(error);
        this.errorPosition = errorPosition;
    }

    public static EvaluationResult success(double value) {
        return new EvaluationResult(value, EvaluationError.NONE, -1);
    }

    public static EvaluationResult failure(EvaluationError error, int position) {
        if (error == EvaluationError.NONE) {
            throw new IllegalArgumentException("A failure must have an error");
        }
        return new EvaluationResult(Double.NaN, error, Math.max(position, 0));
    }

    public boolean isSuccess() {
        return error == EvaluationError.NONE;
    }

    public double getValue() {
        if (!isSuccess()) {
            throw new IllegalStateException("A failed evaluation has no value");
        }
        return value;
    }

    public EvaluationError getError() {
        return error;
    }

    public int getErrorPosition() {
        return errorPosition;
    }
}
