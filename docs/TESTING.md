# Тестирование и QA

## Автоматические наборы

### JVM — 42 теста

- арифметика, приоритеты и правоассоциативная степень;
- унарные операции и математические функции;
- DEG/RAD и обратные тригонометрические функции;
- константы, `Ans`, неявное умножение и scientific notation;
- контекстные проценты;
- domain/division/overflow/syntax/complexity errors;
- форматирование чисел;
- переходы `CalculatorState`, live preview и восстановление.

Команда:

```powershell
.\gradlew.bat testDebugUnitTest
```

### Instrumented — 9 тестов на устройство

- холодный запуск и начальное состояние;
- сценарий `7 + 5 = 12` через реальный UI;
- восстановление после recreation;
- раскрытие scientific-панели;
- сохранение и фактическое применение Dark theme;
- минимальный touch-target 48 dp для всех видимых интерактивных элементов;
- Unicode round-trip истории;
- лимит 50 записей;
- восстановление после повреждённой записи и очистка.

Каждый тест запускается Android Test Orchestrator в отдельном процессе с очисткой package data.

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

## Поддерживаемая матрица

Локальный release gate использует обе границы диапазона:

| Среда | API | Назначение |
|---|---:|---|
| Android 6.0 | 23 | минимально поддерживаемая версия и legacy system UI |
| Android 17 | 37 | target/compile API, edge-to-edge и актуальные platform changes |

CI повторяет unit/lint/release build и device-тесты на API 23 и 36. API 37 остаётся обязательным локальным release gate до стабильной поддержки этого image на GitHub-hosted runners.

## Ручной smoke

Перед публикацией подписанный minified APK должен быть установлен с чистыми данными. Проверяются:

1. холодный запуск без crash/ANR;
2. `7 + 5 = 12`;
3. scientific functions и DEG/RAD;
4. history, copy и memory;
5. Light/Dark/System и recreation;
6. русский и английский locale;
7. отсутствие `FATAL EXCEPTION`, ANR и StrictMode violations в logcat;
8. подпись APK и отсутствие неожиданных permissions.

Нельзя закрывать release gate lint baseline, `ignoreFailures`, отключением тестов или удалением assertions.
