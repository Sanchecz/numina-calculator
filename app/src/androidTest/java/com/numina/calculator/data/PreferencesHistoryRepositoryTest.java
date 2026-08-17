package com.numina.calculator.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class PreferencesHistoryRepositoryTest {
    private SharedPreferences preferences;
    private PreferencesHistoryRepository repository;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        preferences = context.getSharedPreferences("history_repository_test", Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
        repository = new PreferencesHistoryRepository(preferences);
    }

    @Test
    public void roundTripsUnicodeHistory() {
        repository.add(new HistoryEntry("2*pi+sqrt(9)", "9.28318530718", 1234L));
        HistoryEntry restored = repository.load().get(0);
        assertEquals("2*pi+sqrt(9)", restored.getExpression());
        assertEquals("9.28318530718", restored.getResult());
        assertEquals(1234L, restored.getCreatedAtEpochMillis());
    }

    @Test
    public void keepsNewestFiftyEntries() {
        for (int index = 0; index < 60; index++) {
            repository.add(new HistoryEntry(String.valueOf(index), String.valueOf(index), index));
        }
        assertEquals(50, repository.load().size());
        assertEquals("59", repository.load().get(0).getExpression());
        assertEquals("10", repository.load().get(49).getExpression());
    }

    @Test
    public void ignoresCorruptRecordsAndCanClear() {
        preferences.edit().putString("calculation_history_v1", "not-a-record").commit();
        assertTrue(repository.load().isEmpty());
        repository.add(new HistoryEntry("1+1", "2", 1L));
        repository.clear();
        assertTrue(repository.load().isEmpty());
    }
}
