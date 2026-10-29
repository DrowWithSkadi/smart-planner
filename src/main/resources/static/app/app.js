/*
 * Smart Planner — Mini App.
 * Обычный JavaScript без сборки: открой файл и читай сверху вниз.
 *
 * Как устроено:
 *  1. Telegram передаёт в Mini App подписанные данные пользователя (tg.initData).
 *     Мы отправляем их в заголовке Authorization, сервер проверяет подпись и понимает, кто мы.
 *  2. Сервер отдаёт время уже в часовом поясе пользователя ("2026-10-30T10:00:00"),
 *     поэтому здесь даты — простые строки, часовые пояса не пересчитываем.
 *  3. «События» в интерфейсе — это задачи на сервере (/api/me/tasks): у них есть начало,
 *     конец, картинка и отметка «выполнено». Пары из подписанных календарей — /api/me/events.
 */
(() => {
    "use strict";

    const tg = window.Telegram?.WebApp;
    const PENS = ["#7aa7ff", "#6ee7a8", "#c69cff", "#ffc46b", "#ff8fb4"]; // цвета календарей
    const IMAGE_MAX_SIDE = 1600;   // картинку уменьшаем до 1600px по большей стороне
    const IMAGE_QUALITY = 0.85;

    /*
     * Цвет месяца. Зима — лёд, весна — розовый, лето — зелень, осень — от янтаря к красному.
     * accent — кнопки, точка, «сейчас»; glow — свечение фона.
     */
    const SEASONS = [
        { accent: "#8cc8ff", glow: "#3f7fd6" }, // январь
        { accent: "#b3c7ff", glow: "#5a6fd6" }, // февраль
        { accent: "#ffa3d1", glow: "#d6458f" }, // март
        { accent: "#ff86bd", glow: "#e0408a" }, // апрель
        { accent: "#f7a8e6", glow: "#b84fc4" }, // май
        { accent: "#7fe8a6", glow: "#1f9e5a" }, // июнь
        { accent: "#4fdd85", glow: "#178a46" }, // июль
        { accent: "#b9e36a", glow: "#6c9a1e" }, // август
        { accent: "#ffb74d", glow: "#c96a12" }, // сентябрь
        { accent: "#ff7043", glow: "#c2361b" }, // октябрь
        { accent: "#ff5c61", glow: "#a3172a" }, // ноябрь
        { accent: "#e6f1ff", glow: "#5f8fc9" }, // декабрь
    ];

    const MONTHS = ["январь", "февраль", "март", "апрель", "май", "июнь", "июль", "август",
        "сентябрь", "октябрь", "ноябрь", "декабрь"];
    const MONTHS_GEN = ["января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа",
        "сентября", "октября", "ноября", "декабря"];
    const WD_SHORT = ["пн", "вт", "ср", "чт", "пт", "сб", "вс"];
    const WD_HERO = ["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"];

    const $ = (id) => document.getElementById(id);
    const $$ = (selector) => Array.from(document.querySelectorAll(selector));

    // ---------- Состояние ----------

    const state = {
        selected: todayStr(),   // выбранный день "YYYY-MM-DD"
        weekStart: null,        // понедельник показанной недели
        tasks: [],              // события (задачи) недели
        events: [],             // события календарей недели
        inbox: [],              // события без даты
        calendars: [],
        monthCursor: null,      // первый день месяца, открытого в сетке
        monthBusy: new Set(),   // дни месяца, где что-то есть
        screen: "day",
        editing: null,          // какое событие редактируем
        image: { file: null, removed: false }, // новая картинка или удаление старой
    };

    // ---------- Даты (строки YYYY-MM-DD) ----------

    function pad(n) { return String(n).padStart(2, "0"); }
    function toStr(d) { return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`; }
    function fromStr(s) { const [y, m, d] = s.split("-").map(Number); return new Date(y, m - 1, d); }
    function todayStr() { return toStr(new Date()); }
    function addDays(s, n) { const d = fromStr(s); d.setDate(d.getDate() + n); return toStr(d); }
    function mondayOf(s) { const d = fromStr(s); d.setDate(d.getDate() - ((d.getDay() + 6) % 7)); return toStr(d); }
    function weekdayIndex(s) { return (fromStr(s).getDay() + 6) % 7; }
    function datePart(dt) { return dt.slice(0, 10); }
    function timePart(dt) { return dt.slice(11, 16); }
    function minutesOf(dt) { return Number(dt.slice(11, 13)) * 60 + Number(dt.slice(14, 16)); }
    function humanDate(s) { const d = fromStr(s); return `${d.getDate()} ${MONTHS_GEN[d.getMonth()]}`; }
    function nowMinutes() { const n = new Date(); return n.getHours() * 60 + n.getMinutes(); }
    function nowTime() { const n = new Date(); return `${pad(n.getHours())}:${pad(n.getMinutes())}`; }
    function addMinutesToTime(time, minutes) {
        const total = Math.min(23 * 60 + 59, Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5)) + minutes);
        return `${pad(Math.floor(total / 60))}:${pad(total % 60)}`;
    }

    // ---------- Запросы к серверу ----------

    async function api(path, options = {}) {
        const headers = { ...(options.headers || {}) };
        if (!(options.body instanceof FormData)) headers["Content-Type"] = "application/json";
        if (tg?.initData) headers.Authorization = "tma " + tg.initData;
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

    // ---------- Помощники интерфейса ----------

    function el(tag, className, text) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        if (text != null) node.textContent = text; // textContent — безопасно, HTML не исполняется
        return node;
    }

    function svg(path) {
        const ns = "http://www.w3.org/2000/svg";
        const node = document.createElementNS(ns, "svg");
        node.setAttribute("viewBox", "0 0 24 24");
        node.setAttribute("aria-hidden", "true");
        const p = document.createElementNS(ns, "path");
        p.setAttribute("d", path);
        node.append(p);
        return node;
    }

    let toastTimer;
    function toast(message) {
        const node = $("toast");
        node.textContent = message;
        node.hidden = false;
        node.style.animation = "none";
        void node.offsetWidth; // перезапуск анимации
        node.style.animation = "";
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => { node.hidden = true; }, 2600);
    }

    function haptic(type = "light") { tg?.HapticFeedback?.impactOccurred(type); }
    function notify(type) { tg?.HapticFeedback?.notificationOccurred(type); }

    function penOf(calendarId) { return PENS[(calendarId - 1) % PENS.length]; }

    /** Перезапускает CSS-анимацию появления. */
    function animate(node, className) {
        node.classList.remove("enter-left", "enter-right", "enter-fade", "slide-left", "slide-right");
        if (!className) return;
        void node.offsetWidth;
        node.classList.add(className);
    }

    /** Меняет цвет интерфейса под месяц (переход плавный — см. @property в CSS). */
    function applySeason(monthIndex) {
        const season = SEASONS[monthIndex];
        const root = document.documentElement.style;
        root.setProperty("--accent", season.accent);
        root.setProperty("--glow", season.glow);
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

    async function selectDate(day, { animateDay = true } = {}) {
        const previous = state.selected;
        const direction = day > previous ? "enter-left" : day < previous ? "enter-right" : "enter-fade";
        const newWeek = mondayOf(day);
        const weekChanged = newWeek !== state.weekStart;
        state.selected = day;
        applySeason(fromStr(day).getMonth());
        renderHero();

        if (weekChanged) {
            const hadWeek = Boolean(state.weekStart);
            const weekDirection = hadWeek && newWeek < state.weekStart ? "slide-right" : "slide-left";
            state.weekStart = newWeek;
            renderWeek(hadWeek ? weekDirection : null);
            await loadWeek();
        }
        renderWeek(null);
        renderDay(animateDay ? direction : null);
        syncMonthCursor();
    }

    async function reload() {
        await loadWeek();
        renderWeek(null);
        renderDay(null);
        await renderMonths();
    }

    // ---------- Заголовок ----------

    function renderHero() {
        const d = fromStr(state.selected);
        $("hero-weekday").textContent = WD_HERO[weekdayIndex(state.selected)];
        $("hero-month").textContent = state.selected === todayStr() ? `сегодня, ${MONTHS[d.getMonth()]}` : MONTHS[d.getMonth()];
        $("hero-num").textContent = String(d.getDate());
    }

    // ---------- Неделя ----------

    function itemsOfDay(day) {
        return {
            tasks: state.tasks.filter((t) => t.dueAt && datePart(t.dueAt) === day),
            events: state.events.filter((e) => {
                const start = datePart(e.startAt);
                // у событий на весь день конец «исключительный» — это начало следующего дня
                const end = e.allDay ? addDays(datePart(e.endAt), -1) : datePart(e.endAt);
                return start <= day && day <= end;
            }),
        };
    }

    function renderWeek(slideClass) {
        const week = $("week");
        const indicator = $("week-indicator");
        week.querySelectorAll(".day").forEach((n) => n.remove());
        const today = todayStr();
        for (let i = 0; i < 7; i++) {
            const day = addDays(state.weekStart, i);
            const { tasks, events } = itemsOfDay(day);
            const button = el("button", "day");
            button.type = "button";
            button.setAttribute("aria-selected", String(day === state.selected));
            button.setAttribute("aria-label", humanDate(day));
            if (day === today) button.classList.add("is-today");
            if (tasks.length + events.length) button.classList.add("has-items");
            button.append(el("span", "day-num", String(fromStr(day).getDate())), el("span", "day-name", WD_SHORT[i]), el("span", "day-tick"));
            button.addEventListener("click", () => { haptic("light"); selectDate(day); });
            week.append(button);
        }
        const index = weekdayIndex(state.selected);
        indicator.classList.toggle("no-anim", Boolean(slideClass));
        indicator.style.transform = `translateX(${index * 100}%)`;
        if (slideClass) animate(week, slideClass);
    }

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

    // ---------- Лента дня ----------

    function renderDay(enterClass) {
        const day = state.selected;
        const { tasks, events } = itemsOfDay(day);

        // события на весь день — «таблетки» над лентой
        const allday = $("allday");
        allday.replaceChildren();
        for (const e of events.filter((x) => x.allDay)) {
            const chip = el("button", "pill");
            chip.type = "button";
            chip.style.setProperty("--pen", penOf(e.calendarId));
            chip.append(el("span", "dot"), document.createTextNode(e.title));
            chip.addEventListener("click", () => openEvent(e));
            allday.append(chip);
        }

        // всё, у чего есть время, — по порядку
        const items = [
            ...events.filter((x) => !x.allDay).map((e) => ({
                type: "event", data: e,
                start: datePart(e.startAt) < day ? 0 : minutesOf(e.startAt),
                end: datePart(e.endAt) > day ? 24 * 60 : minutesOf(e.endAt),
                label: datePart(e.startAt) < day ? "00:00" : timePart(e.startAt),
            })),
            ...tasks.map((t) => ({
                type: "task", data: t,
                start: minutesOf(t.dueAt),
                end: t.endAt && datePart(t.endAt) === day ? minutesOf(t.endAt) : minutesOf(t.dueAt),
                label: timePart(t.dueAt),
            })),
        ].sort((a, b) => a.start - b.start || a.end - b.end);

        const agenda = $("agenda");
        agenda.replaceChildren();

        const isToday = day === todayStr();
        const isPastDay = day < todayStr();
        const now = nowMinutes();
        let nowShown = !isToday;

        for (const item of items) {
            if (!nowShown && item.start > now) {
                agenda.append(nowMarker());
                nowShown = true;
            }
            const slot = el("div", "slot");
            slot.append(el("span", "slot-time", item.label));
            const card = item.type === "event" ? eventCard(item.data, day) : taskCard(item.data);
            if (isPastDay || (isToday && item.end < now)) card.classList.add("is-past");
            slot.append(card);
            agenda.append(slot);
        }
        if (!nowShown && items.length) agenda.append(nowMarker());

        if (!items.length && !events.some((e) => e.allDay)) {
            const empty = el("div", "empty");
            empty.append(el("strong", null, isPastDay ? "В этот день ничего не было" : "День свободен"),
                el("span", null, "Добавь событие — напомню о нём в Telegram."));
            const add = el("button", "accent-btn", "Добавить событие");
            add.type = "button";
            add.addEventListener("click", () => openTask(null));
            empty.append(add);
            agenda.append(empty);
        }

        if (state.inbox.length) {
            agenda.append(el("h3", "group-title", "Без даты"));
            for (const t of state.inbox) {
                const slot = el("div", "slot");
                slot.append(el("span", "slot-time", ""), taskCard(t));
                agenda.append(slot);
            }
        }

        animate(agenda, enterClass);
    }

    function nowMarker() {
        const row = el("div", "now");
        row.setAttribute("aria-label", `Сейчас ${nowTime()}`);
        row.append(el("span", "now-pill", nowTime()), el("span", "now-line"));
        return row;
    }

    function timeRange(start, end) {
        return end && end !== start ? `${start} — ${end}` : start;
    }

    function taskCard(t) {
        const card = el("article", "card" + (t.done ? " is-done" : ""));
        const head = el("div", "card-head");
        const main = el("button", "card-main");
        main.type = "button";
        main.append(el("span", "card-title", t.title));
        if (t.dueAt) {
            const end = t.endAt ? (datePart(t.endAt) === datePart(t.dueAt) ? timePart(t.endAt) : `${humanDate(datePart(t.endAt))}, ${timePart(t.endAt)}`) : null;
            main.append(el("span", "card-time", timeRange(timePart(t.dueAt), end)));
        }
        main.addEventListener("click", () => openTask(t));
        head.append(main, checkbox(t));
        card.append(head);
        if (t.description) card.append(el("p", "card-desc", t.description));
        if (t.imageUrl) {
            const img = el("img", "card-image");
            img.src = t.imageUrl;
            img.alt = "";
            img.loading = "lazy";
            img.addEventListener("click", () => openTask(t));
            card.append(img);
        }
        return card;
    }

    function eventCard(e, day) {
        const card = el("button", "card is-event");
        card.type = "button";
        card.style.setProperty("--pen", penOf(e.calendarId));
        const source = el("span", "card-source", e.calendarName);
        card.append(source, el("span", "card-title", e.title));
        const start = datePart(e.startAt) < day ? "00:00" : timePart(e.startAt);
        const end = datePart(e.endAt) > day ? "24:00" : timePart(e.endAt);
        card.append(el("span", "card-time", timeRange(start, end)));
        if (e.location) {
            const pill = el("span", "pill");
            pill.append(svg("M12 21s-6-5.3-6-11a6 6 0 1112 0c0 5.7-6 11-6 11z"), document.createTextNode(e.location));
            pill.querySelector("svg").style.cssText = "width:14px;height:14px";
            card.append(pill);
        }
        card.addEventListener("click", () => openEvent(e));
        return card;
    }

    function checkbox(t) {
        const check = el("button", "check");
        check.type = "button";
        check.setAttribute("role", "checkbox");
        check.setAttribute("aria-checked", String(t.done));
        check.setAttribute("aria-label", t.done ? `Снять отметку: ${t.title}` : `Отметить выполненным: ${t.title}`);
        check.append(svg("M5 12.5l4.5 4.5L19 7.5"));
        check.addEventListener("click", async (event) => {
            event.stopPropagation();
            const done = !t.done;
            // сначала меняем вид, потом спрашиваем сервер — так отклик мгновенный
            t.done = done;
            check.setAttribute("aria-checked", String(done));
            check.closest(".card")?.classList.toggle("is-done", done);
            if (done) notify("success"); else haptic("light");
            try {
                await api(`/api/me/tasks/${t.id}/done?value=${done}`, { method: "PATCH" });
                if (done && !t.dueAt) {
                    setTimeout(() => { state.inbox = state.inbox.filter((x) => x.id !== t.id); renderDay(null); }, 600);
                }
            } catch (err) {
                t.done = !done;
                check.setAttribute("aria-checked", String(!done));
                check.closest(".card")?.classList.toggle("is-done", !done);
                toast(err.message);
            }
        });
        return check;
    }

    // ---------- Месяц ----------

    function syncMonthCursor() {
        const d = fromStr(state.selected);
        const cursor = new Date(d.getFullYear(), d.getMonth(), 1);
        if (!state.monthCursor || cursor.getTime() !== state.monthCursor.getTime()) {
            state.monthCursor = cursor;
            renderMonths();
        } else {
            drawMonths();
        }
    }

    async function renderMonths() {
        if (!state.monthCursor) return;
        const cursor = state.monthCursor;
        const gridStart = mondayOf(toStr(cursor));
        const gridEnd = addDays(gridStart, 41);
        drawMonths(); // сразу рисуем сетку, точки появятся после загрузки
        try {
            const [tasks, events] = await Promise.all([
                api(`/api/me/tasks?from=${gridStart}&to=${gridEnd}`),
                api(`/api/me/events?from=${gridStart}&to=${gridEnd}`),
            ]);
            if (cursor !== state.monthCursor) return; // пока грузили, месяц переключили
            state.monthBusy = new Set([...tasks.filter((t) => t.dueAt).map((t) => datePart(t.dueAt)),
                ...events.map((e) => datePart(e.startAt))]);
            drawMonths();
        } catch (err) {
            // точки на днях — не главное, без них тоже можно выбрать день
        }
    }

    function drawMonths() {
        const cursor = state.monthCursor;
        if (!cursor) return;
        const gridStart = mondayOf(toStr(cursor));
        const today = todayStr();
        $$("[data-month-title]").forEach((n) => { n.textContent = `${MONTHS[cursor.getMonth()]} ${cursor.getFullYear()}`; });
        for (const grid of $$("[data-month-grid]")) {
            grid.replaceChildren(...WD_SHORT.map((w) => el("span", "wd", w)));
            for (let i = 0; i < 42; i++) {
                const day = addDays(gridStart, i);
                const date = fromStr(day);
                const button = el("button", "month-day", String(date.getDate()));
                button.type = "button";
                button.setAttribute("aria-label", humanDate(day));
                button.setAttribute("aria-selected", String(day === state.selected));
                if (date.getMonth() !== cursor.getMonth()) button.classList.add("is-other");
                if (day === today) button.classList.add("is-today");
                if (state.monthBusy.has(day)) button.classList.add("has-items");
                button.addEventListener("click", () => {
                    haptic("light");
                    if (state.screen === "month") showScreen("day");
                    selectDate(day);
                });
                grid.append(button);
            }
        }
    }

    $$("[data-month-step]").forEach((button) => button.addEventListener("click", () => {
        const step = Number(button.dataset.monthStep);
        state.monthCursor = new Date(state.monthCursor.getFullYear(), state.monthCursor.getMonth() + step, 1);
        state.monthBusy = new Set();
        applySeason(state.monthCursor.getMonth()); // цвет «примеряет» листаемый месяц
        haptic("light");
        renderMonths();
    }));

    $("hero-date").addEventListener("click", () => {
        if (window.matchMedia("(min-width: 980px)").matches) {
            selectDate(todayStr());
        } else {
            showScreen("month");
        }
    });

    // ---------- Экраны и вкладки ----------

    function showScreen(screen) {
        state.screen = screen;
        $$(".tab").forEach((t) => {
            if (t.dataset.screen === screen) t.setAttribute("aria-current", "page");
            else t.removeAttribute("aria-current");
        });
        $("screen-day").hidden = screen !== "day";
        $("screen-month").hidden = screen !== "month";
        $("screen-calendars").hidden = screen !== "calendars";
        const current = $(`screen-${screen}`);
        animate(current, "enter-fade");
        if (screen === "day") {
            applySeason(fromStr(state.selected).getMonth());
            syncMonthCursor();
        }
        moveTabIndicator();
        window.scrollTo({ top: 0, behavior: "smooth" });
    }

    function moveTabIndicator() {
        const active = document.querySelector(`.tab[data-screen="${state.screen}"]`);
        const indicator = $("tab-indicator");
        if (!active || active.offsetParent === null) {
            indicator.style.width = "0";
            return;
        }
        indicator.style.width = `${active.offsetWidth}px`;
        indicator.style.transform = `translateX(${active.offsetLeft}px)`;
    }

    $$(".tab").forEach((tab) => tab.addEventListener("click", () => {
        if (tab.dataset.screen === state.screen) return;
        haptic("light");
        showScreen(tab.dataset.screen);
    }));
    $$("[data-open-screen]").forEach((b) => b.addEventListener("click", () => showScreen(b.dataset.openScreen)));
    window.addEventListener("resize", moveTabIndicator);

    // ---------- Нижние окна ----------

    const openSheets = [];

    function openSheet(id) {
        const sheet = $(id);
        sheet.hidden = false;
        requestAnimationFrame(() => requestAnimationFrame(() => sheet.classList.add("is-open")));
        openSheets.push(sheet);
        tg?.BackButton?.show();
    }

    function closeSheet() {
        const sheet = openSheets.pop();
        if (!sheet) return;
        sheet.classList.remove("is-open");
        const body = sheet.querySelector(".sheet-body");
        body.style.transform = "";
        setTimeout(() => { if (!sheet.classList.contains("is-open")) sheet.hidden = true; }, 460);
        if (!openSheets.length) tg?.BackButton?.hide();
    }

    $$("[data-close]").forEach((node) => node.addEventListener("click", closeSheet));
    document.addEventListener("keydown", (e) => { if (e.key === "Escape") closeSheet(); });
    tg?.BackButton?.onClick(closeSheet);

    // окно можно смахнуть вниз за «ручку» или заголовок
    $$(".sheet").forEach((sheet) => {
        const body = sheet.querySelector(".sheet-body");
        let startY = null;
        let dy = 0;
        sheet.querySelectorAll("[data-drag]").forEach((handle) => {
            handle.addEventListener("pointerdown", (e) => {
                if (e.target.closest("button")) return;
                startY = e.clientY;
                dy = 0;
                sheet.classList.add("is-dragging");
                handle.setPointerCapture(e.pointerId);
            });
            handle.addEventListener("pointermove", (e) => {
                if (startY == null) return;
                dy = Math.max(0, e.clientY - startY);
                body.style.transform = `translateY(${dy}px)`;
            });
            const end = () => {
                if (startY == null) return;
                startY = null;
                sheet.classList.remove("is-dragging");
                if (dy > 110) closeSheet();
                else body.style.transform = "";
            };
            handle.addEventListener("pointerup", end);
            handle.addEventListener("pointercancel", end);
        });
    });

    // ---------- Событие: создание и редактирование ----------

    function openTask(task) {
        state.editing = task || null;
        state.image = { file: null, removed: false };
        $("task-sheet-title").textContent = task ? "Событие" : "Новое событие";
        $("task-title").value = task?.title || "";
        $("task-desc").value = task?.description || "";
        $("task-date").value = task ? (task.dueAt ? datePart(task.dueAt) : "") : state.selected;
        $("task-start").value = task?.dueAt ? timePart(task.dueAt) : "";
        $("task-end").value = task?.endAt ? timePart(task.endAt) : "";
        $("task-remind").value = String(task?.remindBeforeMinutes ?? 15);
        $("task-delete").hidden = !task;
        $("task-image").value = "";
        showImagePreview(task?.imageUrl || null);
        openSheet("task-sheet");
        if (!task) setTimeout(() => $("task-title").focus(), 320);
    }

    function showImagePreview(src) {
        $("image-preview").hidden = !src;
        $("image-pick").hidden = Boolean(src);
        if (src) $("image-preview-img").src = src;
        else $("image-preview-img").removeAttribute("src");
    }

    $("task-image").addEventListener("change", async () => {
        const file = $("task-image").files[0];
        if (!file) return;
        try {
            const blob = await shrinkImage(file);
            state.image = { file: blob, removed: false };
            showImagePreview(URL.createObjectURL(blob));
        } catch (err) {
            toast("Не получилось открыть картинку");
        }
    });

    $("image-remove").addEventListener("click", () => {
        state.image = { file: null, removed: true };
        $("task-image").value = "";
        showImagePreview(null);
    });

    /** Уменьшает фото до 1600px и сжимает в JPEG — так оно грузится быстро даже по мобильной сети. */
    function shrinkImage(file) {
        return new Promise((resolve, reject) => {
            const img = new Image();
            const url = URL.createObjectURL(file);
            img.onload = () => {
                URL.revokeObjectURL(url);
                const scale = Math.min(1, IMAGE_MAX_SIDE / Math.max(img.width, img.height));
                const canvas = document.createElement("canvas");
                canvas.width = Math.round(img.width * scale);
                canvas.height = Math.round(img.height * scale);
                canvas.getContext("2d").drawImage(img, 0, 0, canvas.width, canvas.height);
                canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error("toBlob"))), "image/jpeg", IMAGE_QUALITY);
            };
            img.onerror = () => { URL.revokeObjectURL(url); reject(new Error("image")); };
            img.src = url;
        });
    }

    // быстрые кнопки длительности: конец = начало + N минут
    $$("[data-duration]").forEach((button) => button.addEventListener("click", () => {
        const start = $("task-start").value || addMinutesToTime(nowTime(), 30 - (nowMinutes() % 30));
        $("task-start").value = start;
        $("task-end").value = addMinutesToTime(start, Number(button.dataset.duration));
        haptic("light");
    }));

    $("task-start").addEventListener("change", () => {
        const start = $("task-start").value;
        const end = $("task-end").value;
        if (start && end && end < start) $("task-end").value = addMinutesToTime(start, 60);
    });

    $("task-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        const title = $("task-title").value.trim();
        if (!title) return;
        const date = $("task-date").value;
        const start = $("task-start").value || (date ? "09:00" : ""); // день без времени — значит утром
        const end = $("task-end").value;
        if (end && !date) { toast("Выбери день, чтобы указать время"); return; }
        if (end && end < start) { toast("Конец не может быть раньше начала"); return; }

        const body = {
            title,
            description: $("task-desc").value.trim() || null,
            dueAt: date ? `${date}T${start}` : null,
            endAt: date && end ? `${date}T${end}` : null,
            remindBeforeMinutes: Number($("task-remind").value),
        };
        const save = $("task-save");
        save.disabled = true;
        save.textContent = "Сохраняю…";
        try {
            const editing = state.editing;
            let task = editing
                ? await api(`/api/me/tasks/${editing.id}`, { method: "PUT", body: JSON.stringify(body) })
                : await api("/api/me/tasks", { method: "POST", body: JSON.stringify(body) });

            if (state.image.file) {
                const form = new FormData();
                form.append("file", state.image.file, "image.jpg");
                task = await api(`/api/me/tasks/${task.id}/image`, { method: "POST", body: form });
            } else if (state.image.removed && editing?.imageUrl) {
                task = await api(`/api/me/tasks/${task.id}/image`, { method: "DELETE" });
            }

            notify("success");
            closeSheet();
            if (date && date !== state.selected) await selectDate(date);
            await reload();
            toast(editing ? "Событие сохранено" : "Событие добавлено");
        } catch (err) {
            notify("error");
            toast(err.message);
        } finally {
            save.disabled = false;
            save.textContent = "Сохранить";
        }
    });

    $("task-delete").addEventListener("click", async () => {
        const task = state.editing;
        if (!task || !(await confirmAction(`Удалить «${task.title}»?`))) return;
        try {
            await api(`/api/me/tasks/${task.id}`, { method: "DELETE" });
            closeSheet();
            await reload();
            toast("Событие удалено");
        } catch (err) {
            toast(err.message);
        }
    });

    function confirmAction(message) {
        if (tg?.showConfirm && tg.initData) return new Promise((resolve) => tg.showConfirm(message, resolve));
        return Promise.resolve(window.confirm(message));
    }

    $("add-btn").addEventListener("click", () => {
        haptic("medium");
        if (state.screen !== "day") showScreen("day");
        openTask(null);
    });

    // ---------- Событие из календаря ----------

    function openEvent(e) {
        const sheet = $("event-sheet");
        sheet.style.setProperty("--pen", penOf(e.calendarId));
        $("event-calendar").textContent = e.calendarName;
        $("event-title").textContent = e.title;
        const day = datePart(e.startAt);
        $("event-time").textContent = e.allDay
            ? `${humanDate(day)} · весь день`
            : `${humanDate(day)} · ${timePart(e.startAt)} — ${timePart(e.endAt)}`;
        $("event-location").textContent = e.location || "";
        $("event-desc").textContent = e.description || "";
        openSheet("event-sheet");
    }

    // ---------- Календари ----------

    async function loadCalendars() {
        state.calendars = await api("/api/me/calendars");
        renderCalendars();
    }

    function renderCalendars() {
        const side = $("side-calendar-list");
        side.replaceChildren();
        for (const c of state.calendars) {
            const li = el("li");
            li.style.setProperty("--pen", penOf(c.id));
            li.append(el("span", "dot"), document.createTextNode(c.name));
            side.append(li);
        }
        if (!state.calendars.length) side.append(el("li", null, "Пока нет подписок"));

        const list = $("calendar-list");
        list.replaceChildren();
        for (const c of state.calendars) {
            const li = el("li", "glass calendar-item");
            li.style.borderRadius = "22px";
            li.style.setProperty("--pen", penOf(c.id));

            const row = el("div", "calendar-row");
            row.append(el("span", "dot"), el("span", "calendar-name", c.name));
            li.append(row);

            const status = el("p", "calendar-status" + (c.lastError ? " is-error" : ""));
            status.textContent = c.lastError
                ? c.lastError
                : c.lastSyncedAt
                    ? `Обновлён ${new Date(c.lastSyncedAt).toLocaleString("ru-RU", { day: "numeric", month: "long", hour: "2-digit", minute: "2-digit" })}`
                    : "Ещё не обновлялся";
            li.append(status);

            const actions = el("div", "calendar-actions");
            const label = el("label", "switch");
            const toggle = document.createElement("input");
            toggle.type = "checkbox";
            toggle.checked = c.remindersEnabled;
            toggle.addEventListener("change", async () => {
                haptic("light");
                try {
                    await api(`/api/me/calendars/${c.id}`, { method: "PATCH", body: JSON.stringify({ remindersEnabled: toggle.checked }) });
                    c.remindersEnabled = toggle.checked;
                    toast(toggle.checked ? "Напоминания включены" : "Напоминания выключены");
                } catch (err) {
                    toggle.checked = !toggle.checked;
                    toast(err.message);
                }
            });
            label.append(toggle, el("span", "switch-track"), document.createTextNode("Напоминания"));

            const sync = el("button", "small-btn", "Обновить");
            sync.type = "button";
            sync.addEventListener("click", async () => {
                sync.disabled = true;
                sync.textContent = "Обновляю…";
                try {
                    const result = await api(`/api/me/calendars/${c.id}/sync`, { method: "POST" });
                    toast(result.error ? result.error : result.notModified ? "Изменений нет" : `Обновлено событий: ${result.added + result.updated}`);
                    await loadCalendars();
                    await reload();
                } catch (err) {
                    toast(err.message);
                } finally {
                    sync.disabled = false;
                    sync.textContent = "Обновить";
                }
            });

            const remove = el("button", "small-btn is-danger", "Отписаться");
            remove.type = "button";
            remove.addEventListener("click", async () => {
                if (!(await confirmAction(`Отписаться от «${c.name}»? Его события пропадут из планера.`))) return;
                try {
                    await api(`/api/me/calendars/${c.id}`, { method: "DELETE" });
                    toast(`Отписался от «${c.name}»`);
                    await loadCalendars();
                    await reload();
                } catch (err) {
                    toast(err.message);
                }
            });

            actions.append(label, sync, remove);
            li.append(actions);
            list.append(li);
        }
    }

    $("subscribe-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        const input = $("subscribe-url");
        const button = event.submitter;
        button.disabled = true;
        button.textContent = "Загружаю календарь…";
        try {
            const calendar = await api("/api/me/calendars", { method: "POST", body: JSON.stringify({ url: input.value.trim() }) });
            input.value = "";
            notify("success");
            toast(`Подписался на «${calendar.name}»`);
            await loadCalendars();
            await reload();
        } catch (err) {
            notify("error");
            toast(err.message);
        } finally {
            button.disabled = false;
            button.textContent = "Подписаться";
        }
    });

    // ---------- Запуск ----------

    async function start() {
        if (tg) {
            tg.ready();
            tg.expand();
            try {
                tg.setHeaderColor("#09090b");
                tg.setBackgroundColor("#09090b");
                tg.setBottomBarColor?.("#09090b");
            } catch (e) { /* старые версии Telegram не умеют менять цвета */ }
        }
        applySeason(new Date().getMonth());
        renderHero();
        try {
            const me = await api("/api/me");
            // часовой пояс берём с устройства — тогда время пар и напоминаний совпадёт с часами телефона
            const zone = Intl.DateTimeFormat().resolvedOptions().timeZone;
            if (zone && zone !== me.timezone) {
                await api("/api/me", { method: "PATCH", body: JSON.stringify({ timezone: zone }) });
            }
            await Promise.all([selectDate(todayStr(), { animateDay: false }), loadCalendars()]);
            moveTabIndicator();
        } catch (err) {
            showFatal(err.status === 401
                ? "Открой планер из Telegram: кнопка «Планер» в чате с ботом."
                : `Не удалось загрузить планер: ${err.message}`);
        }
    }

    // раз в минуту двигаем линию «сейчас»
    setInterval(() => {
        if (state.weekStart && !document.hidden && state.screen === "day" && !openSheets.length) renderDay(null);
    }, 60_000);

    start();
})();
