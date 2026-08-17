package com.numina.calculator.domain;

import java.util.Locale;

/**
 * Deterministic, side-effect-free expression evaluator. It deliberately avoids
 * script engines and reflection so untrusted calculator input cannot execute code.
 */
public final class ExpressionEngine {
    static final int MAX_EXPRESSION_LENGTH = 512;
    private static final int MAX_NESTING_DEPTH = 64;
    private static final int MAX_OPERATIONS = 4096;
    private static final double INTEGER_TOLERANCE = 1.0e-10;

    public EvaluationResult evaluate(String expression, AngleMode angleMode, double answer) {
        if (expression == null || expression.trim().isEmpty()) {
            return EvaluationResult.failure(EvaluationError.EMPTY, 0);
        }
        if (expression.length() > MAX_EXPRESSION_LENGTH) {
            return EvaluationResult.failure(EvaluationError.TOO_COMPLEX, MAX_EXPRESSION_LENGTH);
        }

        String normalized = normalize(expression);
        try {
            Parser parser = new Parser(normalized, angleMode, answer);
            Value value = parser.parseExpression();
            parser.skipWhitespace();
            if (!parser.isAtEnd()) {
                throw parser.error(EvaluationError.SYNTAX);
            }
            double result = value.asNumber();
            ensureFinite(result, parser.position);
            return EvaluationResult.success(normalizeZero(result));
        } catch (CalculationException exception) {
            return EvaluationResult.failure(exception.error, exception.position);
        } catch (StackOverflowError ignored) {
            return EvaluationResult.failure(EvaluationError.TOO_COMPLEX, 0);
        }
    }

    private static String normalize(String expression) {
        return expression
            .replace('\u00d7', '*')
            .replace('\u00f7', '/')
            .replace('\u2212', '-')
            .replace('\u2013', '-')
            .replace('\u2014', '-')
            .replace('\u03c0', 'p')
            .replace(',', '.');
    }

    private static double normalizeZero(double value) {
        return value == 0.0d ? 0.0d : value;
    }

    private static void ensureFinite(double value, int position) throws CalculationException {
        if (!Double.isFinite(value)) {
            throw new CalculationException(EvaluationError.OVERFLOW, position);
        }
    }

    private static final class Value {
        private final double raw;
        private final boolean percentage;

        private Value(double raw, boolean percentage) {
            this.raw = raw;
            this.percentage = percentage;
        }

        static Value number(double value) {
            return new Value(value, false);
        }

        Value asPercentage() {
            return new Value(raw, true);
        }

        double asNumber() {
            return percentage ? raw / 100.0d : raw;
        }
    }

    private static final class CalculationException extends Exception {
        private static final long serialVersionUID = 1L;
        private final EvaluationError error;
        private final int position;

        CalculationException(EvaluationError error, int position) {
            this.error = error;
            this.position = Math.max(position, 0);
        }
    }

    private static final class Parser {
        private final String input;
        private final AngleMode angleMode;
        private final double answer;
        private int position;
        private int depth;
        private int operations;

        Parser(String input, AngleMode angleMode, double answer) {
            this.input = input;
            this.angleMode = angleMode == null ? AngleMode.DEGREES : angleMode;
            this.answer = Double.isFinite(answer) ? answer : 0.0d;
        }

        Value parseExpression() throws CalculationException {
            return parseAddition();
        }

        Value parseAddition() throws CalculationException {
            Value left = parseMultiplication();
            while (true) {
                skipWhitespace();
                char operator;
                if (match('+')) {
                    operator = '+';
                } else if (match('-')) {
                    operator = '-';
                } else {
                    return left;
                }
                tick();
                Value right = parseMultiplication();
                double leftNumber = left.asNumber();
                double rightNumber = right.percentage
                    ? leftNumber * right.raw / 100.0d
                    : right.asNumber();
                double result = operator == '+' ? leftNumber + rightNumber : leftNumber - rightNumber;
                ensureFinite(result, position);
                left = Value.number(result);
            }
        }

