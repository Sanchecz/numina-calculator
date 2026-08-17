# Numina Calculator

Numina — нативный офлайн-калькулятор для Android 6.0–17. Он поддерживает обычные и научные вычисления, контекстные проценты, память, историю, светлую и тёмную темы и не требует сетевого доступа или Android-разрешений.

## Возможности

- базовые операции, скобки, степень, факториал и неявное умножение;
- `sin`, `cos`, `tan`, обратные функции, `sqrt`, `ln`, `log`, `abs`, `exp`;
- режимы DEG/RAD, константы π/e и повторное использование `Ans`;
- контекстные проценты: `200 + 10% = 220`, `200 × 10% = 20`;
- память `MC`, `MR`, `M+`, `M−` и локальная история до 50 вычислений;
- аппаратная клавиатура, копирование результата, haptic feedback;
- английская и русская локализация, RTL, светлая/тёмная/системная тема;
- адаптивная раскладка для компактных экранов и touch-target не менее 48 dp;
- полностью офлайн: нет `INTERNET`, рекламы, аналитики и runtime-зависимостей.

## Стек

- Java 17 source level, JDK 21 для воспроизводимой сборки;
- Android Gradle Plugin 9.3.0, Gradle 9.7.0;
- `compileSdk`/`targetSdk` 37, `minSdk` 23;
- Android framework UI без production-библиотек;
- JUnit 4, AndroidX Test, Espresso и Android Test Orchestrator;
- R8, resource shrinking, строгий Android Lint и `javac -Xlint:all -Werror`.

## Быстрый старт

Требуются JDK 21 и Android SDK Platform 37 с Build Tools 36.0.0. Укажите SDK в `local.properties` или через `ANDROID_SDK_ROOT`, затем выполните:

```powershell
.\gradlew.bat assembleDebug
```

Установка на подключённое устройство:

```powershell
.\gradlew.bat installDebug
```

Полная локальная проверка без device-тестов:

```powershell
.\scripts\verify.ps1
```

С подключённым эмулятором или устройством:

```powershell
.\scripts\verify.ps1 -Connected
```

## Release-сборка

Подпись берётся только из переменных окружения; ключи и пароли не хранятся в репозитории:

```powershell
$env:NUMINA_KEYSTORE_PATH = 'C:\secure\numina-upload.jks'
$env:NUMINA_KEYSTORE_PASSWORD = '<secret>'
$env:NUMINA_KEY_ALIAS = 'numina-upload'
$env:NUMINA_KEY_PASSWORD = '<secret>'
.\scripts\build-release.ps1
```

Готовые файлы появляются в `app/build/outputs/apk/release/` и `app/build/outputs/bundle/release/`. Подробный release-процесс, ротация ключей и Play Console checklist описаны в [docs/RELEASE.md](docs/RELEASE.md).

## Документация

- [Архитектура](docs/ARCHITECTURE.md)
- [Тестирование и QA](docs/TESTING.md)
- [Release runbook](docs/RELEASE.md)
- [Google Play listing](docs/STORE_LISTING.md)
- [Privacy](PRIVACY.md)
- [Security policy](SECURITY.md)
- [История изменений](CHANGELOG.md)

## Лицензия

MIT, см. [LICENSE](LICENSE).
