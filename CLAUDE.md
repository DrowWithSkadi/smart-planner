# Smart Planner — заметки для Claude

Учебный проект: планер задач. Бэкенд Java 21 + Spring Boot 3.5, Telegram-бот (TelegramBots 10.3,
long polling), позже — Telegram Mini App как фронтенд. Автор — студент, объяснять по-русски и просто.

## Правила

- Пакеты по фичам: `user`, `task`, `reminder`, `bot`, `common`, дальше `calendar` (ICS) и т.д.
- Слои: controller → service → repository. Бизнес-логика только в сервисах; бот и REST вызывают сервисы.
- Время храним в UTC (`Instant`), в часовой пояс пользователя переводим только при показе/вводе.
- Без Lombok — обычные геттеры/сеттеры и records для DTO.
- Комментарии в коде — на русском, коротко, объясняют «зачем».
- Токен бота — только в `local.properties` или переменной окружения, никогда в репозитории.
- Сборка: `mvn test`. Тесты на чистую логику (парсеры, расчёты) обязательны.
- Календари: `calendar/ics` — чистая логика без Spring (IcsParser, IcsFetcher), тестируется отдельно.
  События хранятся развёрнутыми (одна строка на повторение), ключ — (subscription, uid, startAt).
  Скачивание — вне транзакции, запись — в TransactionTemplate.
- REST API — только `/api/me/...`; пользователь берётся через `@CurrentUser AppUser` (подпись Telegram initData).
- Mini App: `static/app/` — ванильный JS без сборки, цвета из темы Telegram (`--tg-theme-*`), идея дизайна — день на тетрадном листе в клетку.
- Напоминания идут через `reminder.Reminder` (общий вид для задач и событий) → `ReminderSender`.
