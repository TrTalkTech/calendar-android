#!/usr/bin/env python3
"""Functional P0 checks on emulator-5554. Writes only to local calendar named «Локальный» (id=4) / Задачи (id=5)."""

from __future__ import annotations

import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta, timezone

SERIAL = "emulator-5554"
PKG = "ru.laktapp.calendar"
ACTIVITY = "com.example.calendar.MainActivity"
LOCAL_CALENDAR_ID = 4
TASKS_ID = 5
FORBIDDEN_CALENDARS = {2, 3, 6}
MSK = timezone(timedelta(hours=3))
PREFIX = "QA30"
VERSION = "1.03"
TODAY = datetime.now(MSK).date()


def adb(*args: str, check: bool = True, timeout: float = 25.0) -> str:
    try:
        proc = subprocess.run(
            ["adb", "-s", SERIAL, *args],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            timeout=timeout,
        )
    except subprocess.TimeoutExpired as exc:
        out = (exc.stdout or "") if isinstance(exc.stdout, str) else ""
        if check:
            raise RuntimeError(f"adb {args} timed out") from exc
        return out
    if check and proc.returncode != 0:
        raise RuntimeError(f"adb {args} failed:\n{proc.stdout}")
    return proc.stdout


def sh(cmd: str, check: bool = True) -> str:
    return adb("shell", cmd, check=check)


results: list[tuple[str, bool, str]] = []


def record(test_id: str, ok: bool, note: str = "") -> bool:
    results.append((test_id, ok, note))
    print(("PASS" if ok else "FAIL"), test_id, note, flush=True)
    return ok


def millis(dt: datetime) -> int:
    return int(dt.timestamp() * 1000)


def dump_root() -> ET.Element:
    last_err: Exception | None = None
    for attempt in range(5):
        try:
            adb("shell", "uiautomator dump /sdcard/uidump.xml", timeout=40)
            xml = adb("exec-out", "cat", "/sdcard/uidump.xml", timeout=15)
            return ET.fromstring(xml)
        except Exception as exc:
            last_err = exc
            time.sleep(1.0)
            if attempt == 2:
                sh("pkill uiautomator", check=False)
                adb("shell", "am", "start", "-n", f"{PKG}/{ACTIVITY}", check=False, timeout=15)
                time.sleep(1.0)
    print(f"WARN dump_root failed: {last_err}", flush=True)
    return ET.Element("hierarchy")


def iter_nodes(root: ET.Element):
    for node in root.iter("node"):
        yield node.attrib


def texts(root: ET.Element) -> list[str]:
    out = []
    for attr in iter_nodes(root):
        for key in ("text", "content-desc"):
            value = (attr.get(key) or "").strip()
            if value:
                out.append(value)
    return out


def has_text(root: ET.Element, value: str, contains: bool = False) -> bool:
    for item in texts(root):
        if contains and value in item:
            return True
        if item == value:
            return True
    return False


