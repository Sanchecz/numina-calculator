package com.numina.calculator.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public final class ExpressionEngineTest {
    private static final double TOLERANCE = 1.0e-9;
    private ExpressionEngine engine;

    @Before
    public void setUp() {
        engine = new ExpressionEngine();
    }

    @Test
    public void evaluatesArithmeticWithPrecedence() {
        assertValue("2+3*4", 14.0d);
        assertValue("20/5-2", 2.0d);
        assertValue("8/2*3", 12.0d);
        assertValue("1-2-3", -4.0d);
    }

    @Test
    public void evaluatesNestedParentheses() {
        assertValue("(2+3)*4", 20.0d);
        assertValue("((2+3)*(7-2))/5", 5.0d);
        assertValue("2*(3+(4*5))", 46.0d);
    }

    @Test
    public void handlesUnaryOperatorsUsingConventionalPowerPrecedence() {
        assertValue("-2^2", -4.0d);
        assertValue("(-2)^2", 4.0d);
        assertValue("2^-3", 0.125d);
        assertValue("--4", 4.0d);
        assertValue("+-4", -4.0d);
    }

    @Test
    public void exponentiationIsRightAssociative() {
        assertValue("2^3^2", 512.0d);
        assertValue("9^0.5", 3.0d);
    }

    @Test
    public void supportsSmartPercentages() {
        assertValue("10%", 0.1d);
        assertValue("200+10%", 220.0d);
        assertValue("200-10%", 180.0d);
        assertValue("200*10%", 20.0d);
        assertValue("200/10%", 2000.0d);
        assertValue("50%+50%", 0.75d);
    }

    @Test
    public void supportsImplicitMultiplication() {
        assertValue("2(3+4)", 14.0d);
        assertValue("(1+2)(3+4)", 21.0d);
        assertValue("2pi", 2.0d * Math.PI);
        assertValue("3sqrt(9)", 9.0d);
        assertValue("pi(2)", 2.0d * Math.PI);
    }

    @Test
    public void evaluatesConstantsAndPreviousAnswer() {
        assertValue("pi", Math.PI);
        assertValue("e", Math.E);
        EvaluationResult answer = engine.evaluate("ans*2", AngleMode.DEGREES, 21.0d);
        assertTrue(answer.isSuccess());
        assertEquals(42.0d, answer.getValue(), TOLERANCE);
    }

    @Test
    public void evaluatesTrigonometryInDegrees() {
        assertValue("sin(30)", 0.5d);
        assertValue("cos(60)", 0.5d);
        assertValue("tan(45)", 1.0d);
        assertValue("asin(0.5)", 30.0d);
        assertValue("acos(0.5)", 60.0d);
        assertValue("atan(1)", 45.0d);
    }

    @Test
    public void evaluatesTrigonometryInRadians() {
        assertValue("sin(pi/2)", Math.sin(Math.PI / 2.0d), AngleMode.RADIANS);
        assertValue("cos(pi)", -1.0d, AngleMode.RADIANS);
        assertValue("atan(1)", Math.PI / 4.0d, AngleMode.RADIANS);
    }

    @Test
    public void evaluatesScientificFunctions() {
        assertValue("sqrt(81)", 9.0d);
        assertValue("ln(e)", 1.0d);
        assertValue("log(1000)", 3.0d);
        assertValue("abs(-12.5)", 12.5d);
        assertValue("exp(0)", 1.0d);
    }

    @Test
    public void evaluatesFactorials() {
        assertValue("0!", 1.0d);
        assertValue("1!", 1.0d);
        assertValue("5!", 120.0d);
        assertValue("3!!", 720.0d);
        assertValue("(2+3)!", 120.0d);
    }

    @Test
    public void acceptsDisplaySymbolsAndLocalizedDecimalSeparator() {
        assertValue("6 × 7", 42.0d);
        assertValue("12 ÷ 4", 3.0d);
        assertValue("5 − 8", -3.0d);
        assertValue("1,5+2,25", 3.75d);
        assertValue("2π", 2.0d * Math.PI);
    }

    @Test
    public void acceptsScientificNotation() {
        assertValue("1e3+2.5e-2", 1000.025d);
        assertValue("1E-3", 0.001d);
    }

    @Test
    public void ignoresWhitespace() {
        assertValue("  2 +\t3\n* 4 ", 14.0d);
    }

    @Test
    public void rejectsEmptyAndMalformedExpressions() {
        assertError("", EvaluationError.EMPTY);
        assertError("   ", EvaluationError.EMPTY);
        assertError("2+", EvaluationError.SYNTAX);
        assertError("(2+3", EvaluationError.SYNTAX);
        assertError("2+3)", EvaluationError.SYNTAX);
        assertError("unknown(2)", EvaluationError.SYNTAX);
        assertError("2..3", EvaluationError.SYNTAX);
    }

    @Test
    public void reportsDivisionByZero() {
        assertError("1/0", EvaluationError.DIVISION_BY_ZERO);
        assertError("1/(2-2)", EvaluationError.DIVISION_BY_ZERO);
        assertError("0/0", EvaluationError.DIVISION_BY_ZERO);
    }

    @Test
    public void rejectsValuesOutsideFunctionDomains() {
        assertError("sqrt(-1)", EvaluationError.DOMAIN);
        assertError("ln(0)", EvaluationError.DOMAIN);
        assertError("log(-10)", EvaluationError.DOMAIN);
        assertError("asin(2)", EvaluationError.DOMAIN);
        assertError("acos(-2)", EvaluationError.DOMAIN);
        assertError("tan(90)", EvaluationError.DOMAIN);
        assertError("(-1)^0.5", EvaluationError.DOMAIN);
    }

    @Test
    public void rejectsInvalidFactorials() {
        assertError("(-1)!", EvaluationError.DOMAIN);
        assertError("1.5!", EvaluationError.DOMAIN);
        assertError("171!", EvaluationError.DOMAIN);
    }

    @Test
    public void reportsOverflow() {
        assertError("exp(1000)", EvaluationError.OVERFLOW);
        assertError("10^1000", EvaluationError.OVERFLOW);
        assertError("1e309", EvaluationError.OVERFLOW);
    }

    @Test
    public void boundsExpressionLength() {
        String oversized = "1".repeat(ExpressionEngine.MAX_EXPRESSION_LENGTH + 1);
        EvaluationResult result = engine.evaluate(oversized, AngleMode.DEGREES, 0.0d);
        assertFalse(result.isSuccess());
        assertEquals(EvaluationError.TOO_COMPLEX, result.getError());
    }

    @Test
    public void boundsParserNesting() {
        String expression = "(".repeat(70) + "1" + ")".repeat(70);
        assertError(expression, EvaluationError.TOO_COMPLEX);
    }

    @Test
    public void failedResultDoesNotExposeANumber() {
        EvaluationResult result = engine.evaluate("1/0", AngleMode.DEGREES, 0.0d);
        assertFalse(result.isSuccess());
        boolean threw = false;
        try {
            result.getValue();
        } catch (IllegalStateException expected) {
            threw = true;
        }
        assertTrue(threw);
    }

    private void assertValue(String expression, double expected) {
        assertValue(expression, expected, AngleMode.DEGREES);
    }

    private void assertValue(String expression, double expected, AngleMode angleMode) {
        EvaluationResult result = engine.evaluate(expression, angleMode, 0.0d);
        assertTrue("Expected success for: " + expression + ", got: " + result.getError(), result.isSuccess());
        assertEquals(expression, expected, result.getValue(), TOLERANCE);
    }

    private void assertError(String expression, EvaluationError expected) {
        EvaluationResult result = engine.evaluate(expression, AngleMode.DEGREES, 0.0d);
        assertFalse("Expected failure for: " + expression, result.isSuccess());
        assertEquals(expression, expected, result.getError());
    }
}
