package com.numina.calculator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.numina.calculator.data.HistoryEntry;
import com.numina.calculator.data.HistoryRepository;
import com.numina.calculator.data.PreferencesHistoryRepository;
import com.numina.calculator.domain.AngleMode;
import com.numina.calculator.domain.CalculatorState;
import com.numina.calculator.domain.EvaluationError;
import com.numina.calculator.domain.EvaluationResult;
import com.numina.calculator.domain.ExpressionEngine;
import com.numina.calculator.domain.NumberFormatter;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public final class MainActivity extends Activity {
    private static final String PREFERENCES_NAME = "numina_preferences";
    private static final String HISTORY_PREFERENCES_NAME = "history_preferences";
    private static final String KEY_THEME = "theme";
    private static final String KEY_HAPTICS = "haptics";
    private static final String KEY_ANGLE_MODE = "angle_mode";
    private static final String KEY_SCIENTIFIC = "scientific_visible";
    private static final String KEY_MEMORY = "memory";
    private static final String KEY_MEMORY_SET = "memory_set";
    private static final String THEME_SYSTEM = "system";
    private static final String THEME_LIGHT = "light";
    private static final String THEME_DARK = "dark";

    private static final String STATE_EXPRESSION = "state_expression";
    private static final String STATE_RESULT = "state_result";
    private static final String STATE_ANSWER = "state_answer";
    private static final String STATE_EVALUATED = "state_evaluated";
    private static final String STATE_ANGLE = "state_angle";
    private static final String STATE_SCIENTIFIC = "state_scientific";

    private final ExpressionEngine engine = new ExpressionEngine();
    private SharedPreferences preferences;
    private HistoryRepository historyRepository;
    private CalculatorState calculatorState;
    private TextView expressionView;
    private TextView resultView;
    private TextView memoryView;
    private Button angleButton;
    private Button scientificButton;
    private LinearLayout scientificPanel;
    private boolean scientificVisible;
    private boolean hapticsEnabled;
    private boolean memorySet;
    private boolean compactHeight;
    private double memoryValue;

    @Override
    protected void attachBaseContext(Context newBase) {
        String theme = newBase
            .getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .getString(KEY_THEME, THEME_SYSTEM);
        if (THEME_SYSTEM.equals(theme)) {
            super.attachBaseContext(newBase);
            return;
        }

        Configuration configuration = new Configuration(newBase.getResources().getConfiguration());
        int nightMode = THEME_DARK.equals(theme)
            ? Configuration.UI_MODE_NIGHT_YES
            : Configuration.UI_MODE_NIGHT_NO;
        configuration.uiMode =
            (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
        super.attachBaseContext(newBase.createConfigurationContext(configuration));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE);
        applyConfiguredTheme(preferences.getString(KEY_THEME, THEME_SYSTEM));
        super.onCreate(savedInstanceState);

        compactHeight = getResources().getConfiguration().screenHeightDp < 760;
        hapticsEnabled = preferences.getBoolean(KEY_HAPTICS, true);
        memorySet = preferences.getBoolean(KEY_MEMORY_SET, false);
        memoryValue = Double.longBitsToDouble(preferences.getLong(KEY_MEMORY, Double.doubleToRawLongBits(0.0d)));
        if (!Double.isFinite(memoryValue)) {
            memoryValue = 0.0d;
            memorySet = false;
        }
        historyRepository = new PreferencesHistoryRepository(
            getSharedPreferences(HISTORY_PREFERENCES_NAME, MODE_PRIVATE)
        );
        calculatorState = new CalculatorState(engine);
        AngleMode storedAngle = readAngleMode(preferences.getString(KEY_ANGLE_MODE, AngleMode.DEGREES.name()));
        calculatorState.setAngleMode(storedAngle);
        scientificVisible = preferences.getBoolean(KEY_SCIENTIFIC, false);

        if (savedInstanceState != null) {
            calculatorState.setAngleMode(readAngleMode(savedInstanceState.getString(STATE_ANGLE)));
            calculatorState.restore(
                savedInstanceState.getString(STATE_EXPRESSION, ""),
                savedInstanceState.getString(STATE_RESULT, "0"),
                savedInstanceState.getDouble(STATE_ANSWER, 0.0d),
                savedInstanceState.getBoolean(STATE_EVALUATED, false)
            );
            scientificVisible = savedInstanceState.getBoolean(STATE_SCIENTIFIC, scientificVisible);
        }

        ScrollView content = createContent();
        setContentView(content);
        // The decor view does not exist until content is attached. On Android 17,
        // asking PhoneWindow for its insets controller earlier throws instead of
        // returning null, so configure edge-to-edge only after setContentView().
        configureSystemBars();
        applySafeInsets(content);
        updateDisplay();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_EXPRESSION, calculatorState.getExpression());
        outState.putString(STATE_RESULT, calculatorState.getResultText());
        outState.putDouble(STATE_ANSWER, calculatorState.getAnswer());
        outState.putBoolean(STATE_EVALUATED, calculatorState.isJustEvaluated());
        outState.putString(STATE_ANGLE, calculatorState.getAngleMode().name());
        outState.putBoolean(STATE_SCIENTIFIC, scientificVisible);
    }

    private ScrollView createContent() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setId(R.id.calculator_root);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setBackgroundColor(color(R.color.background));
        scrollView.setFocusable(true);
        scrollView.setFocusableInTouchMode(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(compactHeight ? 8 : 12), dp(20), dp(compactHeight ? 12 : 20));
        scrollView.addView(content, matchWrap());

        content.addView(createHeader(), matchHeight(dp(compactHeight ? 50 : 54)));
        content.addView(createSpacer(compactHeight ? 8 : 12));
        content.addView(createDisplay(), matchWrap());
        content.addView(createSpacer(compactHeight ? 6 : 10));
        content.addView(createModeControls(), matchHeight(dp(52)));
        content.addView(createSpacer(compactHeight ? 4 : 6));
        content.addView(createMemoryRow(), matchHeight(dp(52)));
        content.addView(createSpacer(compactHeight ? 4 : 6));

        scientificPanel = createScientificPanel();
        scientificPanel.setId(R.id.scientific_panel);
        scientificPanel.setVisibility(scientificVisible ? View.VISIBLE : View.GONE);
        content.addView(scientificPanel, matchWrap());

        content.addView(createMainKeypad(), matchWrap());
        return scrollView;
    }

    private View createHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView mark = new TextView(this);
        mark.setText(R.string.brand_mark);
        mark.setTextColor(color(R.color.on_primary));
        mark.setTextSize(20);
        mark.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        mark.setGravity(Gravity.CENTER);
        GradientDrawable markBackground = new GradientDrawable();
        markBackground.setColor(color(R.color.primary));
        markBackground.setCornerRadius(dp(14));
        mark.setBackground(markBackground);
        LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        markParams.setMarginEnd(dp(12));
        header.addView(mark, markParams);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(R.string.app_title, 21, R.color.text_primary, Typeface.BOLD);
        TextView subtitle = text(R.string.app_subtitle, 12, R.color.text_secondary, Typeface.NORMAL);
        titles.addView(title, matchWrap());
        titles.addView(subtitle, matchWrap());
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        Button history = compactButton("↺", KeyStyle.FUNCTION, this::showHistory);
        history.setId(R.id.history_button);
        history.setContentDescription(getString(R.string.cd_history));
        header.addView(history, squareParams(48, 4));

        Button settings = compactButton("⚙", KeyStyle.FUNCTION, this::showSettings);
        settings.setId(R.id.settings_button);
        settings.setContentDescription(getString(R.string.cd_settings));
        header.addView(settings, squareParams(48, 4));
        return header;
    }

    private View createDisplay() {
        LinearLayout display = new LinearLayout(this);
        display.setOrientation(LinearLayout.VERTICAL);
        display.setGravity(Gravity.END);
        int verticalPadding = compactHeight ? 10 : 16;
        display.setPadding(dp(20), dp(verticalPadding), dp(20), dp(verticalPadding));
        display.setBackgroundResource(R.drawable.bg_display);
        display.setMinimumHeight(dp(compactHeight ? 118 : 138));

        memoryView = text(0, 12, R.color.primary, Typeface.BOLD);
        memoryView.setGravity(Gravity.END);
        memoryView.setVisibility(memorySet ? View.VISIBLE : View.INVISIBLE);
        display.addView(memoryView, matchWrap());

        expressionView = text(0, 22, R.color.text_secondary, Typeface.NORMAL);
        expressionView.setId(R.id.expression_text);
        expressionView.setGravity(Gravity.END);
        expressionView.setTextDirection(View.TEXT_DIRECTION_LTR);
        expressionView.setMaxLines(2);
        display.addView(expressionView, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1.0f
        ));

        resultView = text(0, 44, R.color.text_primary, Typeface.BOLD);
        resultView.setId(R.id.result_text);
        resultView.setGravity(Gravity.END);
        resultView.setTextDirection(View.TEXT_DIRECTION_LTR);
        resultView.setSingleLine(true);
        resultView.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        resultView.setOnLongClickListener(view -> {
            copyResult();
            return true;
        });
        display.addView(resultView, matchWrap());
        return display;
    }

    private View createModeControls() {
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setFillViewport(true);
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        scroller.addView(controls, matchHeight(dp(52)));

        scientificButton = compactButton("ƒx", KeyStyle.FUNCTION, this::toggleScientificPanel);
        scientificButton.setContentDescription(getString(R.string.cd_scientific));
        controls.addView(scientificButton, weightedHeight(1.0f, dp(48), 4));

        angleButton = compactButton(getString(R.string.degrees_short), KeyStyle.FUNCTION, this::toggleAngleMode);
        controls.addView(angleButton, weightedHeight(1.0f, dp(48), 4));

        Button answer = compactButton("Ans", KeyStyle.FUNCTION, () -> calculatorState.appendConstant("ans"));
        controls.addView(answer, weightedHeight(1.0f, dp(48), 4));

        Button parentheses = compactButton("( )", KeyStyle.FUNCTION, this::appendSmartParenthesis);
        controls.addView(parentheses, weightedHeight(1.0f, dp(48), 4));
        return scroller;
    }

    private View createMemoryRow() {
        LinearLayout row = keyRow();
        Button clearMemory = compactButton("MC", KeyStyle.FUNCTION, this::clearMemory);
        clearMemory.setContentDescription(getString(R.string.cd_memory_clear));
        row.addView(clearMemory, keyParams(48));
        Button recallMemory = compactButton("MR", KeyStyle.FUNCTION, this::recallMemory);
        recallMemory.setContentDescription(getString(R.string.cd_memory_recall));
        row.addView(recallMemory, keyParams(48));
        Button addMemory = compactButton("M+", KeyStyle.FUNCTION, () -> changeMemory(true));
        addMemory.setContentDescription(getString(R.string.cd_memory_add));
        row.addView(addMemory, keyParams(48));
        Button subtractMemory = compactButton("M−", KeyStyle.FUNCTION, () -> changeMemory(false));
        subtractMemory.setContentDescription(getString(R.string.cd_memory_subtract));
        row.addView(subtractMemory, keyParams(48));
        return row;
    }

    private LinearLayout createScientificPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        addScientificRow(panel,
            keySpec("sin", () -> calculatorState.appendFunction("sin")),
            keySpec("cos", () -> calculatorState.appendFunction("cos")),
            keySpec("tan", () -> calculatorState.appendFunction("tan")),
            keySpec("√", () -> calculatorState.appendFunction("sqrt"))
        );
        addScientificRow(panel,
            keySpec("sin⁻¹", () -> calculatorState.appendFunction("asin")),
            keySpec("cos⁻¹", () -> calculatorState.appendFunction("acos")),
            keySpec("tan⁻¹", () -> calculatorState.appendFunction("atan")),
            keySpec("x²", calculatorState::appendSquare)
        );
        addScientificRow(panel,
            keySpec("ln", () -> calculatorState.appendFunction("ln")),
            keySpec("log", () -> calculatorState.appendFunction("log")),
            keySpec("xʸ", calculatorState::appendPower),
            keySpec("x!", () -> calculatorState.appendPostfix('!'))
        );
        addScientificRow(panel,
            keySpec("π", () -> calculatorState.appendConstant("pi")),
            keySpec("e", () -> calculatorState.appendConstant("e")),
            keySpec("|x|", () -> calculatorState.appendFunction("abs")),
            keySpec("eˣ", () -> calculatorState.appendFunction("exp"))
        );
        panel.addView(createSpacer(4));
        return panel;
    }

    private View createMainKeypad() {
        LinearLayout keypad = new LinearLayout(this);
        keypad.setOrientation(LinearLayout.VERTICAL);
        addMainRow(keypad,
            mainSpec("AC", KeyStyle.FUNCTION, calculatorState::clear, R.id.key_clear, R.string.cd_clear),
            mainSpec("⌫", KeyStyle.FUNCTION, calculatorState::backspace, R.id.key_backspace, R.string.cd_backspace),
            mainSpec("%", KeyStyle.OPERATOR, () -> calculatorState.appendPostfix('%')),
            mainSpec("÷", KeyStyle.OPERATOR, () -> calculatorState.appendOperator('/'))
        );
        addMainRow(keypad,
            mainSpec("7", KeyStyle.NUMBER, () -> calculatorState.appendDigit('7')),
            mainSpec("8", KeyStyle.NUMBER, () -> calculatorState.appendDigit('8')),
            mainSpec("9", KeyStyle.NUMBER, () -> calculatorState.appendDigit('9')),
            mainSpec("×", KeyStyle.OPERATOR, () -> calculatorState.appendOperator('*'))
        );
        addMainRow(keypad,
            mainSpec("4", KeyStyle.NUMBER, () -> calculatorState.appendDigit('4')),
            mainSpec("5", KeyStyle.NUMBER, () -> calculatorState.appendDigit('5')),
            mainSpec("6", KeyStyle.NUMBER, () -> calculatorState.appendDigit('6')),
            mainSpec("−", KeyStyle.OPERATOR, () -> calculatorState.appendOperator('-'))
        );
        addMainRow(keypad,
            mainSpec("1", KeyStyle.NUMBER, () -> calculatorState.appendDigit('1')),
            mainSpec("2", KeyStyle.NUMBER, () -> calculatorState.appendDigit('2')),
            mainSpec("3", KeyStyle.NUMBER, () -> calculatorState.appendDigit('3')),
            mainSpec("+", KeyStyle.OPERATOR, () -> calculatorState.appendOperator('+'))
        );
        addMainRow(keypad,
            mainSpec("±", KeyStyle.NUMBER, calculatorState::toggleSign),
            mainSpec("0", KeyStyle.NUMBER, () -> calculatorState.appendDigit('0')),
            mainSpec(".", KeyStyle.NUMBER, calculatorState::appendDecimalPoint),
            mainSpec("=", KeyStyle.EQUALS, this::calculate, R.id.key_equals, R.string.cd_equals)
        );
        return keypad;
    }

    private void addScientificRow(LinearLayout panel, KeySpec... specs) {
        LinearLayout row = keyRow();
        for (KeySpec spec : specs) {
            Button key = compactButton(spec.label, KeyStyle.FUNCTION, spec.action);
            key.setTag("key_" + spec.label);
            row.addView(key, keyParams(52));
        }
        panel.addView(row, matchHeight(dp(56)));
    }

    private void addMainRow(LinearLayout keypad, KeySpec... specs) {
        LinearLayout row = keyRow();
        for (KeySpec spec : specs) {
            Button key = mainButton(spec.label, spec.style, spec.action);
            key.setTag("key_" + spec.label);
            if (spec.viewId != View.NO_ID) {
                key.setId(spec.viewId);
            }
            if (spec.contentDescription != 0) {
                key.setContentDescription(getString(spec.contentDescription));
            }
            if (spec.viewId == R.id.key_backspace) {
                key.setOnLongClickListener(view -> {
                    performKeyHaptic(view);
                    calculatorState.clear();
                    updateDisplay();
                    return true;
                });
            }
            row.addView(key, keyParams(compactHeight ? 52 : 64));
        }
        keypad.addView(row, matchHeight(dp(compactHeight ? 56 : 68)));
    }

    private Button mainButton(String label, KeyStyle style, Runnable action) {
        Button button = baseButton(label, style, action);
        button.setTextSize(style == KeyStyle.FUNCTION ? 18 : 24);
        return button;
    }

    private Button compactButton(String label, KeyStyle style, Runnable action) {
        Button button = baseButton(label, style, action);
        button.setTextSize(15);
        return button;
    }

    private Button baseButton(String label, KeyStyle style, Runnable action) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setMinimumHeight(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setStateListAnimator(null);
        button.setFilterTouchesWhenObscured(true);
        button.setBackgroundResource(style.background);
        button.setTextColor(color(style.textColor));
        button.setContentDescription(getString(R.string.cd_key, label));
        button.setOnClickListener(view -> {
            performKeyHaptic(view);
            action.run();
            updateDisplay();
        });
        return button;
    }

    private TextView text(int stringResource, int sizeSp, int colorResource, int style) {
        TextView view = new TextView(this);
        if (stringResource != 0) {
            view.setText(stringResource);
        }
        view.setTextSize(sizeSp);
        view.setTextColor(color(colorResource));
        view.setTypeface(Typeface.create("sans-serif", style));
        view.setIncludeFontPadding(false);
        return view;
    }

    private void calculate() {
        String completedExpression = calculatorState.getExpression();
        EvaluationResult result = calculatorState.evaluate();
        if (result.isSuccess()) {
            historyRepository.add(new HistoryEntry(
                completedExpression,
                NumberFormatter.format(result.getValue()),
                System.currentTimeMillis()
            ));
        }
    }

    private void appendSmartParenthesis() {
        String expression = calculatorState.getExpression();
        int opens = count(expression, '(');
        int closes = count(expression, ')');
        if (opens > closes && !expression.isEmpty() && isValueEnd(expression.charAt(expression.length() - 1))) {
            calculatorState.appendCloseParenthesis();
        } else {
            calculatorState.appendOpenParenthesis();
        }
    }

    private void toggleScientificPanel() {
        scientificVisible = !scientificVisible;
        scientificPanel.setVisibility(scientificVisible ? View.VISIBLE : View.GONE);
        preferences.edit().putBoolean(KEY_SCIENTIFIC, scientificVisible).apply();
    }

    private void toggleAngleMode() {
        AngleMode next = calculatorState.getAngleMode() == AngleMode.DEGREES
            ? AngleMode.RADIANS
            : AngleMode.DEGREES;
        calculatorState.setAngleMode(next);
        preferences.edit().putString(KEY_ANGLE_MODE, next.name()).apply();
    }

    private void changeMemory(boolean add) {
        EvaluationResult current = engine.evaluate(
            calculatorState.getExpression(),
            calculatorState.getAngleMode(),
            calculatorState.getAnswer()
        );
        double value;
        if (current.isSuccess()) {
            value = current.getValue();
        } else if (calculatorState.isJustEvaluated()) {
            value = calculatorState.getAnswer();
        } else {
            Toast.makeText(this, errorText(current.getError()), Toast.LENGTH_SHORT).show();
            return;
        }
        double updated = (memorySet ? memoryValue : 0.0d) + (add ? value : -value);
        if (!Double.isFinite(updated)) {
            Toast.makeText(this, R.string.error_overflow, Toast.LENGTH_SHORT).show();
            return;
        }
        memoryValue = updated;
        memorySet = true;
        saveMemory();
        Toast.makeText(this, getString(R.string.memory_stored, NumberFormatter.format(memoryValue)), Toast.LENGTH_SHORT).show();
    }

    private void clearMemory() {
        memoryValue = 0.0d;
        memorySet = false;
        preferences.edit().remove(KEY_MEMORY).putBoolean(KEY_MEMORY_SET, false).apply();
        Toast.makeText(this, R.string.memory_cleared, Toast.LENGTH_SHORT).show();
    }

    private void recallMemory() {
        if (!memorySet) {
            Toast.makeText(this, R.string.memory_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        String value = NumberFormatter.format(memoryValue);
        if (memoryValue < 0.0d) {
            value = "(" + value + ")";
        }
        String expression = calculatorState.getExpression();
        if (calculatorState.isJustEvaluated() || expression.isEmpty()) {
            calculatorState.setExpression(value);
        } else {
            char last = expression.charAt(expression.length() - 1);
            String separator = isValueEnd(last) ? "*" : "";
            calculatorState.setExpression(expression + separator + value);
        }
    }

    private void saveMemory() {
        preferences.edit()
            .putLong(KEY_MEMORY, Double.doubleToRawLongBits(memoryValue))
            .putBoolean(KEY_MEMORY_SET, memorySet)
            .apply();
    }

    private void showHistory() {
        List<HistoryEntry> history = historyRepository.load();
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(R.string.history);
        if (history.isEmpty()) {
            builder.setMessage(R.string.history_empty);
        } else {
            ScrollView scroll = new ScrollView(this);
            LinearLayout list = new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);
            list.setPadding(dp(16), dp(8), dp(16), dp(8));
            scroll.addView(list, matchWrap());
            DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
            for (HistoryEntry entry : history) {
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(16), dp(12), dp(16), dp(12));
                card.setBackgroundResource(R.drawable.bg_display);
                card.setFilterTouchesWhenObscured(true);
                TextView calculation = text(0, 18, R.color.text_primary, Typeface.BOLD);
                calculation.setText(getString(
                    R.string.history_entry_calculation,
                    displayExpression(entry.getExpression()),
                    entry.getResult()
                ));
                TextView timestamp = text(0, 12, R.color.text_secondary, Typeface.NORMAL);
                timestamp.setText(getString(
                    R.string.history_entry_time,
                    dateFormat.format(new Date(entry.getCreatedAtEpochMillis()))
                ));
                card.addView(calculation, matchWrap());
                card.addView(timestamp, matchWrap());
                card.setOnClickListener(view -> {
                    calculatorState.setExpression(entry.getExpression());
                    updateDisplay();
                    AlertDialog dialog = (AlertDialog) view.getTag();
                    if (dialog != null) {
                        dialog.dismiss();
                    }
                });
                LinearLayout.LayoutParams cardParams = matchWrap();
                cardParams.bottomMargin = dp(8);
                list.addView(card, cardParams);
            }
            builder.setView(scroll);
        }
        builder.setNegativeButton(R.string.history_clear, (dialog, which) -> historyRepository.clear());
        builder.setPositiveButton(R.string.close, null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> {
            if (!history.isEmpty()) {
                ViewGroup content = dialog.findViewById(android.R.id.custom);
                attachDialogToHistoryCards(content, dialog);
            }
        });
        dialog.show();
    }

    private void attachDialogToHistoryCards(View view, AlertDialog dialog) {
        if (!(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);
            if (child instanceof LinearLayout && child.hasOnClickListeners()) {
                child.setTag(dialog);
            }
            attachDialogToHistoryCards(child, dialog);
        }
    }

    private void showSettings() {
        String currentTheme = preferences.getString(KEY_THEME, THEME_SYSTEM);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(8), dp(24), 0);

        TextView appearance = text(R.string.theme, 14, R.color.text_secondary, Typeface.BOLD);
        content.addView(appearance, matchWrap());
        RadioGroup themes = new RadioGroup(this);
        RadioButton system = radioButton(R.string.theme_system, THEME_SYSTEM.equals(currentTheme));
        RadioButton light = radioButton(R.string.theme_light, THEME_LIGHT.equals(currentTheme));
        RadioButton dark = radioButton(R.string.theme_dark, THEME_DARK.equals(currentTheme));
        themes.addView(system);
        themes.addView(light);
        themes.addView(dark);
        content.addView(themes, matchWrap());

        Switch haptics = new Switch(this);
        haptics.setText(R.string.haptic_feedback);
        haptics.setTextColor(color(R.color.text_primary));
        haptics.setChecked(hapticsEnabled);
        haptics.setPadding(0, dp(8), 0, dp(8));
        content.addView(haptics, matchWrap());

        new AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setView(content)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.apply, (dialog, which) -> {
                String selectedTheme = system.isChecked()
                    ? THEME_SYSTEM
                    : light.isChecked() ? THEME_LIGHT : THEME_DARK;
                boolean themeChanged = !selectedTheme.equals(currentTheme);
                hapticsEnabled = haptics.isChecked();
                preferences.edit()
                    .putString(KEY_THEME, selectedTheme)
                    .putBoolean(KEY_HAPTICS, hapticsEnabled)
                    .apply();
                if (themeChanged) {
                    recreate();
                }
            })
            .show();
    }

    private RadioButton radioButton(int label, boolean checked) {
        RadioButton button = new RadioButton(this);
        // RadioGroup identifies children by ID. Programmatic RadioButtons default
        // to NO_ID, which lets more than one option remain checked.
        button.setId(View.generateViewId());
        button.setText(label);
        button.setTextColor(color(R.color.text_primary));
        button.setChecked(checked);
        button.setMinHeight(dp(48));
        return button;
    }

    private void copyResult() {
        if (calculatorState.getLastError() != EvaluationError.NONE) {
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.result_label), calculatorState.getResultText()));
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
    }

    private void updateDisplay() {
        if (expressionView == null) {
            return;
        }
        String displayExpression = calculatorState.getDisplayExpression();
        expressionView.setText(displayExpression);
        expressionView.setContentDescription(getString(R.string.cd_expression, displayExpression));

        EvaluationError error = calculatorState.getLastError();
        String displayedResult = error == EvaluationError.NONE
            ? calculatorState.getResultText()
            : errorText(error);
        resultView.setText(displayedResult);
        resultView.setTextColor(color(error == EvaluationError.NONE ? R.color.text_primary : R.color.operator_text));
        resultView.setTextSize(displayedResult.length() > 18 ? 32 : displayedResult.length() > 12 ? 38 : 44);
        resultView.setContentDescription(getString(R.string.cd_result, displayedResult));

        if (memorySet) {
            memoryView.setText(getString(R.string.memory_value, NumberFormatter.format(memoryValue)));
            memoryView.setVisibility(View.VISIBLE);
        } else {
            memoryView.setVisibility(View.INVISIBLE);
        }

        String angleLabel = getString(calculatorState.getAngleMode() == AngleMode.DEGREES
            ? R.string.degrees_short
            : R.string.radians_short);
        angleButton.setText(angleLabel);
        angleButton.setContentDescription(getString(R.string.cd_angle_mode, angleLabel));
        scientificButton.setSelected(scientificVisible);
        scientificButton.setAlpha(scientificVisible ? 1.0f : 0.78f);
    }

    private String errorText(EvaluationError error) {
        switch (error) {
            case EMPTY:
                return getString(R.string.error_empty);
            case DIVISION_BY_ZERO:
                return getString(R.string.error_division_by_zero);
            case DOMAIN:
                return getString(R.string.error_domain);
            case OVERFLOW:
                return getString(R.string.error_overflow);
            case TOO_COMPLEX:
                return getString(R.string.error_too_complex);
            case SYNTAX:
            case NONE:
            default:
                return getString(R.string.error_syntax);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (event.isCtrlPressed() && keyCode == KeyEvent.KEYCODE_C) {
            copyResult();
            return true;
        }
        int unicode = event.getUnicodeChar();
        if (unicode >= '0' && unicode <= '9') {
            calculatorState.appendDigit((char) unicode);
        } else {
            switch (unicode) {
                case '.':
                case ',':
                    calculatorState.appendDecimalPoint();
                    break;
                case '+':
                case '-':
                case '*':
                case '/':
                case '^':
                    calculatorState.appendOperator((char) unicode);
                    break;
                case '%':
                case '!':
                    calculatorState.appendPostfix((char) unicode);
                    break;
                case '(':
                    calculatorState.appendOpenParenthesis();
                    break;
                case ')':
                    calculatorState.appendCloseParenthesis();
                    break;
                default:
                    if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || unicode == '=') {
                        calculate();
                    } else if (keyCode == KeyEvent.KEYCODE_DEL) {
                        calculatorState.backspace();
                    } else if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
                        calculatorState.clear();
                    } else {
                        return super.onKeyUp(keyCode, event);
                    }
            }
        }
        updateDisplay();
        return true;
    }

    private void applyConfiguredTheme(String mode) {
        if (THEME_DARK.equals(mode)) {
            setTheme(R.style.Theme_Numina_Dark);
        } else if (THEME_LIGHT.equals(mode)) {
            setTheme(R.style.Theme_Numina_Light);
        } else {
            setTheme(R.style.Theme_Numina);
        }
    }

    // Android 6-10 require the deprecated system-UI flags for edge-to-edge and
    // light icon compatibility. Newer releases use WindowInsetsController below.
    @SuppressWarnings("deprecation")
    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int appearance = dark ? 0 : WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(
                    appearance,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                );
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (!dark) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.setNavigationBarDividerColor(Color.TRANSPARENT);
        }
    }

    // The pre-Android 11 inset accessors are intentionally retained for minSdk 23.
    @SuppressWarnings("deprecation")
    private void applySafeInsets(View root) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            int left;
            int right;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets safe = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                );
                top = safe.top;
                bottom = safe.bottom;
                left = safe.left;
                right = safe.right;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
                left = insets.getSystemWindowInsetLeft();
                right = insets.getSystemWindowInsetRight();
            }
            view.setPadding(left, top, right, bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    private void performKeyHaptic(View view) {
        if (hapticsEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    private int color(int resource) {
        return getResources().getColor(resource, getTheme());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private View createSpacer(int heightDp) {
        View spacer = new View(this);
        spacer.setLayoutParams(matchHeight(dp(heightDp)));
        return spacer;
    }

    private LinearLayout keyRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private LinearLayout.LayoutParams keyParams(int heightDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(heightDp), 1.0f);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        return params;
    }

    private LinearLayout.LayoutParams squareParams(int sizeDp, int marginStartDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        params.setMarginStart(dp(marginStartDp));
        return params;
    }

    private LinearLayout.LayoutParams weightedHeight(float weight, int heightPx, int marginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, heightPx, weight);
        params.setMargins(dp(marginDp), 0, dp(marginDp), 0);
        return params;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams matchHeight(int height) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
    }

    private static AngleMode readAngleMode(String value) {
        try {
            return AngleMode.valueOf(value == null ? AngleMode.DEGREES.name() : value);
        } catch (IllegalArgumentException ignored) {
            return AngleMode.DEGREES;
        }
    }

    private static String displayExpression(String expression) {
        return expression.replace('*', '×').replace('/', '÷').replace('-', '−');
    }

    private static int count(String value, char target) {
        int total = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == target) {
                total++;
            }
        }
        return total;
    }

    private static boolean isValueEnd(char value) {
        return Character.isDigit(value) || Character.isLetter(value) || value == ')' || value == '%' || value == '!';
    }

    private static KeySpec keySpec(String label, Runnable action) {
        return new KeySpec(label, KeyStyle.FUNCTION, action, View.NO_ID, 0);
    }

    private static KeySpec mainSpec(String label, KeyStyle style, Runnable action) {
        return new KeySpec(label, style, action, View.NO_ID, 0);
    }

    private static KeySpec mainSpec(
        String label,
        KeyStyle style,
        Runnable action,
        int viewId,
        int contentDescription
    ) {
        return new KeySpec(label, style, action, viewId, contentDescription);
    }

    private enum KeyStyle {
        NUMBER(R.drawable.bg_key_number, R.color.text_primary),
        FUNCTION(R.drawable.bg_key_function, R.color.text_primary),
        OPERATOR(R.drawable.bg_key_operator, R.color.operator_text),
        EQUALS(R.drawable.bg_key_equals, R.color.on_primary);

        final int background;
        final int textColor;

        KeyStyle(int background, int textColor) {
            this.background = background;
            this.textColor = textColor;
        }
    }

    private static final class KeySpec {
        final String label;
        final KeyStyle style;
        final Runnable action;
        final int viewId;
        final int contentDescription;

        KeySpec(String label, KeyStyle style, Runnable action, int viewId, int contentDescription) {
            this.label = label;
            this.style = style;
            this.action = action;
            this.viewId = viewId;
            this.contentDescription = contentDescription;
        }
    }
}
