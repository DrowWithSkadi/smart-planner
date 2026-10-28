# 📅 Smart Planner

Умный планер задач: бэкенд на Java (Spring Boot), напоминания приходят в Telegram,
а интерфейс — сайт, который открывается прямо в Telegram (Mini App).

## Что уже умеет (версия 0.1)

- Telegram-бот: задачу можно добавить обычным сообщением
  - `Купить хлеб` — без времени
  - `18:00 Тренировка` — сегодня
  - `завтра 9:30 Пара по Java`
  - `29.10 18:00 Встреча`, `05.11.2026 Сдать проект`
- Команды бота: `/today` — задачи на сегодня, `/list` — ближайшие, `/help`
- Напоминание за 15 минут до задачи с кнопками **✅ Готово**, **⏰ +15 мин**, **⏰ +1 час**
- REST API для задач (его будет использовать сайт)
- База данных H2 — ничего не нужно устанавливать, данные хранятся в папке `data/`

## Технологии

| Что | Чем |
|---|---|
| Язык | Java 21 |
| Фреймворк | Spring Boot 3.5 (Web, Data JPA, Validation, Scheduling) |
| База данных | H2 (позже — PostgreSQL) |
| Telegram | [TelegramBots](https://github.com/rubenlagus/TelegramBots) 10.3 |
| Сборка | Maven |

## Запуск

1. Установи **JDK 21** и **IntelliJ IDEA**.
2. Открой проект: **File → New → Project from Version Control**, вставь ссылку на этот репозиторий.
   IntelliJ сам скачает зависимости (Maven) — подожди, пока внизу закончится индексация.
3. Создай бота у [@BotFather](https://t.me/BotFather) (`/newbot`) и получи токен.
4. В корне проекта скопируй файл `local.properties.example` в `local.properties` и впиши токен:
   ```properties
   telegram.bot.token=123456:ABC...
   telegram.bot.username=my_planner_bot
   ```
   Файл `local.properties` в `.gitignore` — в GitHub он не попадёт. **Никогда не коммить токен.**
5. Запусти `SmartPlannerApplication` (зелёная стрелка рядом с `main`).
6. Напиши своему боту `/start`.

Без токена приложение тоже запускается: работает API, а бот выключен.

Проверка, что сервер жив: <http://localhost:8080>

## API

Пока пользователь указывается в адресе. `userId` можно узнать на `GET /api/users`.

| Метод | Адрес | Что делает |
|---|---|---|
| GET | `/api/users` | список пользователей |
| GET | `/api/users/{userId}/tasks` | все задачи |
| GET | `/api/users/{userId}/tasks?date=2026-10-29` | задачи на день |
| POST | `/api/users/{userId}/tasks` | создать задачу |
| PUT | `/api/users/{userId}/tasks/{taskId}` | изменить задачу |
| PATCH | `/api/users/{userId}/tasks/{taskId}/done?value=true` | отметить выполненной |
| DELETE | `/api/users/{userId}/tasks/{taskId}` | удалить |

Пример тела для создания задачи:

```json
{
  "title": "Пара по базам данных",
  "description": "Аудитория 204",
  "dueAt": "2026-10-29T10:00",
  "remindBeforeMinutes": 15
}
```

## Структура проекта

```
src/main/java/com/smartplanner/
├── SmartPlannerApplication.java   — точка входа
├── user/       — пользователи (создаются по /start)
├── task/       — задачи: сущность, репозиторий, сервис, REST-контроллер
├── reminder/   — планировщик, который рассылает напоминания
├── bot/        — Telegram-бот, тексты сообщений, разбор быстрого ввода
└── common/     — общие классы (ошибки)
```

## План развития

- [x] Каркас: Spring Boot, база, задачи, бот, напоминания
- [ ] Подписка на календарь по ссылке (ICS / webcal) с автообновлением — расписание пар
- [ ] Mini App: красивый интерфейс с календарём внутри Telegram
- [ ] Повторяющиеся задачи (каждый день, по будням…)
- [ ] Утренний дайджест и вечерний итог
- [ ] Своя ICS-ссылка, чтобы видеть задачи в Apple/Google Календаре
- [ ] Ввод задач свободным текстом и голосом через нейросеть
- [ ] PostgreSQL и Docker
