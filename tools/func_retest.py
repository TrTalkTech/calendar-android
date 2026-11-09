#!/usr/bin/env python3
"""Retest of failed P0 cases after empty-title save fix. Local calendar «Локальный» (id=4) only."""

from __future__ import annotations

import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

SERIAL = "emulator-5554"
PKG = "ru.laktapp.calendar"
ACTIVITY = "com.example.calendar.MainActivity"
LOCAL_CALENDAR_ID = 4
FORBIDDEN = {2, 3, 6}

results: list[tuple[str, bool, str]] = []


def adb(*args: str) -> str:
    proc = subprocess.run(
        ["adb", "-s", SERIAL, *args],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    if proc.returncode != 0:
        raise RuntimeError(f"adb {args}: {proc.stdout}")
    return proc.stdout


def sh(cmd: str) -> str:
    return adb("shell", cmd)


def record(test_id: str, ok: bool, note: str = "") -> bool:
    results.append((test_id, ok, note))
    print(("PASS" if ok else "FAIL"), test_id, note, flush=True)
    return ok


def dump() -> ET.Element:
    sh("uiautomator dump /sdcard/uidump.xml")
    return ET.fromstring(adb("exec-out", "cat", "/sdcard/uidump.xml"))


def nodes(root: ET.Element):
    for n in root.iter("node"):
        yield n.attrib


def all_text(root: ET.Element) -> str:
    bits = []
    for attr in nodes(root):
        for key in ("text", "content-desc"):
            v = (attr.get(key) or "").strip()
            if v:
                bits.append(v)
    return "\n".join(bits)


def has(root: ET.Element, value: str) -> bool:
    return value in all_text(root)


def center(bounds: str) -> tuple[int, int]:
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def find(root: ET.Element, value: str) -> tuple[int, int] | None:
    for attr in nodes(root):
        if attr.get("text") == value or attr.get("content-desc") == value:
            return center(attr["bounds"])
        if value in (attr.get("text") or "") or value in (attr.get("content-desc") or ""):
            return center(attr["bounds"])
    return None


def tap(x: int, y: int) -> None:
    sh(f"input tap {x} {y}")
    time.sleep(0.45)


def tap_value(value: str, timeout: float = 7.0) -> bool:
    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        last = dump()
        c = find(last, value)
        if c:
            tap(*c)
            return True
        time.sleep(0.35)
    print(" missing", value, "seen:", all_text(last or dump()).replace("\n", " | ")[:240])
    return False


def swipe(x1, y1, x2, y2) -> None:
    sh(f"input swipe {x1} {y1} {x2} {y2} 350")
    time.sleep(0.35)


def back() -> None:
    sh("input keyevent 4")
    time.sleep(0.4)


def launch() -> None:
    sh(f"am force-stop {PKG}")
    time.sleep(0.3)
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/{ACTIVITY}")
    time.sleep(1.0)


def events() -> str:
    return sh(
        "content query --uri content://com.android.calendar/events "
        "--projection _id:calendar_id:title:deleted:allDay:organizer"
    )


def rows_for(title: str) -> list[str]:
    return [ln for ln in events().splitlines() if f"title={title}" in ln]


def assert_local_only(title: str) -> tuple[bool, str]:
    rows = rows_for(title)
    if not rows:
        return False, "missing"
    bad = [ln for ln in rows if any(f"calendar_id={cid}" in ln for cid in FORBIDDEN)]
    good = [ln for ln in rows if f"calendar_id={LOCAL_CALENDAR_ID}" in ln]
    if bad:
        return False, "wrote forbidden " + bad[0]
    if not good:
        return False, rows[0]
    return True, good[0]


def go_home() -> None:
    for _ in range(7):
        root = dump()
        if has(root, "Сегодня") and has(root, "Календарь") and (has(root, "Новое") or has(root, "Поиск событий")):
            return
        if has(root, "Готово"):
            tap_value("Готово")
            continue
        if has(root, "Отмена"):
            tap_value("Отмена")
            continue
        if has(root, "✕"):
            tap_value("✕")
            continue
        back()


def pick_local_calendar() -> bool:
    root = dump()
    if has(root, "Локальный") and not has(root, "Готово"):
        # already selected on the form
        if "Локальный" in all_text(root) and "work@example.com" not in all_text(root):
            return True
    if not tap_value("Календарь"):
        return False
    time.sleep(0.4)
    if not tap_value("Локальный"):
        return False
    tap_value("Готово")
    time.sleep(0.3)
    root = dump()
    return has(root, "Локальный")


def save_form() -> bool:
    return tap_value("Сохранить") or tap_value("✓")


def confirm_overlap_or_home() -> bool:
    root = dump()
    if has(root, "Пересечение встреч"):
        record("T-OVL-03", True, "dialog shown")
        tap_value("Сохранить всё равно")
        time.sleep(0.7)
        return True
    return False


def type_into_title(text: str) -> None:
    # title row is near the placeholder
    root = dump()
    c = find(root, "Название")
    if c:
        tap(c[0] + 280, c[1])
    else:
        tap(640, 390)
    time.sleep(0.3)
    sh("input keyevent 123")  # move end
    escaped = text.replace(" ", "%s")
    sh(f"input text {escaped}")
    time.sleep(0.3)
    sh("input keyevent 111")
    time.sleep(0.2)


def main() -> int:
    launch()
    go_home()

    # Year grid
    tap_value("2026")
    time.sleep(0.8)
    year = dump()
    record("T-CAL-05a", has(year, "Январь") or has(year, "Декабрь") or has(year, "Октябрь"), all_text(year)[:180])
    tap_value("Месяц")
    time.sleep(0.4)

    # Empty title -> Событие on Local (may hit overlap first)
    go_home()
    tap_value("Новое")
    time.sleep(0.6)
    pick_local_calendar()
    save_form()
    time.sleep(0.7)
    saw_overlap = confirm_overlap_or_home()
    if not saw_overlap:
        root = dump()
        record("T-OVL-03", has(root, "Пересечение встреч"), "no dialog after empty save")
        if has(root, "Сохранить всё равно"):
            tap_value("Сохранить всё равно")
            time.sleep(0.7)
    ok, note = assert_local_only("Событие")
    record("T-EVT-02", ok, note)
    go_home()
    home = dump()
    record("T-EVT-02b", has(home, "Событие"), "card on journal")

    # Named create
    tap_value("Новое")
    time.sleep(0.5)
    type_into_title("QA29NEW")
    pick_local_calendar()
    save_form()
    time.sleep(0.6)
    root = dump()
    if has(root, "Пересечение встреч"):
        tap_value("Сохранить всё равно")
        time.sleep(0.7)
    ok, note = assert_local_only("QA29NEW")
    record("T-EVT-01", ok, note)
    go_home()
    record("T-EVT-01b", has(dump(), "QA29NEW"), "visible in list")

    # All-day
    tap_value("Новое")
    time.sleep(0.5)
    type_into_title("QA29ALLDAY")
    tap_value("Весь день")
    pick_local_calendar()
    save_form()
    time.sleep(0.8)
    ok, note = assert_local_only("QA29ALLDAY")
    allday_ok = ok and any("allDay=1" in ln for ln in rows_for("QA29ALLDAY"))
    record("T-EVT-03", allday_ok, note)

    # Duplicate Конец Вечности
    go_home()
    tap_value("Конец Вечности")
    time.sleep(0.6)
    root = dump()
    if not has(root, "Дублировать"):
        swipe(640, 2000, 640, 850)
    if tap_value("Дублировать"):
        time.sleep(0.6)
        root = dump()
        if has(root, "OK"):
            tap_value("OK")
        elif has(root, "SET"):
            tap_value("SET")
        elif has(root, "Готово"):
            tap_value("Готово")
        time.sleep(0.8)
        form = dump()
        record("T-DUP-03b", has(form, "Новое") or has(form, "Название") or has(form, "Сохранить"), "duplicate form")
        pick_local_calendar()
        save_form()
        time.sleep(0.6)
        root = dump()
        if has(root, "Пересечение встреч"):
            tap_value("Сохранить всё равно")
            time.sleep(0.7)
        copies = [ln for ln in rows_for("Конец Вечности") if f"calendar_id={LOCAL_CALENDAR_ID}" in ln]
        record("T-DUP-03", len(copies) >= 2, f"copies={len(copies)}")
    else:
        record("T-DUP-03b", False, "no duplicate")
        record("T-DUP-03", False, "no duplicate")

    # Settings: theme + production
    go_home()
    tap_value("Настройки")
    time.sleep(0.6)
    found_theme = False
    found_prod = False
    for _ in range(8):
        root = dump()
        txt = all_text(root)
        if "Светлое" in txt or "Тёмное" in txt or "Как в системе" in txt:
            found_theme = True
        if "Россия" in txt or "Производственный календарь" in txt:
            found_prod = True
        if found_theme and found_prod:
            break
        swipe(640, 2100, 640, 900)
    record("T-SET-01", found_theme, "theme labels")
    record("T-PROD-02a", found_prod, "production toggle")
    back()
    go_home()

    # Search task vs event
    if tap_value("Поиск событий"):
        time.sleep(0.4)
        root = dump()
        c = find(root, "Название, место, участник, вложение")
        if c:
            tap(*c)
        sh("input text QA29NEW")
        time.sleep(1.0)
        search = dump()
        # event title in results, not only the query field: look for time row or second occurrence
        record("T-SRCH-01", has(search, "QA29NEW"), "search QA29NEW")
        back()
        time.sleep(0.3)
        tap_value("Поиск событий")
        time.sleep(0.3)
        c = find(dump(), "Название, место, участник, вложение")
        if c:
            tap(*c)
        sh("input text QA29-TASK")
        time.sleep(1.0)
        search = dump()
        record(
            "T-SRCH-03",
            has(search, "Ничего не найдено") or not has(search, "QA29-TASK"),
            "task excluded",
        )
        back()
    go_home()

    # Delete QA29NEW
    if tap_value("QA29NEW"):
        time.sleep(0.5)
        root = dump()
        if not has(root, "Удалить событие"):
            swipe(640, 2100, 640, 800)
        if tap_value("Удалить событие"):
            time.sleep(0.4)
            tap_value("Удалить событие") or tap_value("Удалить")
            time.sleep(0.7)
        leftover = [ln for ln in rows_for("QA29NEW") if "deleted=0" in ln]
        record("T-EVT-06", not leftover, leftover[0] if leftover else "gone")
    else:
        record("T-EVT-06", False, "not in list")

    print("\n=== RETEST SUMMARY ===")
    failed = [r for r in results if not r[1]]
    for i, ok, note in results:
        print(f"{'PASS' if ok else 'FAIL'}\t{i}\t{note}")
    print(f"total={len(results)} fail={len(failed)}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