        Value parseMultiplication() throws CalculationException {
            Value left = parseUnary();
            while (true) {
                skipWhitespace();
                char operator;
                if (match('*')) {
                    operator = '*';
                } else if (match('/')) {
                    operator = '/';
                } else if (startsImplicitFactor()) {
                    operator = '*';
                } else {
                    return left;
                }
                tick();
                Value right = parseUnary();
                double divisor = right.asNumber();
                if (operator == '/' && divisor == 0.0d) {
                    throw error(EvaluationError.DIVISION_BY_ZERO);
                }
                double result = operator == '*'
                    ? left.asNumber() * divisor
                    : left.asNumber() / divisor;
                ensureFinite(result, position);
                left = Value.number(result);
            }
        }

        Value parseUnary() throws CalculationException {
            skipWhitespace();
            if (match('+')) {
                tick();
                return parseUnary();
            }
            if (match('-')) {
                tick();
                Value nested = parseUnary();
                return Value.number(-nested.asNumber());
            }
            return parsePower();
        }

        Value parsePower() throws CalculationException {
            Value base = parsePostfix();
            skipWhitespace();
            if (match('^')) {
                tick();
                Value exponent = parseUnary();
                double result = Math.pow(base.asNumber(), exponent.asNumber());
                if (Double.isNaN(result)) {
                    throw error(EvaluationError.DOMAIN);
                }
                ensureFinite(result, position);
                return Value.number(result);
            }
            return base;
        }

        Value parsePostfix() throws CalculationException {
            Value value = parsePrimary();
            while (true) {
                skipWhitespace();
                if (match('%')) {
                    tick();
                    value = value.asPercentage();
                } else if (match('!')) {
                    tick();
                    value = Value.number(factorial(value.asNumber()));
                } else {
                    return value;
                }
            }
        }

        Value parsePrimary() throws CalculationException {
            skipWhitespace();
            if (isAtEnd()) {
                throw error(EvaluationError.SYNTAX);
            }
            if (match('(')) {
                enterDepth();
                Value nested = parseExpression();
                skipWhitespace();
                if (!match(')')) {
                    throw error(EvaluationError.SYNTAX);
                }
                leaveDepth();
                return nested;
            }
            char current = input.charAt(position);
            if (isAsciiDigit(current) || current == '.') {
                return Value.number(parseNumber());
            }
            if (isAsciiLetter(current)) {
                String identifier = parseIdentifier().toLowerCase(Locale.ROOT);
                if (identifier.equals("p") || identifier.equals("pi")) {
                    return Value.number(Math.PI);
                }
                if (identifier.equals("e")) {
                    return Value.number(Math.E);
                }
                if (identifier.equals("ans")) {
                    return Value.number(answer);
                }
                skipWhitespace();
                if (!match('(')) {
                    throw error(EvaluationError.SYNTAX);
                }
                enterDepth();
                Value argument = parseExpression();
                skipWhitespace();
                if (!match(')')) {
                    throw error(EvaluationError.SYNTAX);
                }
                leaveDepth();
                return Value.number(applyFunction(identifier, argument.asNumber()));
            }
            throw error(EvaluationError.SYNTAX);
        }

        double parseNumber() throws CalculationException {
            int start = position;
            boolean hasDigit = false;
            boolean hasDecimal = false;
            while (!isAtEnd()) {
                char current = input.charAt(position);
                if (isAsciiDigit(current)) {
                    hasDigit = true;
                    position++;
                } else if (current == '.' && !hasDecimal) {
                    hasDecimal = true;
                    position++;
                } else {
                    break;
                }
            }
            if (!hasDigit) {
                throw new CalculationException(EvaluationError.SYNTAX, start);
            }
            if (!isAtEnd() && (input.charAt(position) == 'e' || input.charAt(position) == 'E')) {
                int exponentStart = position++;
                if (!isAtEnd() && (input.charAt(position) == '+' || input.charAt(position) == '-')) {
                    position++;
                }
                int exponentDigits = position;
                while (!isAtEnd() && isAsciiDigit(input.charAt(position))) {
                    position++;
                }
                if (position == exponentDigits) {
                    position = exponentStart;
                }
            }
            try {
                double value = Double.parseDouble(input.substring(start, position));
                ensureFinite(value, start);
                return value;
            } catch (NumberFormatException exception) {
                throw new CalculationException(EvaluationError.SYNTAX, start);
            }
        }

