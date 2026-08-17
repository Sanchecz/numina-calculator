package com.numina.calculator.domain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class NumberFormatterTest {
    @Test
    public void removesFloatingPointNoiseAndTrailingZeros() {
        assertEquals("0.3", NumberFormatter.format(0.1d + 0.2d));
        assertEquals("42", NumberFormatter.format(42.0d));
        assertEquals("1.25", NumberFormatter.format(1.25000000000001d));
    }

    @Test
    public void normalizesNegativeZero() {
        assertEquals("0", NumberFormatter.format(-0.0d));
    }

    @Test
    public void usesCompactNotationAtExtremeMagnitudes() {
        assertEquals("1e12", NumberFormatter.format(1.0e12));
        assertEquals("1e-12", NumberFormatter.format(1.0e-12));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInfinity() {
        NumberFormatter.format(Double.POSITIVE_INFINITY);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNotANumber() {
        NumberFormatter.format(Double.NaN);
    }
}
