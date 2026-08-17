package com.numina.calculator.domain;

/**
 * UI-independent calculator input state. All mutations preserve a parseable,
 * bounded expression and live previews are produced by {@link ExpressionEngine}.
 */
public final class CalculatorState {
    private final ExpressionEngine engine;
    private String expression = "";
    private String resultText = "0";
    private double answer;
    private AngleMode angleMode = AngleMode.DEGREES;
    private EvaluationError lastError = EvaluationError.NONE;
    private boolean justEvaluated;

    public CalculatorState(ExpressionEngine engine) {
        if (engine == null) {
            throw new IllegalArgumentException("ExpressionEngine is required");
        }
        this.engine = engine;
    }

    public String getExpression() {
        return expression;
    }

    public String getDisplayExpression() {
        if (expression.isEmpty()) {
            return "0";
        }
        return expression.replace('*', '\u00d7').replace('/', '\u00f7').replace('-', '\u2212');
    }

    public String getResultText() {
        return resultText;
    }

    public double getAnswer() {
        return answer;
    }

    public AngleMode getAngleMode() {
        return angleMode;
    }

    public EvaluationError getLastError() {
        return lastError;
    }

    public boolean isJustEvaluated() {
        return justEvaluated;
    }

    public void setAngleMode(AngleMode angleMode) {
        this.angleMode = angleMode == null ? AngleMode.DEGREES : angleMode;
        updatePreview();
    }

    public void restore(String expression, String resultText, double answer, boolean justEvaluated) {
        this.expression = safeExpression(expression);
        this.answer = Double.isFinite(answer) ? answer : 0.0d;
        this.resultText = resultText == null || resultText.isBlank() ? "0" : resultText;
        this.justEvaluated = justEvaluated;
        this.lastError = EvaluationError.NONE;
    }

    public void setExpression(String value) {
        expression = safeExpression(value);
        justEvaluated = false;
        updatePreview();
    }

    public void appendDigit(char digit) {
        if (digit < '0' || digit > '9') {
            throw new IllegalArgumentException("Expected an ASCII digit");
        }
        prepareForFreshValue();
        appendBounded(String.valueOf(digit));
        updatePreview();
    }

    public void appendDecimalPoint() {
        prepareForFreshValue();
        int start = expression.length();
        while (start > 0) {
            char previous = expression.charAt(start - 1);
            if (!isNumberCharacter(previous)) {
                break;
            }
            start--;
        }
        String currentNumber = expression.substring(start);
        if (currentNumber.indexOf('.') >= 0) {
            return;
        }
        if (expression.isEmpty() || !Character.isDigit(lastCharacter())) {
            appendBounded("0");
        }
        appendBounded(".");
        updatePreview();
    }

    public void appendOperator(char operator) {
        if (!isOperator(operator)) {
            throw new IllegalArgumentException("Unsupported operator");
        }
        if (justEvaluated) {
            expression = NumberFormatter.format(answer);
            justEvaluated = false;
        }
        if (expression.isEmpty()) {
            if (operator == '-') {
                appendBounded("-");
            }
            updatePreview();
            return;
        }

        char last = lastCharacter();
        if (isOperator(last)) {
            if (operator == '-' && last != '-') {
                appendBounded("-");
            } else {
                expression = expression.substring(0, expression.length() - 1) + operator;
            }
        } else if (last != '(' && last != '.') {
            appendBounded(String.valueOf(operator));
        }
        updatePreview();
    }

    public void appendOpenParenthesis() {
        prepareForFreshValue();
        appendBounded("(");
        updatePreview();
    }

    public void appendCloseParenthesis() {
        if (justEvaluated || expression.isEmpty()) {
            return;
        }
        char last = lastCharacter();
        if (isOperator(last) || last == '(' || last == '.') {
            return;
        }
        if (count('(') > count(')')) {
            appendBounded(")");
            updatePreview();
        }
    }

    public void appendFunction(String function) {
        if (!isAllowedFunction(function)) {
            throw new IllegalArgumentException("Unsupported function");
        }
        if (justEvaluated) {
            expression = function + "(" + NumberFormatter.format(answer) + ")";
            justEvaluated = false;
        } else {
            appendBounded(function + "(");
        }
        updatePreview();
    }

    public void appendConstant(String constant) {
        if (!("pi".equals(constant) || "e".equals(constant) || "ans".equals(constant))) {
            throw new IllegalArgumentException("Unsupported constant");
        }
        prepareForFreshValue();
        appendBounded(constant);
        updatePreview();
    }