        String parseIdentifier() {
            int start = position;
            while (!isAtEnd() && isAsciiLetter(input.charAt(position))) {
                position++;
            }
            return input.substring(start, position);
        }

        double applyFunction(String function, double value) throws CalculationException {
            tick();
            double radians = angleMode == AngleMode.DEGREES ? Math.toRadians(value) : value;
            double result;
            switch (function) {
                case "sin":
                    result = Math.sin(radians);
                    break;
                case "cos":
                    result = Math.cos(radians);
                    break;
                case "tan":
                    if (Math.abs(Math.cos(radians)) < 1.0e-14) {
                        throw error(EvaluationError.DOMAIN);
                    }
                    result = Math.tan(radians);
                    break;
                case "asin":
                    if (value < -1.0d || value > 1.0d) {
                        throw error(EvaluationError.DOMAIN);
                    }
                    result = fromRadians(Math.asin(value));
                    break;
                case "acos":
                    if (value < -1.0d || value > 1.0d) {
                        throw error(EvaluationError.DOMAIN);
                    }
                    result = fromRadians(Math.acos(value));
                    break;
                case "atan":
                    result = fromRadians(Math.atan(value));
                    break;
                case "sqrt":
                    if (value < 0.0d) {
                        throw error(EvaluationError.DOMAIN);
                    }
                    result = Math.sqrt(value);
                    break;
                case "ln":
                    if (value <= 0.0d) {
                        throw error(EvaluationError.DOMAIN);
                    }
                    result = Math.log(value);
                    break;
                case "log":
                    if (value <= 0.0d) {
                        throw error(EvaluationError.DOMAIN);
                    }
                    result = Math.log10(value);
                    break;
                case "abs":
                    result = Math.abs(value);
                    break;
                case "exp":
                    result = Math.exp(value);
                    break;
                default:
                    throw error(EvaluationError.SYNTAX);
            }
            ensureFinite(result, position);
            return normalizeNearInteger(result);
        }

        double fromRadians(double value) {
            return angleMode == AngleMode.DEGREES ? Math.toDegrees(value) : value;
        }

        double factorial(double value) throws CalculationException {
            if (value < 0.0d || value > 170.0d || Math.abs(value - Math.rint(value)) > INTEGER_TOLERANCE) {
                throw error(EvaluationError.DOMAIN);
            }
            int integer = (int) Math.rint(value);
            double result = 1.0d;
            for (int factor = 2; factor <= integer; factor++) {
                tick();
                result *= factor;
            }
            ensureFinite(result, position);
            return result;
        }

        double normalizeNearInteger(double value) {
            double nearest = Math.rint(value);
            return Math.abs(value - nearest) < 1.0e-12 ? nearest : value;
        }

        boolean startsImplicitFactor() {
            skipWhitespace();
            if (isAtEnd()) {
                return false;
            }
            char current = input.charAt(position);
            return current == '(' || isAsciiLetter(current);
        }

        void enterDepth() throws CalculationException {
            depth++;
            if (depth > MAX_NESTING_DEPTH) {
                throw error(EvaluationError.TOO_COMPLEX);
            }
        }

        void leaveDepth() {
            depth--;
        }

        void tick() throws CalculationException {
            operations++;
            if (operations > MAX_OPERATIONS) {
                throw error(EvaluationError.TOO_COMPLEX);
            }
        }

        void skipWhitespace() {
            while (!isAtEnd() && Character.isWhitespace(input.charAt(position))) {
                position++;
            }
        }

        boolean match(char expected) {
            if (!isAtEnd() && input.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        boolean isAtEnd() {
            return position >= input.length();
        }

        CalculationException error(EvaluationError error) {
            return new CalculationException(error, position);
        }

        private static boolean isAsciiDigit(char value) {
            return value >= '0' && value <= '9';
        }

        private static boolean isAsciiLetter(char value) {
            return (value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z');
        }
    }
}
