# Release runbook

## 1. Предварительные условия

- чистый рабочий каталог;
- JDK 21;
- Android SDK Platform 37, Platform Tools и Build Tools 36.0.0;
- publisher-owned upload key, сохранённый вне репозитория;
- включённый Google Play App Signing;
- уникальный и подтверждённый в Play Console `applicationId`.

`applicationId` по умолчанию — `com.numina.calculator`. До первого публичного релиза владелец продукта обязан подтвердить, что этот ID доступен и принадлежит ему: после публикации изменить его без создания нового приложения нельзя.

## 2. Версия

Обновите в `app/build.gradle.kts`:

- `versionCode` — строго увеличивающееся целое;
- `versionName` — SemVer для пользователя.

Обновите `CHANGELOG.md` и store listing при изменении поведения или privacy.

## 3. Подпись

Gradle принимает ключ только из окружения:

| Переменная | Значение |
|---|---|
| `NUMINA_KEYSTORE_PATH` | абсолютный путь к JKS/PKCS12 |
| `NUMINA_KEYSTORE_PASSWORD` | пароль keystore |
| `NUMINA_KEY_ALIAS` | alias upload key |
| `NUMINA_KEY_PASSWORD` | пароль ключа |

Не храните значения в `gradle.properties`, `local.properties`, CI logs или репозитории. В CI используйте encrypted environment secrets и ограничьте доступ к production environment.

## 4. Release gate

```powershell
.\scripts\verify.ps1 -Connected
.\scripts\build-release.ps1
```

`build-release.ps1` откажется собирать публикационный artifact, если отсутствует хотя бы одна signing variable.

Проверьте APK:

```powershell
$buildTools = "$env:ANDROID_SDK_ROOT\build-tools\36.0.0"
& "$buildTools\apksigner.bat" verify --verbose --print-certs app\build\outputs\apk\release\app-release.apk
& "$buildTools\aapt.exe" dump permissions app\build\outputs\apk\release\app-release.apk
```

Ожидается корректная v1/v2+ подпись и отсутствие `uses-permission`.

## 5. Play Console

1. Создайте internal testing release и загрузите `app-release.aab`.
2. Заполните Data safety: данные не собираются и не передаются; история хранится только на устройстве.
3. Укажите privacy policy из `PRIVACY.md` на публичной HTTPS-странице.
4. Заполните content rating, target audience, ads declaration (`No ads`) и app access (`No restricted access`).
5. Проверьте pre-launch report на доступных устройствах.
6. Проведите internal/closed testing и staged rollout.
7. Сохраните mapping file из `app/build/outputs/mapping/release/mapping.txt` рядом с релизом для расшифровки stack traces.

## 6. Rollback

Google Play не позволяет откатить `versionCode`. Для исправления создайте новую версию с большим `versionCode`, приложите предыдущую стабильную кодовую базу или hotfix и повторите весь release gate. Используйте staged rollout, чтобы ограничить blast radius.

## 7. Архив релиза

Храните в защищённом release storage:

- AAB и SHA-256;
- mapping/resources mapping;
- changelog и release report;
- commit/tag;
- Play Console pre-launch report;
- certificate fingerprint upload key.

Сам private/upload key и пароли хранятся отдельно от artifacts.