    public void appendPostfix(char postfix) {
        if (postfix != '%' && postfix != '!') {
            throw new IllegalArgumentException("Unsupported postfix operator");
        }
        if (justEvaluated) {
            expression = NumberFormatter.format(answer);
            justEvaluated = false;
        }
        if (expression.isEmpty()) {
            return;
        }
        char last = lastCharacter();
        if (Character.isDigit(last) || last == ')' || Character.isLetter(last) || last == '%' || last == '!') {
            appendBounded(String.valueOf(postfix));
            updatePreview();
        }
    }

    public void appendSquare() {
        if (justEvaluated) {
            expression = NumberFormatter.format(answer);
            justEvaluated = false;
        }
        if (!expression.isEmpty() && isValueEnding(lastCharacter())) {
            appendBounded("^2");
            updatePreview();
        }
    }

    public void appendPower() {
        appendOperator('^');
    }

    public void toggleSign() {
        if (expression.isEmpty()) {
            expression = "-";
        } else if (justEvaluated) {
            expression = NumberFormatter.format(-answer);
            answer = -answer;
            resultText = NumberFormatter.format(answer);
            justEvaluated = true;
            return;
        } else if (expression.startsWith("-(") && expression.endsWith(")")) {
            expression = expression.substring(2, expression.length() - 1);
        } else {
            String wrapped = "-(" + expression + ")";
            if (wrapped.length() <= ExpressionEngine.MAX_EXPRESSION_LENGTH) {
                expression = wrapped;
            }
        }
        updatePreview();
    }

    public void backspace() {
        if (expression.isEmpty()) {
            return;
        }
        if (justEvaluated) {
            clear();
            return;
        }
        int lastCodePoint = expression.offsetByCodePoints(expression.length(), -1);
        expression = expression.substring(0, lastCodePoint);
        updatePreview();
    }

    public void clear() {
        expression = "";
        resultText = "0";
        lastError = EvaluationError.NONE;
        justEvaluated = false;
    }

    public EvaluationResult evaluate() {
        EvaluationResult evaluation = engine.evaluate(expression, angleMode, answer);
        lastError = evaluation.getError();
        if (evaluation.isSuccess()) {
            answer = evaluation.getValue();
            resultText = NumberFormatter.format(answer);
            justEvaluated = true;
        } else {
            justEvaluated = false;
        }
        return evaluation;
    }

    private void updatePreview() {
        lastError = EvaluationError.NONE;
        if (expression.isEmpty() || expression.equals("-")) {
            resultText = "0";
            return;
        }
        EvaluationResult preview = engine.evaluate(expression, angleMode, answer);
        if (preview.isSuccess()) {
            resultText = NumberFormatter.format(preview.getValue());
        }
    }

    private void prepareForFreshValue() {
        if (justEvaluated) {
            expression = "";
            resultText = "0";
            justEvaluated = false;
        }
    }

    private void appendBounded(String text) {
        if (expression.length() + text.length() <= ExpressionEngine.MAX_EXPRESSION_LENGTH) {
            expression += text;
        }
    }

    private String safeExpression(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= ExpressionEngine.MAX_EXPRESSION_LENGTH
            ? trimmed
            : trimmed.substring(0, ExpressionEngine.MAX_EXPRESSION_LENGTH);
    }

    private int count(char expected) {
        int total = 0;
        for (int index = 0; index < expression.length(); index++) {
            if (expression.charAt(index) == expected) {
                total++;
            }
        }
        return total;
    }

    private char lastCharacter() {
        return expression.charAt(expression.length() - 1);
    }

    private static boolean isValueEnding(char value) {
        return Character.isDigit(value) || Character.isLetter(value) || value == ')' || value == '%' || value == '!';
    }

    private static boolean isNumberCharacter(char value) {
        return Character.isDigit(value) || value == '.' || value == 'e' || value == 'E';
    }

    private static boolean isOperator(char value) {
        return value == '+' || value == '-' || value == '*' || value == '/' || value == '^';
    }

    private static boolean isAllowedFunction(String function) {
        return "sin".equals(function) || "cos".equals(function) || "tan".equals(function) ||
            "asin".equals(function) || "acos".equals(function) || "atan".equals(function) ||
            "sqrt".equals(function) || "ln".equals(function) || "log".equals(function) ||
            "abs".equals(function) || "exp".equals(function);
    }
}
