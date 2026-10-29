/*
 * Smart Planner — Mini App.
 * Обычный JavaScript без сборки: открой файл и читай сверху вниз.
 *
 * Как устроено:
 *  1. Telegram передаёт в Mini App подписанные данные пользователя (tg.initData).
 *     Мы отправляем их в заголовке Authorization, сервер проверяет подпись и понимает, кто мы.
 *  2. Сервер отдаёт время уже в часовом поясе пользователя ("2026-10-30T10:00:00"),
 *     поэтому здесь мы работаем с датами как с простыми строками и не пересчитываем пояса.
 */
(() => {
    "use strict";

    const tg = window.Telegram?.WebApp;
    const HOUR_PX = 56;          // высота часа — 4 клетки по 14px (см. --hour в CSS)
    const MIN_BLOCK_PX = 28;     // самый короткий блок — 2 клетки
    const TASK_VISUAL_MIN = 30;  // задача занимает на листе полчаса
    const PENS = 5;              // сколько цветов «ручек» для календарей

    const MONTHS = ["январь", "февраль", "март", "апрель", "май", "июнь", "июль", "август",
        "сентябрь", "октябрь", "ноябрь", "декабрь"];
    const MONTHS_GEN = ["января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа",
        "сентября", "октября", "ноября", "декабря"];
    const WEEKDAYS = ["пн", "вт", "ср", "чт", "пт", "сб", "вс"];
    const WEEKDAYS_FULL = ["понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье"];

    const $ = (id) => document.getElementById(id);

    // ---------- Состояние ----------

    const state = {
        selected: todayStr(),   // выбранный день "YYYY-MM-DD"
        weekStart: null,        // понедельник показанной недели
        tasks: [],              // задачи показанной недели
        events: [],             // события показанной недели
        inbox: [],              // задачи без даты
        calendars: [],
        monthCursor: null,      // какой месяц открыт в окне выбора месяца
        editingTask: null,
    };

    // ---------- Даты (строки YYYY-MM-DD) ----------

    function pad(n) { return String(n).padStart(2, "0"); }
    function toStr(d) { return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`; }
    function fromStr(s) { const [y, m, d] = s.split("-").map(Number); return new Date(y, m - 1, d); }
    function todayStr() { return toStr(new Date()); }
    function addDays(s, n) { const d = fromStr(s); d.setDate(d.getDate() + n); return toStr(d); }
    function mondayOf(s) { const d = fromStr(s); const wd = (d.getDay() + 6) % 7; d.setDate(d.getDate() - wd); return toStr(d); }
    function weekdayIndex(s) { return (fromStr(s).getDay() + 6) % 7; }
    function datePart(dt) { return dt.slice(0, 10); }
    function timePart(dt) { return dt.slice(11, 16); }
    function minutesOf(dt) { return Number(dt.slice(11, 13)) * 60 + Number(dt.slice(14, 16)); }

    function humanDate(s) {
        const d = fromStr(s);
        return `${d.getDate()} ${MONTHS_GEN[d.getMonth()]}`;
    }

    // ---------- Запросы к серверу ----------

    async function api(path, options = {}) {
        const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
        if (tg?.initData) {
            headers.Authorization = "tma " + tg.initData;
        }
        const response = await fetch(path, { ...options, headers });
        if (response.status === 204) return null;
        const body = await response.json().catch(() => null);
        if (!response.ok) {
            const error = new Error(body?.error || `Ошибка сервера (${response.status})`);
            error.status = response.status;
            throw error;
        }
        return body;
    }

    // ---------- Небольшие помощники интерфейса ----------

    function el(tag, className, text) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        if (text != null) node.textContent = text; // textContent — безопасно, HTML не исполняется
        return node;
    }

    let toastTimer;
    function toast(message) {
        const node = $("toast");
        node.textContent = message;
        node.hidden = false;
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => { node.hidden = true; }, 2600);
    }

    function haptic(type = "light") {
        tg?.HapticFeedback?.impactOccurred(type);
    }

    function penOf(calendarId) {
        return `var(--pen-${((calendarId - 1) % PENS) + 1})`;
    }

    function showFatal(message) {
        const node = $("fatal");
        node.textContent = message;
        node.hidden = false;
    }

    // ---------- Загрузка данных ----------

    async function loadWeek() {
        const from = state.weekStart;
        const to = addDays(from, 6);
        const [tasks, events, inbox] = await Promise.all([
            api(`/api/me/tasks?from=${from}&to=${to}`),
            api(`/api/me/events?from=${from}&to=${to}`),
            api("/api/me/tasks/inbox"),
        ]);
        state.tasks = tasks;
        state.events = events;
        state.inbox = inbox;
    }

    async function selectDate(dateStr) {
        const weekStart = mondayOf(dateStr);
        state.selected = dateStr;
        if (weekStart !== state.weekStart) {
            state.weekStart = weekStart;
            renderWeek(); // сразу показываем новую неделю, данные догрузятся
            await loadWeek();
        }
        renderPlan();
    }

    async function reload() {
        await loadWeek();
        renderPlan();
    }

    // ---------- Экран «План» ----------

    function renderPlan() {
        renderWeek();
        renderDay();
        renderInbox();
    }

    function itemsOfDay(day) {
        return {
            tasks: state.tasks.filter((t) => t.dueAt && datePart(t.dueAt) === day),
            events: state.events.filter((e) => {
                const start = datePart(e.startAt);
                // событие могло начаться вчера и закончиться сегодня; у событий на весь день конец «исключительный»
                const end = e.allDay ? addDays(datePart(e.endAt), -1) : datePart(e.endAt);
                return start <= day && day <= end;
            }),
        };
    }

    function renderWeek() {
        const selected = fromStr(state.selected);
        $("month-title").textContent = MONTHS[selected.getMonth()];
        const week = $("week");
        week.replaceChildren();
        const today = todayStr();
        for (let i = 0; i < 7; i++) {
            const day = addDays(state.weekStart, i);
            const button = el("button", "day");
            button.type = "button";
            button.setAttribute("aria-selected", String(day === state.selected));
            button.setAttribute("aria-label", humanDate(day));
            if (day === today) button.classList.add("is-today");
            button.append(el("span", "day-name", WEEKDAYS[i]), el("span", "day-num", String(fromStr(day).getDate())));

            const dots = el("span", "day-dots");
            const { tasks, events } = itemsOfDay(day);
            const count = Math.min(3, tasks.length + events.length);
            for (let k = 0; k < count; k++) dots.append(el("i"));
            button.append(dots);

            button.addEventListener("click", () => { haptic("light"); selectDate(day); });
            week.append(button);
        }
        $("today-btn").hidden = state.selected === today;
    }

    function renderDay() {
        const day = state.selected;
        const { tasks, events } = itemsOfDay(day);

        // заголовок дня
        const head = $("day-head");
        head.replaceChildren();
        const title = el("strong", null, `${WEEKDAYS_FULL[weekdayIndex(day)]}, ${humanDate(day)}`);
        const doneCount = tasks.filter((t) => t.done).length;
        const summary = tasks.length ? `задачи: ${doneCount} из ${tasks.length}` : "";
        head.append(title, el("span", null, summary));

        // события на весь день — «наклейки» над листом
        const allday = $("allday");
        allday.replaceChildren();
        for (const e of events.filter((x) => x.allDay)) {
            const chip = el("button", "chip", e.title);
            chip.type = "button";
            chip.style.setProperty("--pen", penOf(e.calendarId));
            chip.addEventListener("click", () => openEvent(e));
            allday.append(chip);
        }

        renderTimeline(day, events.filter((x) => !x.allDay), tasks);
    }

    /**
     * Рисует день на тетрадном листе.
     * События и задачи раскладываются по колонкам, если пересекаются по времени.
     */
    function renderTimeline(day, events, tasks) {
        const items = [];
        for (const e of events) {
            // если событие переходит через полночь — обрезаем его границами дня
            const start = datePart(e.startAt) < day ? 0 : minutesOf(e.startAt);
            const end = datePart(e.endAt) > day ? 24 * 60 : minutesOf(e.endAt);
            items.push({ type: "event", start, end: Math.max(end, start + 15), data: e });
        }
        for (const t of tasks) {
            const start = minutesOf(t.dueAt);
            items.push({ type: "task", start, end: start + TASK_VISUAL_MIN, data: t });
        }
        items.sort((a, b) => a.start - b.start || b.end - a.end);

        // видимые часы: как минимум 8:00–21:00, шире — если есть что показать
        let startHour = 8;
        let endHour = 21;
        for (const item of items) {
            startHour = Math.min(startHour, Math.floor(item.start / 60));
            endHour = Math.max(endHour, Math.ceil(item.end / 60));
        }
        endHour = Math.min(endHour, 24);

        const timeline = $("timeline");
        timeline.replaceChildren();
        timeline.style.height = `${(endHour - startHour) * HOUR_PX}px`;

        for (let h = startHour; h <= endHour; h++) {
            const label = el("span", "hour-label", `${pad(h % 24)}:00`);
            label.style.top = `${(h - startHour) * HOUR_PX}px`;
            label.setAttribute("aria-hidden", "true");
            timeline.append(label);
        }

        const top = (minutes) => ((minutes - startHour * 60) / 60) * HOUR_PX;

        // раскладка по колонкам: группы пересекающихся элементов делят ширину поровну
        let group = [];
        let groupEnd = -1;
        const flush = () => {
            const columns = [];
            for (const item of group) {
                let col = columns.findIndex((lastEnd) => lastEnd <= item.start);
                if (col === -1) { col = columns.length; columns.push(0); }
                columns[col] = item.end;
                item.col = col;
            }
            for (const item of group) item.cols = columns.length;
            group = [];
        };
        for (const item of items) {
            if (item.start >= groupEnd && group.length) flush();
            group.push(item);
            groupEnd = Math.max(groupEnd, item.end);
        }
        if (group.length) flush();

        const now = new Date();
        const nowMinutes = now.getHours() * 60 + now.getMinutes();
        const isToday = day === todayStr();
        const isPastDay = day < todayStr();

        for (const item of items) {
            const node = item.type === "event" ? eventNode(item.data) : taskLine(item.data);
            const width = 100 / item.cols;
            node.style.left = `calc(${item.col * width}% + 4px)`;
            node.style.width = `calc(${width}% - 6px)`;
            node.style.top = `${top(item.start) + 1}px`;
            if (item.type === "event") {
                const height = Math.max(top(item.end) - top(item.start), MIN_BLOCK_PX) - 2;
                node.style.height = `${height}px`;
                if (height < 44) node.classList.add("is-short");
                if (isPastDay || (isToday && item.end <= nowMinutes)) node.classList.add("is-past");
            }
            timeline.append(node);
        }

        if (!items.length) {
            const empty = el("p", "empty-day", "Свободный день. Нажми «+», чтобы добавить задачу.");
            empty.style.top = `${HOUR_PX}px`;
            empty.style.margin = "0";
            timeline.append(empty);
        }

        if (isToday && nowMinutes >= startHour * 60 && nowMinutes <= endHour * 60) {
            const line = el("div", "now-line");
            line.style.top = `${top(nowMinutes)}px`;
            line.setAttribute("aria-hidden", "true");
            timeline.append(line);
        }
    }

    function eventNode(e) {
        const node = el("button", "event");
        node.type = "button";
        node.style.setProperty("--pen", penOf(e.calendarId));
        node.append(el("span", "event-title", e.title));
        const meta = [`${timePart(e.startAt)}–${timePart(e.endAt)}`];
        if (e.location) meta.push(e.location);
        node.append(el("span", "event-meta", meta.join(", ")));
        node.addEventListener("click", () => openEvent(e));
        return node;
    }

    function taskLine(t) {
        const row = el("div", "task-line" + (t.done ? " is-done" : ""));
        row.append(checkbox(t));
        const text = el("button", "task-text");
        text.type = "button";
        text.append(el("span", "task-time", timePart(t.dueAt)), document.createTextNode(t.title));
        text.addEventListener("click", () => openTask(t));
        row.append(text);
        return row;
    }

    function checkbox(t) {
        const check = el("button", "check");
        check.type = "button";
        check.setAttribute("role", "checkbox");
        check.setAttribute("aria-checked", String(t.done));
        check.setAttribute("aria-label", t.done ? `Снять отметку: ${t.title}` : `Отметить выполненной: ${t.title}`);
        check.addEventListener("click", async () => {
            const done = !t.done;
            haptic(done ? "medium" : "light");
            try {
                await api(`/api/me/tasks/${t.id}/done?value=${done}`, { method: "PATCH" });
                t.done = done;
                if (done && !t.dueAt) state.inbox = state.inbox.filter((x) => x.id !== t.id);
                renderPlan();
            } catch (err) {
                toast(err.message);
            }
        });
        return check;
    }

    function renderInbox() {
        const list = $("inbox-list");
        list.replaceChildren();
        $("inbox").hidden = state.inbox.length === 0;
        for (const t of state.inbox) {
            const li = el("li", "task-item" + (t.done ? " is-done" : ""));
            li.append(checkbox(t));
            const text = el("button", "task-text", t.title);
            text.type = "button";
            text.addEventListener("click", () => openTask(t));
            li.append(text);
            list.append(li);
        }
    }

    // ---------- Нижние окна ----------

    const openSheets = [];

    function openSheet(id) {
        const sheet = $(id);
        sheet.hidden = false;
        openSheets.push(sheet);
        tg?.BackButton?.show();
    }

    function closeSheet() {
        const sheet = openSheets.pop();
        if (sheet) sheet.hidden = true;
        if (!openSheets.length) tg?.BackButton?.hide();
    }

    document.querySelectorAll("[data-close]").forEach((node) => node.addEventListener("click", closeSheet));
    document.addEventListener("keydown", (e) => { if (e.key === "Escape") closeSheet(); });
    tg?.BackButton?.onClick(closeSheet);

    // ---------- Задача: создание и редактирование ----------

    function openTask(task) {
        state.editingTask = task || null;
        $("task-sheet-title").textContent = task ? "Задача" : "Новая задача";
        $("task-title").value = task?.title || "";
        $("task-desc").value = task?.description || "";
        $("task-date").value = task ? (task.dueAt ? datePart(task.dueAt) : "") : state.selected;
        $("task-time").value = task?.dueAt ? timePart(task.dueAt) : "";
        $("task-remind").value = String(task?.remindBeforeMinutes ?? 15);
        $("task-delete").hidden = !task;
        openSheet("task-sheet");
        if (!task) setTimeout(() => $("task-title").focus(), 250);
    }

    $("task-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        const title = $("task-title").value.trim();
        if (!title) return;
        const date = $("task-date").value;
        const time = $("task-time").value || "09:00"; // дата без времени — значит утром
        const body = {
            title,
            description: $("task-desc").value.trim() || null,
            dueAt: date ? `${date}T${time}` : null,
            remindBeforeMinutes: Number($("task-remind").value),
        };
        const submit = event.submitter;
        if (submit) submit.disabled = true;
        try {
            const task = state.editingTask;
            if (task) {
                await api(`/api/me/tasks/${task.id}`, { method: "PUT", body: JSON.stringify(body) });
            } else {
                await api("/api/me/tasks", { method: "POST", body: JSON.stringify(body) });
            }
            tg?.HapticFeedback?.notificationOccurred("success");
            closeSheet();
            if (date && mondayOf(date) !== state.weekStart) {
                state.weekStart = null; // переходим на неделю новой задачи
                await selectDate(date);
            } else {
                await reload();
            }
            toast(task ? "Задача сохранена" : "Задача добавлена");
        } catch (err) {
            toast(err.message);
        } finally {
            if (submit) submit.disabled = false;
        }
    });

    $("task-delete").addEventListener("click", async () => {
        const task = state.editingTask;
        if (!task) return;
        const ok = await confirmAction(`Удалить задачу «${task.title}»?`);
        if (!ok) return;
        try {
            await api(`/api/me/tasks/${task.id}`, { method: "DELETE" });
            closeSheet();
            await reload();
            toast("Задача удалена");
        } catch (err) {
            toast(err.message);
        }
    });

    function confirmAction(message) {
        if (tg?.showConfirm && tg.initData) {
            return new Promise((resolve) => tg.showConfirm(message, resolve));
        }
        return Promise.resolve(window.confirm(message));
    }

    // ---------- Событие календаря ----------

    function openEvent(e) {
        $("event-calendar").textContent = e.calendarName;
        $("event-calendar").style.setProperty("--pen", penOf(e.calendarId));
        $("event-title").textContent = e.title;
        const day = datePart(e.startAt);
        $("event-time").textContent = e.allDay
            ? `${humanDate(day)}, весь день`
            : `${humanDate(day)}, ${timePart(e.startAt)}–${timePart(e.endAt)}`;
        $("event-location").textContent = e.location ? `📍 ${e.location}` : "";
        $("event-desc").textContent = e.description || "";
        openSheet("event-sheet");
    }

    // ---------- Выбор месяца ----------

    async function renderMonth() {
        const cursor = state.monthCursor;
        $("month-sheet-title").textContent = `${MONTHS[cursor.getMonth()]} ${cursor.getFullYear()}`;
        const first = toStr(cursor);
        const gridStart = mondayOf(first);
        const gridEnd = addDays(gridStart, 41); // 6 недель

        const grid = $("month-grid");
        grid.replaceChildren(...WEEKDAYS.map((w) => el("span", "wd", w)));

        let busy = new Set();
        try {
            const [tasks, events] = await Promise.all([
                api(`/api/me/tasks?from=${gridStart}&to=${gridEnd}`),
                api(`/api/me/events?from=${gridStart}&to=${gridEnd}`),
            ]);
            busy = new Set([...tasks.filter((t) => t.dueAt).map((t) => datePart(t.dueAt)),
                ...events.map((e) => datePart(e.startAt))]);
        } catch (err) {
            // точки на днях — не главное, без них тоже можно выбрать день
        }

        const today = todayStr();
        for (let i = 0; i < 42; i++) {
            const day = addDays(gridStart, i);
            const date = fromStr(day);
            const button = el("button", "month-day", String(date.getDate()));
            button.type = "button";
            button.setAttribute("aria-label", humanDate(day));
            button.setAttribute("aria-selected", String(day === state.selected));
            if (date.getMonth() !== cursor.getMonth()) button.classList.add("is-other");
            if (day === today) button.classList.add("is-today");
            if (busy.has(day)) button.classList.add("has-items");
            button.addEventListener("click", () => {
                closeSheet();
                selectDate(day);
            });
            grid.append(button);
        }
    }

    $("month-title").addEventListener("click", () => {
        const d = fromStr(state.selected);
        state.monthCursor = new Date(d.getFullYear(), d.getMonth(), 1);
        openSheet("month-sheet");
        renderMonth();
    });
    $("month-prev").addEventListener("click", () => {
        state.monthCursor = new Date(state.monthCursor.getFullYear(), state.monthCursor.getMonth() - 1, 1);
        renderMonth();
    });
    $("month-next").addEventListener("click", () => {
        state.monthCursor = new Date(state.monthCursor.getFullYear(), state.monthCursor.getMonth() + 1, 1);
        renderMonth();
    });

    $("today-btn").addEventListener("click", () => selectDate(todayStr()));

    // листание недель свайпом
    let touchX = null;
    $("week").addEventListener("touchstart", (e) => { touchX = e.touches[0].clientX; }, { passive: true });
    $("week").addEventListener("touchend", (e) => {
        if (touchX == null) return;
        const dx = e.changedTouches[0].clientX - touchX;
        touchX = null;
        if (Math.abs(dx) > 50) {
            haptic("light");
            selectDate(addDays(state.selected, dx < 0 ? 7 : -7));
        }
    });

    // ---------- Экран «Календари» ----------

    async function loadCalendars() {
        state.calendars = await api("/api/me/calendars");
        renderCalendars();
    }

    function renderCalendars() {
        const list = $("calendar-list");
        list.replaceChildren();
        if (!state.calendars.length) {
            list.append(el("li", "empty-list",
                "Подпишись на календарь — например, на расписание занятий. Пары появятся на листе дня, а за 15 минут до начала придёт напоминание в Telegram."));
            return;
        }
        for (const c of state.calendars) {
            const li = el("li", "calendar-item");
            li.style.setProperty("--pen", penOf(c.id));
            li.append(el("span", "dot"), el("span", "calendar-name", c.name));

            const status = el("span", "calendar-status" + (c.lastError ? " is-error" : ""));
            status.textContent = c.lastError
                ? c.lastError
                : c.lastSyncedAt ? `Обновлён ${new Date(c.lastSyncedAt).toLocaleString("ru-RU", { day: "numeric", month: "long", hour: "2-digit", minute: "2-digit" })}` : "Ещё не обновлялся";
            li.append(status);

            const actions = el("div", "calendar-actions");

            const switchLabel = el("label", "switch");
            const toggle = document.createElement("input");
            toggle.type = "checkbox";
            toggle.checked = c.remindersEnabled;
            toggle.addEventListener("change", async () => {
                try {
                    await api(`/api/me/calendars/${c.id}`, {
                        method: "PATCH", body: JSON.stringify({ remindersEnabled: toggle.checked }),
                    });
                    c.remindersEnabled = toggle.checked;
                    toast(toggle.checked ? "Напоминания включены" : "Напоминания выключены");
                } catch (err) {
                    toggle.checked = !toggle.checked;
                    toast(err.message);
                }
            });
            switchLabel.append(toggle, document.createTextNode("Напоминания"));

            const sync = el("button", "small-btn", "Обновить");
            sync.type = "button";
            sync.addEventListener("click", async () => {
                sync.disabled = true;
                try {
                    const result = await api(`/api/me/calendars/${c.id}/sync`, { method: "POST" });
                    toast(result.error ? result.error
                        : result.notModified ? "Изменений нет" : `Обновлено: ${result.added + result.updated} событий`);
                    await loadCalendars();
                    await reload();
                } catch (err) {
                    toast(err.message);
                } finally {
                    sync.disabled = false;
                }
            });

            const remove = el("button", "small-btn is-danger", "Отписаться");
            remove.type = "button";
            remove.addEventListener("click", async () => {
                if (!(await confirmAction(`Отписаться от «${c.name}»? События этого календаря пропадут из планера.`))) return;
                try {
                    await api(`/api/me/calendars/${c.id}`, { method: "DELETE" });
                    toast(`Отписался от «${c.name}»`);
                    await loadCalendars();
                    await reload();
                } catch (err) {
                    toast(err.message);
                }
            });

            actions.append(switchLabel, sync, remove);
            li.append(actions);
            list.append(li);
        }
    }

    $("subscribe-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        const input = $("subscribe-url");
        const button = event.submitter;
        button.disabled = true;
        button.textContent = "Загружаю…";
        try {
            const calendar = await api("/api/me/calendars", {
                method: "POST", body: JSON.stringify({ url: input.value.trim() }),
            });
            input.value = "";
            tg?.HapticFeedback?.notificationOccurred("success");
            toast(`Подписался на «${calendar.name}»`);
            await loadCalendars();
            await reload();
        } catch (err) {
            tg?.HapticFeedback?.notificationOccurred("error");
            toast(err.message);
        } finally {
            button.disabled = false;
            button.textContent = "Подписаться";
        }
    });

    // ---------- Вкладки ----------

    document.querySelectorAll(".tab").forEach((tab) => {
        tab.addEventListener("click", () => {
            const screen = tab.dataset.screen;
            document.querySelectorAll(".tab").forEach((t) => t.removeAttribute("aria-current"));
            tab.setAttribute("aria-current", "page");
            $("screen-plan").hidden = screen !== "plan";
            $("screen-calendars").hidden = screen !== "calendars";
            $("add-btn").hidden = screen !== "plan";
            haptic("light");
        });
    });

    $("add-btn").addEventListener("click", () => { haptic("light"); openTask(null); });

    // ---------- Запуск ----------

    async function start() {
        if (tg) {
            tg.ready();
            tg.expand();
        }
        try {
            const me = await api("/api/me");
            // часовой пояс берём с устройства — тогда время пар и напоминаний совпадёт с часами телефона
            const zone = Intl.DateTimeFormat().resolvedOptions().timeZone;
            if (zone && zone !== me.timezone) {
                await api("/api/me", { method: "PATCH", body: JSON.stringify({ timezone: zone }) });
            }
            await Promise.all([selectDate(todayStr()), loadCalendars()]);
        } catch (err) {
            if (err.status === 401) {
                showFatal("Открой планер из Telegram: кнопка «Планер» в чате с ботом.");
            } else {
                showFatal(`Не удалось загрузить планер: ${err.message}`);
            }
        }
    }

    // раз в минуту двигаем линию «сейчас»
    setInterval(() => { if (state.weekStart && !document.hidden) renderDay(); }, 60_000);

    start();
})();
