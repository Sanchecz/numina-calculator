package com.numina.calculator;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withTagValue;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class MainActivityTest {
    @Rule
    public final ActivityScenarioRule<MainActivity> activityRule =
        new ActivityScenarioRule<>(MainActivity.class);

    @Test
    public void launchesWithAccessibleInitialState() {
        onView(withId(R.id.calculator_root)).check(matches(isDisplayed()));
        onView(withId(R.id.expression_text)).check(matches(withText("0")));
        onView(withId(R.id.result_text)).check(matches(withText("0")));
    }

    @Test
    public void calculatesPrimaryKeypadScenario() {
        onView(withId(R.id.key_clear)).perform(click());
        onView(withTagValue(is("key_7"))).perform(click());
        onView(withTagValue(is("key_+"))).perform(click());
        onView(withTagValue(is("key_5"))).perform(click());
        onView(withId(R.id.key_equals)).perform(click());
        onView(withId(R.id.expression_text)).check(matches(withText("7+5")));
        onView(withId(R.id.result_text)).check(matches(withText("12")));
    }

    @Test
    public void stateSurvivesActivityRecreation() {
        onView(withId(R.id.key_clear)).perform(click());
        onView(withTagValue(is("key_9"))).perform(click());
        onView(withTagValue(is("key_×"))).perform(click());
        onView(withTagValue(is("key_3"))).perform(click());
        activityRule.getScenario().recreate();
        onView(withId(R.id.expression_text)).check(matches(withText("9×3")));
        onView(withId(R.id.result_text)).check(matches(withText("27")));
    }

    @Test
    public void scientificPanelCanBeExpanded() {
        activityRule.getScenario().onActivity(activity -> {
            View panel = activity.findViewById(R.id.scientific_panel);
            if (panel.getVisibility() == View.VISIBLE) {
                activity.findViewById(R.id.scientific_panel).setVisibility(View.GONE);
            }
        });
        onView(withText("ƒx")).perform(click());
        onView(withTagValue(is("key_sin"))).check(matches(isDisplayed()));
    }

    @Test
    public void darkThemeSelectionIsPersisted() {
        onView(withContentDescription(R.string.cd_settings)).perform(click());
        onView(withText(R.string.theme_dark)).perform(click());
        onView(withText(R.string.apply)).perform(click());
        onView(withId(R.id.calculator_root)).check(matches(isDisplayed()));

        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String selectedTheme = context
            .getSharedPreferences("numina_preferences", Context.MODE_PRIVATE)
            .getString("theme", "");
        assertEquals("dark", selectedTheme);
        activityRule.getScenario().onActivity(activity -> assertEquals(
            Configuration.UI_MODE_NIGHT_YES,
            activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK
        ));
    }

    @Test
    public void visibleInteractiveControlsMeetMinimumTouchTarget() {
        activityRule.getScenario().onActivity(activity -> {
            float density = activity.getResources().getDisplayMetrics().density;
            assertTouchTargets(activity.findViewById(R.id.calculator_root), Math.round(48.0f * density));
        });
    }

    private static void assertTouchTargets(View view, int minimumPixels) {
        if (view.getVisibility() != View.VISIBLE) {
            return;
        }
        if (view.isClickable()) {
            assertTrue("Touch target width below 48dp: " + view, view.getWidth() >= minimumPixels);
            assertTrue("Touch target height below 48dp: " + view, view.getHeight() >= minimumPixels);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                assertTouchTargets(group.getChildAt(index), minimumPixels);
            }
        }
    }
}
