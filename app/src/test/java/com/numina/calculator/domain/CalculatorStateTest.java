package com.numina.calculator.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public final class CalculatorStateTest {
    private CalculatorState state;

    @Before
    public void setUp() {
        state = new CalculatorState(new ExpressionEngine());
    }

    @Test
    public void buildsAndEvaluatesExpression() {
        state.appendDigit('7');
        state.appendOperator('+');
        state.appendDigit('5');
        assertEquals("12", state.getResultText());
        EvaluationResult evaluation = state.evaluate();
        assertTrue(evaluation.isSuccess());
        assertEquals("12", state.getResultText());
        assertTrue(state.isJustEvaluated());
    }

    @Test
    public void digitAfterEqualsStartsFreshExpression() {
        state.setExpression("7+5");
        state.evaluate();
        state.appendDigit('3');
        assertEquals("3", state.getExpression());
    }

    @Test
    public void operatorAfterEqualsContinuesFromAnswer() {
        state.setExpression("7+5");
        state.evaluate();
        state.appendOperator('*');
        state.appendDigit('2');
        assertEquals("12*2", state.getExpression());
        assertEquals("24", state.getResultText());
    }

    @Test
    public void preventsMultipleDecimalSeparatorsInNumber() {
        state.appendDigit('1');
        state.appendDecimalPoint();
        state.appendDigit('2');
        state.appendDecimalPoint();
        state.appendDigit('3');
        assertEquals("1.23", state.getExpression());
    }

    @Test
    public void decimalStartsWithZero() {
        state.appendDecimalPoint();
        state.appendDigit('5');
        assertEquals("0.5", state.getExpression());
    }

    @Test
    public void replacesTrailingBinaryOperator() {
        state.setExpression("8+");
        state.appendOperator('*');
        assertEquals("8*", state.getExpression());
    }

    @Test
    public void allowsUnaryMinusAfterBinaryOperator() {
        state.setExpression("8*");
        state.appendOperator('-');
        state.appendDigit('2');
        assertEquals("8*-2", state.getExpression());
        assertEquals("-16", state.getResultText());
    }

    @Test
    public void closesOnlyExistingParenthesis() {
        state.appendCloseParenthesis();
        assertEquals("", state.getExpression());
        state.appendOpenParenthesis();
        state.appendDigit('2');
        state.appendCloseParenthesis();
        state.appendCloseParenthesis();
        assertEquals("(2)", state.getExpression());
    }

    @Test
    public void togglesSignWithoutLosingExpression() {
        state.setExpression("2+3");
        state.toggleSign();
        assertEquals("-(2+3)", state.getExpression());
        assertEquals("-5", state.getResultText());
        state.toggleSign();
        assertEquals("2+3", state.getExpression());
    }

    @Test
    public void backspaceAfterEqualsClearsEverything() {
        state.setExpression("6*7");
        state.evaluate();
        state.backspace();
        assertEquals("", state.getExpression());
        assertEquals("0", state.getResultText());
    }

    @Test
    public void scientificFunctionAfterEqualsWrapsAnswer() {
        state.setExpression("9");
        state.evaluate();
        state.appendFunction("sqrt");
        assertEquals("sqrt(9)", state.getExpression());
        assertEquals("3", state.getResultText());
    }

    @Test
    public void changesAngleModeAndPreview() {
        state.setExpression("sin(90)");
        assertEquals("1", state.getResultText());
        state.setAngleMode(AngleMode.RADIANS);
        assertEquals(AngleMode.RADIANS, state.getAngleMode());
        assertFalse("1".equals(state.getResultText()));
    }

    @Test
    public void exposesLocalizedDisplayOperators() {
        state.setExpression("8*5/2-1");
        assertEquals("8×5÷2−1", state.getDisplayExpression());
    }

    @Test
    public void reportsEvaluationErrorAndRecoversOnInput() {
        state.setExpression("1/0");
        EvaluationResult result = state.evaluate();
        assertEquals(EvaluationError.DIVISION_BY_ZERO, result.getError());
        assertEquals(EvaluationError.DIVISION_BY_ZERO, state.getLastError());
        state.backspace();
        state.appendDigit('2');
        assertEquals(EvaluationError.NONE, state.getLastError());
        assertEquals("0.5", state.getResultText());
    }

    @Test
    public void restoredExpressionIsBounded() {
        state.restore("1".repeat(600), "1", 1.0d, false);
        assertEquals(ExpressionEngine.MAX_EXPRESSION_LENGTH, state.getExpression().length());
    }
}
