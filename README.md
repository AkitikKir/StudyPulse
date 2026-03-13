# StudyPulse

Android MVP для микрообучения с интервальными повторениями, push-напоминаниями, геймификацией, CSV-импортом и AI-автозаполнением через Groq.

## Реализовано

- Карточки `Термин -> Определение` (+ optional `imageUri`).
- Алгоритм интервальных повторений SM-2-lite (`Сложно / Понял / Легко`).
- Сессия повторений с flip-анимацией карточки.
- Push-уведомления через WorkManager.
- Геймификация:
  - daily streak,
  - best streak,
  - total reviews,
  - достижения.
- Импорт CSV через системный file picker.
- AI-функция Groq:
  - автозаполнение определения/перевода/примера,
  - кэш результатов в Room,
  - обработка ошибок и fallback на кэш.
- Firebase интеграция:
  - Google Auth,
  - GitHub Auth,
  - (optional) Anonymous Auth fallback,
  - Realtime Database sync (`users/{uid}/studypulse`).
- Адаптивный визуал для светлой/темной темы.

## CSV формат

Допустимы строки вида:

```csv
term,definition,imageUri
abnegation,самоотречение,https://example.com/img.png
entropy,мера беспорядка,
```

`imageUri` можно не указывать.

## Настройка Groq API

В `~/.gradle/gradle.properties` или в проектном `gradle.properties`:

```properties
GROQ_API_KEY=your_groq_api_key
```

Если ключ не задан, кнопка AI покажет понятную ошибку и приложение продолжит работать без AI.

## GitHub Secrets для CI

В репозитории добавьте secrets:

- `GROQ_API_KEY` — ключ Groq для автозаполнения.
- `GOOGLE_SERVICES_JSON` — полный JSON содержимого `google-services.json`.

Workflow автоматически:

1. создает `app/google-services.json` из `GOOGLE_SERVICES_JSON`;
2. добавляет `GROQ_API_KEY` в `~/.gradle/gradle.properties`;
3. собирает debug APK.

## CI/CD

Workflow: `.github/workflows/android-debug-apk.yml`

- запускается на push в `main` и вручную;
- собирает `:app:assembleDebug`;
- публикует `app-debug.apk` как artifact `studypulse-debug-apk`.

## Демо на защите

1. Создание карточки вручную.
2. AI-автозаполнение по термину.
3. Импорт набора из CSV.
4. Сессия повторения: flip + оценка + изменение следующей даты.
5. Рост streak и достижений.
6. Уведомление о повторении.
7. Ссылка на последний CI artifact APK.