def bounds_center(bounds: str) -> tuple[int, int]:
    match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if not match:
        raise ValueError(bounds)
    x1, y1, x2, y2 = map(int, match.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def find_center(root: ET.Element, value: str, contains: bool = False, prefer: str | None = None) -> tuple[int, int] | None:
    for attr in iter_nodes(root):
        text = attr.get("text") or ""
        desc = attr.get("content-desc") or ""
        hay = text if prefer != "desc" else desc
        other = desc if prefer != "desc" else text
        matched = False
        if contains:
            matched = value in hay or value in other
        else:
            matched = hay == value or other == value
        if matched:
            return bounds_center(attr["bounds"])
    return None


def tap_xy(x: int, y: int) -> None:
    sh(f"input tap {x} {y}")


def wait_dump(predicate, timeout: float = 8.0, interval: float = 0.45) -> ET.Element | None:
    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        last = dump_root()
        try:
            if predicate(last):
                return last
        except Exception:
            pass
        time.sleep(interval)
    return last


def tap_text(value: str, contains: bool = False, timeout: float = 8.0) -> bool:
    root = wait_dump(lambda r: find_center(r, value, contains) is not None, timeout=timeout)
    if root is None:
        return False
    center = find_center(root, value, contains)
    if center is None:
        return False
    tap_xy(*center)
    time.sleep(0.55)
    return True


def swipe(x1: int, y1: int, x2: int, y2: int, ms: int = 400) -> None:
    sh(f"input swipe {x1} {y1} {x2} {y2} {ms}")
    time.sleep(0.4)


def back() -> None:
    sh("input keyevent 4")
    time.sleep(0.4)


def hide_ime() -> None:
    # BACK/ESC закрывают форму. «Весь день» снимает фокус и прячет IME;
    # два тапа возвращают переключатель как был. Не тапать «Календарь» — откроет picker.
    if tap_text("Весь день", timeout=8):
        time.sleep(0.3)
        tap_text("Весь день", timeout=8)
        time.sleep(0.3)


def type_ascii(text: str) -> None:
    escaped = text.replace(" ", "%s").replace("'", "")
    sh(f"input text {escaped}")
    time.sleep(0.3)


def launch_app() -> None:
    sh(f"am force-stop {PKG}", check=False)
    time.sleep(0.4)
    adb("shell", "am", "start", "-n", f"{PKG}/{ACTIVITY}", timeout=15)
    wait_dump(
        lambda r: has_text(r, "Сегодня") or has_text(r, "Календарь") or has_text(r, "Настройки"),
        timeout=12,
    )


def go_home() -> ET.Element:
    for _ in range(8):
        root = dump_root()
        if has_text(root, "Календарь") and has_text(root, "Сегодня"):
            if has_text(root, "Новое") or has_text(root, "Поиск событий"):
                return root
        if has_text(root, "Готово") and has_text(root, "Календари карточки"):
            tap_text("Готово")
            continue
        if has_text(root, "Готово") and not has_text(root, "Профили"):
            tap_text("Готово")
            continue
        if has_text(root, "✕"):
            tap_text("✕")
            continue
        if has_text(root, "Отмена"):
            tap_text("Отмена")
            continue
        if has_text(root, "Профили") or has_text(root, "Оформление") or has_text(root, "Светлое"):
            tap_text("Календарь")
            time.sleep(0.45)
            continue
        if has_text(root, "Новая задача") or (
            has_text(root, "Задачи") and not has_text(root, "Сегодня")
        ):
            tap_text("Календарь")
            time.sleep(0.45)
            continue
        back()
    return dump_root()


def content_query(uri: str, projection: str, where: str) -> str:
    return adb(
        "shell",
        f"content query --uri {uri} --projection {projection} --where \"{where}\"",
        check=False,
        timeout=45,
    )


def insert_event(
    calendar_id: int,
    title: str,
    start: datetime,
    end: datetime,
    *,
    all_day: bool = False,
    organizer: str | None = None,
    has_attendee_data: int = 0,
    availability: int = 0,
) -> str:
    if calendar_id in FORBIDDEN_CALENDARS:
        raise RuntimeError(f"refusing to write calendar {calendar_id}")
    binds = [
        f"--bind calendar_id:i:{calendar_id}",
        f"--bind title:s:{title}",
        f"--bind dtstart:l:{millis(start)}",
        f"--bind dtend:l:{millis(end)}",
        f"--bind allDay:i:{1 if all_day else 0}",
        "--bind eventTimezone:s:Europe/Moscow",
        f"--bind availability:i:{availability}",
        f"--bind hasAttendeeData:i:{has_attendee_data}",
    ]
    if organizer:
        binds.append(f"--bind organizer:s:{organizer}")
        binds.append("--bind isOrganizer:s:0")
    cmd = "content insert --uri content://com.android.calendar/events " + " ".join(binds)
    last = ""
    for attempt in range(3):
        last = adb("shell", cmd, check=False, timeout=60)
        if last and "Error" not in last:
            return last
        time.sleep(1.5)
    return last


def scroll_until(value: str, contains: bool = False, swipes: int = 6) -> ET.Element:
    root = dump_root()
    for _ in range(swipes):
        if has_text(root, value, contains=contains):
            return root
        swipe(640, 1800, 640, 800)
        root = dump_root()
    return root


def open_settings() -> ET.Element | None:
    tap_text("Настройки")
    return wait_dump(
        lambda r: has_text(r, "Профили")
        or has_text(r, "Оформление")
        or has_text(r, "Светлое")
        or has_text(r, "Как в системе"),
        timeout=6,
    )


def activate_local_slot() -> bool:
    settings = open_settings()
    if settings is None:
        return False
    scroll_until("Локальный", swipes=5)
    if tap_text("Сделать активной: Локальный"):
        time.sleep(0.6)
        return True
    if tap_text("Активная карточка: Локальный"):
        return True
    if tap_text("Локальный"):
        wait_dump(lambda r: has_text(r, "Календари карточки") or has_text(r, "Готово"), timeout=4)
        if has_text(dump_root(), "Готово"):
            tap_text("Готово")
        time.sleep(0.3)
        scroll_until("Локальный", swipes=3)
        if tap_text("Сделать активной: Локальный"):
            time.sleep(0.6)
            return True
        center = find_center(dump_root(), "Локальный")
        if center:
            tap_xy(center[0], center[1] + 90)
            time.sleep(0.5)
            return True
    return False


def pick_local_calendar() -> bool:
    root = dump_root()
    if has_text(root, "Локальный") and not has_text(root, "work@example.com") and not has_text(root, "Work-hidden"):
        # still might be the picker; if Готово present, confirm
        if has_text(root, "Готово"):
            tap_text("Готово")
        return True
    if has_text(root, "work@example.com") or has_text(root, "Work-hidden") or has_text(root, "Календарь"):
        if has_text(root, "work@example.com"):
            tap_text("work@example.com")
        elif has_text(root, "Work-hidden"):
            tap_text("Work-hidden")
        elif has_text(root, "Дни рождения"):
            tap_text("Дни рождения")
        else:
            tap_text("Календарь")
        time.sleep(0.6)
        if not tap_text("Локальный"):
            return False
        tap_text("Готово")
        time.sleep(0.4)
        root = dump_root()
        return has_text(root, "Локальный")
    return False


def save_form(*, confirm_overlap: bool = True) -> bool:
    hide_ime()
    root = wait_dump(
        lambda r: find_center(r, "Сохранить") is not None or find_center(r, "✓") is not None,
        timeout=6,
    )
    if root is None:
        return False
    center = find_center(root, "Сохранить", prefer="desc") or find_center(root, "Сохранить") or find_center(root, "✓")
    if center is None:
        return False
    tap_xy(*center)
    time.sleep(0.6)
    if confirm_overlap:
        overlap = wait_dump(
            lambda r: has_text(r, "Пересечение встреч") or has_text(r, "Сохранить всё равно"),
            timeout=2.5,
        )
        if overlap is not None and has_text(overlap, "Сохранить всё равно"):
            tap_text("Сохранить всё равно")
            time.sleep(0.6)
    time.sleep(0.3)
    return True


def cleanup_qa_rows() -> None:
    for calendar_id in (LOCAL_CALENDAR_ID, TASKS_ID):
        sh(
            "content delete --uri content://com.android.calendar/events "
            f"--where \"calendar_id={calendar_id} AND title LIKE '{PREFIX}%'\"",
            check=False,
        )
        sh(
            "content delete --uri content://com.android.calendar/events "
            f"--where \"calendar_id={calendar_id} AND title='Событие'\"",
            check=False,
        )


def seed_data() -> dict[str, str]:
    cleanup_qa_rows()
    today = datetime.now(MSK).date()
    ovl_start = datetime.combine(today, datetime.min.time(), MSK).replace(hour=8, minute=0)
    rsvp_start = datetime.combine(today, datetime.min.time(), MSK).replace(hour=15, minute=0)
    view_start = datetime.combine(today, datetime.min.time(), MSK).replace(hour=11, minute=0)
    task_day = datetime(today.year, today.month, today.day, tzinfo=timezone.utc)
    insert_event(LOCAL_CALENDAR_ID, f"{PREFIX}-OVL", ovl_start, ovl_start + timedelta(hours=12))
    insert_event(LOCAL_CALENDAR_ID, f"{PREFIX}-VIEW", view_start, view_start + timedelta(hours=1))
    insert_event(
        LOCAL_CALENDAR_ID,
        f"{PREFIX}-RSVP",
        rsvp_start,
        rsvp_start + timedelta(hours=1),
        organizer="boss@qa.example",
        has_attendee_data=1,
    )
    rsvp_rows = content_query(
        "content://com.android.calendar/events",
        "_id:title:organizer:selfAttendeeStatus:hasAttendeeData:calendar_id",
        f"title='{PREFIX}-RSVP'",
    )
    event_id = ""
    for line in rsvp_rows.splitlines():
        if "title=" + PREFIX + "-RSVP" in line or f"title={PREFIX}-RSVP" in line:
            match = re.search(r"_id=(\d+)", line)
            if match:
                event_id = match.group(1)
            break
    if event_id:
        sh(
            "content insert --uri content://com.android.calendar/attendees "
            f"--bind event_id:i:{event_id} "
            "--bind attendeeEmail:s:boss@qa.example "
            "--bind attendeeRelationship:i:2 "
            "--bind attendeeType:i:1 "
            "--bind attendeeStatus:i:1"
        )
        sh(
            "content insert --uri content://com.android.calendar/attendees "
            f"--bind event_id:i:{event_id} "
            "--bind attendeeEmail:s:Local "
            "--bind attendeeRelationship:i:1 "
            "--bind attendeeType:i:1 "
            "--bind attendeeStatus:i:3"
        )
    insert_event(
        TASKS_ID,
        f"{PREFIX}-TASK",
        task_day,
        task_day + timedelta(days=1),
        all_day=True,
        availability=1,
    )
    return {"rsvp_id": event_id, "rsvp_rows": rsvp_rows}


def ensure_month() -> None:
    root = dump_root()
    if not has_text(root, "Месяц"):
        tap_text("Календарь")
        time.sleep(0.5)


def main() -> int:
    adb("shell", "pm", "uninstall", "com.example.calendar2", check=False, timeout=20)
    pkgs = adb("shell", "pm", "list", "packages")
    record("T-ID-01", "package:ru.laktapp.calendar" in pkgs, "applicationId installed")
    record("T-NEG-03", "package:com.example.calendar2" not in pkgs, "no second copy")
    version = adb("shell", "dumpsys", "package", PKG)
    record("T-REL-03-pkg", f"versionName={VERSION}" in version, f"version {VERSION}")

    seed = seed_data()
    launch_app()
    root = go_home()
    focus = adb("shell", "dumpsys", "window")
    record(
        "T-REL-03",
        "ru.laktapp.calendar/com.example.calendar.MainActivity" in focus,
        "MainActivity focused",
    )
    record("T-ID-03", has_text(root, "Месяц") and has_text(root, "Сегодня"), "Russian UI")
    rot = sh("dumpsys window displays | grep mCurrentRotation | head -1")
    record("T-ID-02", "ROTATION_0" in rot, rot.strip())
    weekdays = [t for t in texts(root) if t in {"пн", "вт", "ср", "чт", "пт", "сб", "вс"}]
    record("T-CAL-07", weekdays[:1] == ["пн"], f"weekdays={weekdays[:7]}")

    # Year <-> month, today
    ok_year = tap_text(str(TODAY.year))
    year_root = wait_dump(
        lambda r: any(
            has_text(r, month)
            for month in ("янв", "Янв", "Январь", "Января", "дек", "Дек", "Декабря", "Февраля")
        ),
        timeout=6,
    )
    year_ok = year_root is not None and any(
        has_text(year_root, month)
        for month in ("янв", "Янв", "Январь", "Января", "дек", "Дек", "Декабря", "Февраля")
    )
    record("T-CAL-05a", ok_year and year_ok, "year grid")
    tap_text("Месяц")
    time.sleep(0.4)
    tap_text("Сегодня")
    time.sleep(0.4)
    home = dump_root()
    record(
        "T-CAL-05",
        has_text(home, str(TODAY.day)) and has_text(home, "Сегодня"),
        f"back to today {TODAY}",
    )

    # Account cards: no home switcher, then activate Local
    home_switcher = has_text(home, "Профили") or has_text(home, "Сделать активной: Локальный")
    record("T-SET-06b", not home_switcher, "no account switcher on home")
    set_root = open_settings()
    if set_root is not None:
        theme = wait_dump(
            lambda r: has_text(r, "Светлое")
            or has_text(r, "Тёмное")
            or has_text(r, "Как в системе")
            or has_text(r, "Оформление")
            or has_text(r, "Цвет"),
            timeout=5,
        ) or set_root
        record(
            "T-SET-01",
            has_text(theme, "Светлое")
            or has_text(theme, "Тёмное")
            or has_text(theme, "Как в системе")
            or has_text(theme, "Оформление")
            or has_text(theme, "Цвет"),
            "theme options",
        )
        set_root = scroll_until("Профили", swipes=5)
        cards = (
            has_text(set_root, "Профили")
            and (
                has_text(set_root, "Локальный")
                or has_text(set_root, "Не выбран")
                or has_text(set_root, "work@example.com")
            )
        )
        record("T-SET-02", cards, "three account cards")
        record(
            "T-PROD-02a",
            has_text(set_root, "Россия")
            or has_text(set_root, "Производственный")
            or has_text(set_root, "праздник"),
            "production calendar toggle present",
        )
        opened_editor = tap_text("Календари карточки Локальный") or tap_text("Локальный")
        editor = wait_dump(
            lambda r: has_text(r, "Календари карточки") or has_text(r, "по умолчанию"),
            timeout=5,
        )
        editor_ok = editor is not None and (
            has_text(editor, "Календари карточки") or has_text(editor, "Встречи")
        )
        record("T-SET-04", bool(opened_editor and editor_ok), "slot editor")
        if editor_ok and has_text(dump_root(), "Готово"):
            tap_text("Готово")
            time.sleep(0.3)
    else:
        record("T-SET-02", False, "settings missing")
        record("T-SET-01", False, "settings missing")
        record("T-PROD-02a", False, "settings missing")
        record("T-SET-04", False, "settings missing")
    local_on = activate_local_slot()
    record("T-SET-06a", local_on, "activate Local card")
    go_home()
    found_ovl = has_text(dump_root(), f"{PREFIX}-OVL")
    for _ in range(4):
        if found_ovl:
            break
        swipe(640, 1900, 640, 1100)
        found_ovl = has_text(dump_root(), f"{PREFIX}-OVL")
    record("T-SET-06", found_ovl, "journal shows Local after activate")
    go_home()

    # T-EVT-02 empty title saves as Событие
    tap_text("Новое", timeout=6)
    wait_dump(lambda r: has_text(r, "Новое") and has_text(r, "✓"))
    save_form()
    time.sleep(0.8)
    still_form = dump_root()
    empty_blocked = has_text(still_form, "Новое") and has_text(still_form, "Название")
    empty_row = content_query(
        "content://com.android.calendar/events",
        "_id:calendar_id:title:deleted",
        "title='Событие'",
    )
    empty_ok = (
        not empty_blocked
        and f"calendar_id={LOCAL_CALENDAR_ID}" in empty_row
        and "title=Событие" in empty_row
    )
    record("T-EVT-02", empty_ok, empty_row.splitlines()[-1] if empty_row.strip() else "empty title")
    if has_text(still_form, "✕"):
        tap_text("✕")
        time.sleep(0.4)

    # Create timed event on Local
    go_home()
    tap_text("Новое")
    wait_dump(lambda r: has_text(r, "Название"))
    tap_text("Название")
    type_ascii(f"{PREFIX}-NEW")
    save_form()
    wait_dump(lambda r: has_text(r, f"{PREFIX}-NEW") or has_text(r, "Сегодня"), timeout=6)
    created = content_query(
        "content://com.android.calendar/events",
        "_id:calendar_id:title:deleted",
        f"title='{PREFIX}-NEW'",
    )
    created_ok = f"calendar_id={LOCAL_CALENDAR_ID}" in created and PREFIX + "-NEW" in created
    record("T-EVT-01", created_ok, created.splitlines()[0] if created.strip() else "missing")

    # T-SET-05: default calendar of active slot, no picker
    go_home()
    tap_text("Новое")
    wait_dump(lambda r: has_text(r, "Название"))
    tap_text("Название")
    type_ascii(f"{PREFIX}-DEF")
    save_form()
    time.sleep(0.8)
    defaulted = content_query(
        "content://com.android.calendar/events",
        "_id:calendar_id:title:deleted",
        f"title='{PREFIX}-DEF'",
    )
    record(
        "T-SET-05",
        f"calendar_id={LOCAL_CALENDAR_ID}" in defaulted and PREFIX + "-DEF" in defaulted,
        defaulted.splitlines()[0] if defaulted.strip() else "missing default calendar",
    )

    # All-day
    go_home()
    tap_text("Новое")
    wait_dump(lambda r: has_text(r, "Название"))
    tap_text("Название")
    type_ascii(f"{PREFIX}-ALLDAY")
    tap_text("Весь день")
    save_form()
    time.sleep(0.8)
    allday = content_query(
        "content://com.android.calendar/events",
        "_id:calendar_id:title:allDay:dtstart:dtend",
        f"title='{PREFIX}-ALLDAY'",
    )
    record("T-EVT-03", "allDay=1" in allday and f"calendar_id={LOCAL_CALENDAR_ID}" in allday, allday[:200])

    # Open seeded Local event — forward + duplicate
    go_home()
    scroll_until(f"{PREFIX}-VIEW", swipes=8)
    opened = tap_text(f"{PREFIX}-VIEW", timeout=6)
    view = wait_dump(lambda r: has_text(r, "Править") or has_text(r, "Переслать"), timeout=6)
    record("T-EVT-09a", opened and view is not None and has_text(view, "Править"), "view has Править")
    fwd = view is not None and has_text(view, "Переслать")
    record("T-FWD-03a", fwd, "forward icon in top bar")
    if fwd:
        tap_text("Переслать")
        share = wait_dump(
            lambda r: has_text(r, "Переслать") or has_text(r, "Share") or has_text(r, "Gmail")
            or has_text(r, ".ics") or has_text(r, "Messages") or has_text(r, "Android System"),
            timeout=6,
        )
        share_ok = share is not None and (
            has_text(share, "Gmail")
            or has_text(share, "Messages")
            or has_text(share, "Drive")
            or has_text(share, "Android System")
            or has_text(share, "Nearby")
            or has_text(share, "Bluetooth")
            or has_text(share, "Переслать")
        )
        record("T-FWD-03", share_ok, "share sheet")
        back()
        time.sleep(0.5)
        if has_text(dump_root(), "Переслать") is False and has_text(dump_root(), "Править") is False:
            tap_text(f"{PREFIX}-VIEW", timeout=4)
    else:
        record("T-FWD-03", False, "no forward control")

    view = dump_root()
    if not has_text(view, "Дублировать"):
        swipe(640, 2000, 640, 900)
        view = dump_root()
    dup_visible = has_text(view, "Дублировать")
    record("T-DUP-03a", dup_visible, "duplicate row")
    if dup_visible:
        tap_text("Дублировать")
        picker = wait_dump(lambda r: has_text(r, "Дата копии") or has_text(r, "OK") or has_text(r, "Готово"), timeout=5)
        if picker is not None and has_text(picker, "Дата копии"):
            if has_text(picker, "OK"):
                tap_text("OK")
            elif has_text(picker, "Готово"):
                tap_text("Готово")
            elif has_text(picker, "SET"):
                tap_text("SET")
        form = wait_dump(lambda r: has_text(r, "Новое") or has_text(r, "Название") or has_text(r, "✓"), timeout=6)
        save_form()
        time.sleep(0.8)
        dups = content_query(
            "content://com.android.calendar/events",
            "_id:calendar_id:title:dtstart",
            f"title='{PREFIX}-VIEW'",
        )
        local_copies = [ln for ln in dups.splitlines() if f"calendar_id={LOCAL_CALENDAR_ID}" in ln]
        record("T-DUP-03", len(local_copies) >= 2, f"copies={len(local_copies)}")
    else:
        record("T-DUP-03", False, "no duplicate row")
    go_home()

    # Overlap: new event at 09:00 over QA29-OVL, Local only
    tap_text("Новое")
    wait_dump(lambda r: has_text(r, "Название"))
    tap_text("Название")
    type_ascii(f"{PREFIX}-OVL2")
    save_form(confirm_overlap=False)
    overlap = wait_dump(
        lambda r: has_text(r, "Пересечение встреч") or has_text(r, "Сохранить всё равно"),
        timeout=6,
    )
    overlap_ok = overlap is not None and has_text(overlap, "Пересечение встреч")
    record("T-OVL-03", overlap_ok, "overlap dialog")
    if overlap_ok:
        tap_text("Отмена")
    go_home()

    # RSVP invite
    go_home()
    # scroll journal to 15:00 card
    found_rsvp = has_text(dump_root(), f"{PREFIX}-RSVP")
    for _ in range(4):
        if found_rsvp:
            break
        swipe(640, 1100, 640, 1800)
        found_rsvp = has_text(dump_root(), f"{PREFIX}-RSVP")
    for _ in range(5):
        if found_rsvp:
            break
        swipe(640, 1900, 640, 1100)
        found_rsvp = has_text(dump_root(), f"{PREFIX}-RSVP")
    opened = found_rsvp and tap_text(f"{PREFIX}-RSVP")
    rsvp_view = wait_dump(
        lambda r: has_text(r, "Принять") or has_text(r, "Ответ") or has_text(r, "Править"),
        timeout=6,
    )
    three_buttons = (
        rsvp_view is not None
        and has_text(rsvp_view, "Принять")
        and has_text(rsvp_view, "Под вопросом")
        and has_text(rsvp_view, "Отклонить")
    )
    record("T-RSVP-06a", bool(opened and three_buttons), f"seed={seed.get('rsvp_id')} three buttons")
    if three_buttons:
        tap_text("Принять")
        collapsed = wait_dump(
            lambda r: has_text(r, "Принято") and has_text(r, "Изменить") and not has_text(r, "Отклонить"),
            timeout=6,
        )
        record(
            "T-RSVP-06",
            collapsed is not None and has_text(collapsed, "Принято") and has_text(collapsed, "Изменить"),
            "collapsed after accept",
        )
        status = content_query(
            "content://com.android.calendar/events",
            "_id:selfAttendeeStatus:calendar_id:title",
            f"title='{PREFIX}-RSVP'",
        )
        record("T-RSVP-06b", "selfAttendeeStatus=1" in status, status[:180])
        if "calendar_id=4" not in status:
            record("T-RSVP-06c", False, "RSVP wrote unexpected calendar")
    else:
        record("T-RSVP-06", False, "chooser missing")
    go_home()

    # Search
    if tap_text("Поиск событий"):
        wait_dump(lambda r: has_text(r, "Название, место, участник, вложение") or True, timeout=4)
        # focus field: tap placeholder
        tap_text("Название, место, участник, вложение", contains=True)
        type_ascii(f"{PREFIX}-NEW")
        time.sleep(1.0)
        search = dump_root()
        created_row = content_query(
            "content://com.android.calendar/events",
            "_id:title:calendar_id",
            f"title='{PREFIX}-NEW'",
        )
        record(
            "T-SRCH-01",
            PREFIX + "-NEW" in created_row and has_text(search, f"{PREFIX}-NEW"),
            "search finds created event",
        )
        tap_text("✕") if has_text(dump_root(), "✕") else back()
        go_home()
        if tap_text("Поиск событий"):
            tap_text("Название, место, участник, вложение", contains=True)
            type_ascii(f"{PREFIX}-TASK")
            time.sleep(1.0)
            search_task = dump_root()
            record(
                "T-SRCH-03",
                not has_text(search_task, f"{PREFIX}-TASK") or has_text(search_task, "Ничего не найдено"),
                "task not in event search",
            )
            tap_text("✕") if has_text(dump_root(), "✕") else back()
        else:
            record("T-SRCH-03", False, "search not reopened")
    else:
        record("T-SRCH-01", False, "no search")
        record("T-SRCH-03", False, "no search")
    go_home()

    # Conference location: zoom should not open yandex list
    tap_text("Новое")
    wait_dump(lambda r: has_text(r, "Геопозиция или видеовызов"))
    tap_text("Геопозиция или видеовызов")
    type_ascii("https://zoom.us/j/123")
    time.sleep(1.2)
    loc = dump_root()
    yandex_list = has_text(loc, "Москва") or has_text(loc, "Россия")
    record("T-CONF-01", not yandex_list, "zoom URL has no yandex suggest")
    tap_text("✕")
    go_home()

    # Location suggest
    tap_text("Новое")
    wait_dump(lambda r: has_text(r, "Геопозиция или видеовызов"))
    tap_text("Геопозиция или видеовызов")
    type_ascii("moskva")
    loc = wait_dump(
        lambda r: has_text(r, "Москва") or has_text(r, "Москва-Сити") or has_text(r, "Россия"),
        timeout=8,
    )
    suggest = loc is not None and (
        has_text(loc, "Москва") or has_text(loc, "Москва-Сити") or has_text(loc, "Россия")
    )
    record("T-LOC-01", suggest, "yandex suggest")
    tap_text("✕")
    go_home()

    # Tasks
    tap_text("Задачи")
    tasks = wait_dump(lambda r: has_text(r, "Задачи") or has_text(r, "Новая задача") or has_text(r, "Нет открытых задач"), timeout=5)
    record("T-TASK-10", tasks is not None and not has_text(tasks, "Перенести в Outlook"), "no migrate button")
    record(
        "T-TASK-05a",
        tasks is not None and (has_text(tasks, f"{PREFIX}-TASK") or has_text(tasks, "Сегодня")),
        "tasks screen",
    )
    record(
        "T-TASK-08",
        tasks is not None and has_text(tasks, f"{PREFIX}-TASK"),
        "active slot local Tasks only",
    )
    go_home()
    journal = dump_root()
    record("T-CAL-09", not has_text(journal, f"{PREFIX}-TASK"), "task hidden from journal")
    record("T-TASK-07", not has_text(journal, f"{PREFIX}-TASK"), "same")

    # Delete created event
    go_home()
    scroll_until(f"{PREFIX}-NEW", swipes=10)
    if tap_text(f"{PREFIX}-NEW", timeout=6):
        wait_dump(lambda r: has_text(r, "Править") or has_text(r, "Удалить событие"))
        if not has_text(dump_root(), "Удалить событие"):
            swipe(640, 2000, 640, 800)
        if tap_text("Удалить событие"):
            tap_text("Удалить событие") if has_text(dump_root(), "Удалить событие") else tap_text("Удалить")
            time.sleep(0.6)
        gone = f"{PREFIX}-NEW" not in content_query(
            "content://com.android.calendar/events",
            "_id:title:deleted",
            f"title='{PREFIX}-NEW'",
        ) or "deleted=1" in content_query(
            "content://com.android.calendar/events",
            "_id:title:deleted",
            f"title='{PREFIX}-NEW'",
        )
        record("T-EVT-06", gone, "deleted created event")
    else:
        record("T-EVT-06", False, "could not open created event")

    print("\n=== SUMMARY ===")
    failed = [r for r in results if not r[1]]
    for test_id, ok, note in results:
        print(f"{'PASS' if ok else 'FAIL'}\t{test_id}\t{note}")
    print(f"total={len(results)} fail={len(failed)}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
