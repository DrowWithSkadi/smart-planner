package com.smartplanner.bot;

import com.smartplanner.calendar.CalendarService;
import com.smartplanner.calendar.CalendarSubscription;
import com.smartplanner.calendar.CalendarSyncService;
import com.smartplanner.calendar.ics.IcsException;
import com.smartplanner.common.NotFoundException;
import com.smartplanner.task.Task;
import com.smartplanner.task.TaskService;
import com.smartplanner.task.dto.TaskRequest;
import com.smartplanner.user.AppUser;
import com.smartplanner.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.AfterBotRegistration;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.DefaultLongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.menubutton.SetChatMenuButton;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.menubutton.MenuButtonWebApp;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Telegram-бот: принимает сообщения и нажатия на кнопки.
 * Сама логика задач живёт в TaskService — бот только переводит команды в вызовы сервиса.
 */
@Component
@ConditionalOnBotToken
public class PlannerBot extends DefaultLongPollingUpdateConsumer implements SpringLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(PlannerBot.class);

    /** Сообщение похоже на ссылку на календарь: webcal://… или http(s)://…ics */
    private static final Pattern CALENDAR_URL = Pattern.compile(
            "^(webcals?://\\S+|https?://\\S*(\\.ics|ical|calendar|webcal)\\S*)$",
            Pattern.CASE_INSENSITIVE);

    private final BotProperties properties;
    private final TelegramClient telegram;
    private final UserService userService;
    private final TaskService taskService;
    private final CalendarService calendarService;

    public PlannerBot(BotProperties properties, TelegramClient telegram,
                      UserService userService, TaskService taskService, CalendarService calendarService) {
        this.properties = properties;
        this.telegram = telegram;
        this.userService = userService;
        this.taskService = taskService;
        this.calendarService = calendarService;
    }

    @Override
    public String getBotToken() {
        return properties.token();
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    /**
     * После запуска бота: регистрируем список команд (появится в меню «/» в Telegram)
     * и, если задан адрес Mini App, кнопку «Планер» слева от поля ввода.
     */
    @AfterBotRegistration
    public void afterRegistration() {
        execute(SetMyCommands.builder()
                .command(new BotCommand("today", "План на сегодня"))
                .command(new BotCommand("tomorrow", "План на завтра"))
                .command(new BotCommand("list", "Ближайшие задачи"))
                .command(new BotCommand("calendars", "Мои календари"))
                .command(new BotCommand("help", "Подсказка"))
                .build());
        if (properties.hasWebApp()) {
            execute(SetChatMenuButton.builder()
                    .menuButton(MenuButtonWebApp.builder()
                            .text("Планер")
                            .webAppInfo(new WebAppInfo(properties.webAppUrl()))
                            .build())
                    .build());
            log.info("Кнопка Mini App установлена: {}", properties.webAppUrl());
        }
    }

    @Override
    public void consume(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                handleCallback(update.getCallbackQuery());
            } else if (update.hasMessage() && update.getMessage().hasText()) {
                handleText(update.getMessage());
            }
        } catch (Exception e) {
            log.error("Ошибка при обработке сообщения", e);
        }
    }

    // ---------- Текстовые сообщения ----------

    private void handleText(Message message) {
        User from = message.getFrom();
        AppUser user = userService.findOrCreate(from.getId(), message.getChatId(),
                from.getFirstName(), from.getUserName());
        ZoneId zone = user.zoneId();
        String text = message.getText().trim();

        // "/add@MyBot купить" → команда "/add", аргумент "купить"
        String command = text.startsWith("/") ? text.split("\\s+", 2)[0].split("@")[0].toLowerCase(Locale.ROOT) : "";
        String argument = text.startsWith("/") && text.contains(" ") ? text.substring(text.indexOf(' ') + 1).trim() : "";

        switch (command) {
            case "/start" -> send(user.getChatId(), BotMessages.welcome(from.getFirstName()), startButtons());
            case "/help" -> send(user.getChatId(), BotMessages.help(), null);
            case "/today" -> sendAgenda(user, LocalDate.now(zone), "📅 План на сегодня:", "На сегодня ничего нет 🎉");
            case "/tomorrow" -> sendAgenda(user, LocalDate.now(zone).plusDays(1), "📅 План на завтра:",
                    "На завтра ничего нет 🎉");
            case "/list" -> {
                List<Task> tasks = taskService.findUpcoming(user.getId());
                send(user.getChatId(), BotMessages.taskList("🗂 Ближайшие задачи:", tasks, zone,
                        "Предстоящих задач нет. Напиши, например: завтра 10:00 Сходить в зал"), null);
            }
            case "/add" -> addTask(user, argument);
            case "/calendars" -> sendCalendars(user);
            case "/subscribe" -> {
                if (argument.isBlank()) {
                    send(user.getChatId(), "Пришли ссылку так: /subscribe webcal://… или просто отправь ссылку", null);
                } else {
                    subscribe(user, argument);
                }
            }
            case "" -> {
                if (CALENDAR_URL.matcher(text).matches()) {
                    subscribe(user, text); // прислали ссылку на календарь — подписываемся
                } else {
                    addTask(user, text); // обычный текст без команды — новая задача
                }
            }
            default -> send(user.getChatId(), "Не знаю такую команду. Список команд — /help", null);
        }
    }

    private void addTask(AppUser user, String text) {
        ZoneId zone = user.zoneId();
        try {
            QuickAddParser.ParsedTask parsed = QuickAddParser.parse(text, LocalDate.now(zone));
            Task task = taskService.create(user.getId(),
                    new TaskRequest(parsed.title(), null, parsed.dueAt(), null));
            send(user.getChatId(), BotMessages.taskCreated(task, zone), BotMessages.createdButtons(task.getId()));
        } catch (IllegalArgumentException e) {
            send(user.getChatId(), "🤔 " + e.getMessage() + "\n\n" + BotMessages.help(), null);
        }
    }

    private void sendAgenda(AppUser user, LocalDate day, String header, String emptyText) {
        ZoneId zone = user.zoneId();
        List<Task> tasks = taskService.findForDay(user.getId(), day);
        var events = calendarService.findForDay(user.getId(), day);
        send(user.getChatId(), BotMessages.agenda(header, tasks, events, zone, emptyText), null);
    }

    // ---------- Календари ----------

    private void subscribe(AppUser user, String url) {
        send(user.getChatId(), "⏳ Загружаю календарь…", null);
        try {
            CalendarService.SubscribeResult result = calendarService.subscribe(user.getId(), url, null);
            send(user.getChatId(), BotMessages.calendarSubscribed(result.subscription(), result.eventCount()), null);
        } catch (IcsException e) {
            send(user.getChatId(), "⚠️ " + e.getMessage(), null);
        }
    }

    private void sendCalendars(AppUser user) {
        List<CalendarSubscription> subscriptions = calendarService.list(user.getId());
        send(user.getChatId(), BotMessages.calendarList(subscriptions, user.zoneId()),
                subscriptions.isEmpty() ? null : BotMessages.calendarButtons(subscriptions));
    }

    // ---------- Нажатия на кнопки ----------

    private void handleCallback(CallbackQuery callback) {
        String answer;
        String status = null;
        try {
            AppUser user = userService.findByTelegramId(callback.getFrom().getId())
                    .orElseThrow(() -> new NotFoundException("Сначала напиши /start"));
            String[] parts = callback.getData().split(":");
            Long id = Long.parseLong(parts[1]); // ID задачи или календаря — зависит от кнопки

            switch (parts[0]) {
                case BotMessages.CB_DONE -> {
                    taskService.setDone(user.getId(), id, true);
                    answer = "Отмечено как выполненное";
                    status = "✅ Выполнено";
                }
                case BotMessages.CB_SNOOZE -> {
                    int minutes = Integer.parseInt(parts[2]);
                    taskService.snooze(user.getId(), id, minutes);
                    answer = "Напомню через " + minutes + " мин";
                    status = "⏰ Отложено на " + minutes + " мин";
                }
                case BotMessages.CB_DELETE -> {
                    taskService.delete(user.getId(), id);
                    answer = "Задача удалена";
                    status = "🗑 Удалено";
                }
                case BotMessages.CB_UNSUBSCRIBE -> {
                    String name = calendarService.get(user.getId(), id).getName();
                    calendarService.unsubscribe(user.getId(), id);
                    answer = "Отписался от «" + name + "»";
                    status = "🗑 Отписался от «" + name + "»";
                }
                case BotMessages.CB_SYNC -> {
                    CalendarSyncService.SyncResult result = calendarService.syncNow(user.getId(), id);
                    answer = result.ok()
                            ? (result.notModified() ? "Изменений нет" : "Обновлено, событий: " + (result.added() + result.updated()))
                            : "Ошибка: " + result.error();
                }
                default -> answer = "Неизвестное действие";
            }
        } catch (NotFoundException e) {
            answer = e.getMessage();
        } catch (RuntimeException e) {
            log.warn("Некорректные данные кнопки: {}", callback.getData(), e);
            answer = "Что-то пошло не так";
        }

        execute(AnswerCallbackQuery.builder().callbackQueryId(callback.getId()).text(answer).build());
        if (status != null) {
            appendStatus(callback.getMessage(), status);
        }
    }

    /** Дописывает статус к сообщению с кнопками и убирает кнопки. */
    private void appendStatus(MaybeInaccessibleMessage original, String status) {
        if (!(original instanceof Message message) || message.getText() == null) {
            return;
        }
        execute(EditMessageText.builder()
                .chatId(message.getChatId())
                .messageId(message.getMessageId())
                .text(message.getText() + "\n\n" + status)
                .build());
    }

    // ---------- Вспомогательное ----------

    private InlineKeyboardMarkup startButtons() {
        if (!properties.hasWebApp()) {
            return null;
        }
        InlineKeyboardButton open = InlineKeyboardButton.builder()
                .text("📅 Открыть планер")
                .webApp(new WebAppInfo(properties.webAppUrl()))
                .build();
        return InlineKeyboardMarkup.builder().keyboardRow(new InlineKeyboardRow(open)).build();
    }

    private void send(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage message = SendMessage.builder().chatId(chatId).text(text).build();
        if (keyboard != null) {
            message.setReplyMarkup(keyboard);
        }
        execute(message);
    }

    private <T extends Serializable> void execute(BotApiMethod<T> method) {
        try {
            telegram.execute(method);
        } catch (TelegramApiException e) {
            log.error("Не удалось отправить запрос в Telegram", e);
        }
    }
}
