package com.example.calendar

import com.example.calendar.data.ACCOUNT_SLOT_COUNT
import com.example.calendar.data.AccountSlot
import com.example.calendar.data.AccountSlots
import com.example.calendar.data.AccountSlotsState
import com.example.calendar.data.CalendarAccountKind
import com.example.calendar.data.WritableCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSlotsTest {

    @Test
    fun seed_fillsThreeAccountsAndPicksDefault() {
        val calendars = listOf(
            cal(1, "Work", "a@work.com", "com.google.android.gm.exchange", primary = true),
            cal(2, "Tasks", "a@work.com", "com.google.android.gm.exchange"),
            cal(3, "Личное", "personal@example.com", "com.google"),
            cal(4, "Локальный", "Local", "LOCAL"),
            cal(5, "Задачи", "Zadachi", "LOCAL"),
        )
        val state = AccountSlots.seed(calendars)
        assertEquals(ACCOUNT_SLOT_COUNT, state.slots.size)
        assertEquals(listOf(1L), state.slot(0).eventCalendarIds)
        assertEquals(1L, state.slot(0).defaultEventCalendarId)
        assertEquals(2L, state.slot(0).taskCalendarId)
        assertEquals(listOf(3L), state.slot(1).eventCalendarIds)
        assertEquals(listOf(4L), state.slot(2).eventCalendarIds)
        assertEquals(5L, state.slot(2).taskCalendarId)
        assertEquals(0, state.activeIndex)
        assertEquals(CalendarAccountKind.Exchange, AccountSlots.groupedByAccount(calendars).first().kind)
    }

    @Test
    fun toggle_movesCalendarBetweenSlotsExclusively() {
        val start = AccountSlotsState(
            slots = listOf(
                AccountSlot(eventCalendarIds = listOf(1, 8), defaultEventCalendarId = 1),
                AccountSlot(),
                AccountSlot(),
            ),
            activeIndex = 0,
        )
        val moved = AccountSlots.toggleEventCalendar(start, 1, 8)
        assertEquals(listOf(1L), moved.slot(0).eventCalendarIds)
        assertEquals(listOf(8L), moved.slot(1).eventCalendarIds)
        assertEquals(8L, moved.slot(1).defaultEventCalendarId)
        assertTrue(8L !in AccountSlots.usedIds(moved, exceptSlot = 1))
    }

    @Test
    fun removingDefaultPicksNextEventCalendar() {
        val start = AccountSlotsState(
            slots = listOf(
                AccountSlot(eventCalendarIds = listOf(1, 2), defaultEventCalendarId = 1),
                AccountSlot(),
                AccountSlot(),
            ),
        )
        val next = AccountSlots.toggleEventCalendar(start, 0, 1)
        assertEquals(listOf(2L), next.slot(0).eventCalendarIds)
        assertEquals(2L, next.slot(0).defaultEventCalendarId)
    }

    @Test
    fun setDefault_onlyAmongSelected() {
        val start = AccountSlotsState(
            slots = listOf(
                AccountSlot(eventCalendarIds = listOf(1, 2), defaultEventCalendarId = 1),
                AccountSlot(),
                AccountSlot(),
            ),
        )
        assertEquals(2L, AccountSlots.setDefaultEventCalendar(start, 0, 2).slot(0).defaultEventCalendarId)
        assertEquals(1L, AccountSlots.setDefaultEventCalendar(start, 0, 99).slot(0).defaultEventCalendarId)
    }

    @Test
    fun taskCalendar_isExclusiveAndCanClear() {
        val start = AccountSlotsState(
            slots = listOf(
                AccountSlot(eventCalendarIds = listOf(1), defaultEventCalendarId = 1, taskCalendarId = 10),
                AccountSlot(eventCalendarIds = listOf(2), defaultEventCalendarId = 2),
                AccountSlot(),
            ),
            activeIndex = 0,
        )
        val moved = AccountSlots.setTaskCalendar(start, 1, 10)
        assertNull(moved.slot(0).taskCalendarId)
        assertEquals(10L, moved.slot(1).taskCalendarId)
        val cleared = AccountSlots.setTaskCalendar(moved, 1, 10)
        assertNull(cleared.slot(1).taskCalendarId)
    }

    @Test
    fun setActive_ignoresEmptySlot() {
        val start = AccountSlotsState(
            slots = listOf(
                AccountSlot(eventCalendarIds = listOf(1), defaultEventCalendarId = 1),
                AccountSlot(),
                AccountSlot(),
            ),
            activeIndex = 0,
        )
        assertEquals(0, AccountSlots.setActive(start, 1).activeIndex)
        val withSecond = AccountSlots.toggleEventCalendar(start, 1, 3)
        assertEquals(1, AccountSlots.setActive(withSecond, 1).activeIndex)
    }

    @Test
    fun eventFilterIds_nullWhenNothingConfigured() {
        assertNull(AccountSlots.eventFilterIds(AccountSlotsState()))
        val configured = AccountSlots.toggleEventCalendar(AccountSlotsState(), 0, 4)
        assertEquals(setOf(4L), AccountSlots.eventFilterIds(configured))
    }

    @Test
    fun leftoverLocalTask_attachesToLocalSlotNotGoogle() {
        val calendars = listOf(
            cal(1, "Work", "a@work.com", "com.google.android.gm.exchange", primary = true),
            cal(2, "Tasks", "a@work.com", "com.google.android.gm.exchange"),
            cal(3, "Личное", "personal@example.com", "com.google"),
            cal(4, "Локальный", "Local", "LOCAL"),
            cal(5, "Задачи", "Zadachi", "LOCAL"),
        )
        val state = AccountSlots.seed(calendars)
        assertNull(state.slot(1).taskCalendarId)
        assertEquals(5L, state.slot(2).taskCalendarId)
        assertEquals(setOf(1L), AccountSlots.eventFilterIds(state))
    }

    @Test
    fun eventFilterIds_followsActiveSlot() {
        val start = AccountSlotsState(
            slots = listOf(
                AccountSlot(eventCalendarIds = listOf(1, 8), defaultEventCalendarId = 1),
                AccountSlot(eventCalendarIds = listOf(4), defaultEventCalendarId = 4, taskCalendarId = 5),
                AccountSlot(),
            ),
            activeIndex = 0,
        )
        assertEquals(setOf(1L, 8L), AccountSlots.eventFilterIds(start))
        val local = AccountSlots.setActive(start, 1)
        assertEquals(setOf(4L), AccountSlots.eventFilterIds(local))
        assertEquals(5L, local.active().taskCalendarId)
    }

    @Test
    fun activeSlot_readsAndWritesOnlyItsTaskCalendar() {
        val calendars = listOf(
            cal(1, "Work", "work@example.com", "com.google.android.gm.exchange", primary = true),
            cal(2, "Tasks", "work@example.com", "com.google.android.gm.exchange"),
            cal(3, "Личное", "personal@example.com", "com.google"),
            cal(4, "Локальный", "Local", "LOCAL"),
            cal(5, "Задачи", "Zadachi", "LOCAL"),
            cal(6, "Tasks", "personal@example.com", "com.google"),
        )
        val state = AccountSlotsState(
            slots = listOf(
                AccountSlot(eventCalendarIds = listOf(1), defaultEventCalendarId = 1, taskCalendarId = 2),
                AccountSlot(eventCalendarIds = listOf(3), defaultEventCalendarId = 3, taskCalendarId = 6),
                AccountSlot(eventCalendarIds = listOf(4), defaultEventCalendarId = 4, taskCalendarId = 5),
            ),
            activeIndex = 0,
        )
        assertEquals(listOf(1L), AccountSlots.taskFormCalendars(state, calendars).map { it.id })
        assertEquals(1L, AccountSlots.preferredWriteTaskCalendarId(state, calendars))
        assertEquals(listOf(1L, 2L), AccountSlots.taskCalendarsForActive(state, calendars).map { it.id })
        val google = AccountSlots.setActive(state, 1)
        assertEquals(listOf(6L), AccountSlots.taskFormCalendars(google, calendars).map { it.id })
        assertEquals(6L, AccountSlots.preferredWriteTaskCalendarId(google, calendars))
        val local = AccountSlots.setActive(state, 2)
        assertEquals(listOf(5L), AccountSlots.taskFormCalendars(local, calendars).map { it.id })
        assertEquals(5L, AccountSlots.preferredWriteTaskCalendarId(local, calendars))
    }

    @Test
    fun firstEventCalendarBecomesDefault() {
        val added = AccountSlots.toggleEventCalendar(AccountSlotsState(), 0, 4)
        assertEquals(listOf(4L), added.slot(0).eventCalendarIds)
        assertEquals(4L, added.slot(0).defaultEventCalendarId)
        val second = AccountSlots.toggleEventCalendar(added, 0, 8)
        assertEquals(4L, second.slot(0).defaultEventCalendarId)
        assertEquals(listOf(4L, 8L), second.slot(0).eventCalendarIds)
    }

    @Test
    fun normalize_dropsMissingAndRepairsActive() {
        val calendars = listOf(
            cal(4, "Локальный", "Local", "LOCAL"),
            cal(5, "Задачи", "Zadachi", "LOCAL"),
        )
        val dirty = AccountSlotsState(
            slots = listOf(
                AccountSlot(eventCalendarIds = listOf(99), defaultEventCalendarId = 99, taskCalendarId = 5),
                AccountSlot(eventCalendarIds = listOf(4), defaultEventCalendarId = 4),
                AccountSlot(),
            ),
            activeIndex = 0,
        )
        val clean = AccountSlots.normalize(dirty, calendars)
        assertTrue(clean.slot(0).eventCalendarIds.isEmpty())
        assertEquals(listOf(4L), clean.slot(1).eventCalendarIds)
        assertEquals(5L, clean.slot(0).taskCalendarId)
        assertEquals(1, clean.activeIndex)
        assertFalse(clean.slot(0).isConfigured)
    }

    @Test
    fun normalize_autoAssignsNewAccountToFreeSlot() {
        // Первый запуск: Google + локальный заняли слоты, DAVx5 ещё не настроен
        val initialCalendars = listOf(
            cal(1, "Личное", "personal@example.com", "com.google", primary = true),
            cal(2, "Локальный", "Local", "LOCAL"),
        )
        val seeded = AccountSlots.seed(initialCalendars)
        assertEquals(listOf(1L), seeded.slot(0).eventCalendarIds)
        assertEquals(listOf(2L), seeded.slot(1).eventCalendarIds)

        // DAVx5 появился после первого запуска — normalize должен выделить ему свободный слот
        val withDavx = initialCalendars + listOf(
            cal(7, "CalDAV Work", "work@nextcloud.local", "bitfire.at.davdroid", primary = true),
            cal(8, "Задачи", "work@nextcloud.local", "bitfire.at.davdroid"),
        )
        val updated = AccountSlots.normalize(seeded, withDavx)
        val davxSlot = updated.slots.firstOrNull { 7L in it.eventCalendarIds }
        assertTrue("DAVx5 calendar must land in a free slot", davxSlot != null)
        assertEquals(7L, davxSlot!!.defaultEventCalendarId)
        assertEquals(8L, davxSlot.taskCalendarId)
        // Прежние слоты не тронуты
        assertEquals(listOf(1L), updated.slot(0).eventCalendarIds)
        assertEquals(listOf(2L), updated.slot(1).eventCalendarIds)
        // Активный слот сохраняется
        assertEquals(seeded.activeIndex, updated.activeIndex)
    }

    @Test
    fun eventFilterIds_keepsStrictActiveIsolationWhenUnassignedExist() {
        // Все 10 слотов заняты, 11-й аккаунт не влез — в активный фильтр не подмешиваем
        val slots = (1..ACCOUNT_SLOT_COUNT).map { i ->
            AccountSlot(
                eventCalendarIds = listOf(i.toLong()),
                defaultEventCalendarId = i.toLong(),
                accountName = "acc$i@example.com",
                accountType = "com.google",
            )
        }
        val state = AccountSlotsState(slots, activeIndex = 0)
        val calendars = slots.mapIndexed { i, slot ->
            cal(slot.eventCalendarIds.first(), "Cal$i", "acc${i + 1}@example.com", "com.google", primary = true)
        } + listOf(
            cal(77, "DAVx5", "dav@nc.local", "bitfire.at.davdroid", primary = true),
        )
        assertEquals(setOf(1L), AccountSlots.eventFilterIds(state, calendars))
        val unassigned = AccountSlots.unassignedAccountGroups(state, calendars)
        assertEquals(1, unassigned.size)
        assertEquals("dav@nc.local", unassigned.first().accountName)
        assertEquals(CalendarAccountKind.CalDav, unassigned.first().kind)
    }

    @Test
    fun eventFilterIds_activeOnlyWhenAllCalendarsSlotted() {
        val state = AccountSlotsState(
            slots = listOf(
                AccountSlot(eventCalendarIds = listOf(1, 2), defaultEventCalendarId = 1),
            ),
            activeIndex = 0,
        )
        val calendars = listOf(cal(1, "A", "a@x.com", "com.google"), cal(2, "B", "b@x.com", "com.google"))
        assertEquals(setOf(1L, 2L), AccountSlots.eventFilterIds(state, calendars))
        assertEquals(setOf(1L, 2L), AccountSlots.eventFilterIds(state))
    }

    @Test
    fun calendarAccountKind_detectsDavx() {
        assertEquals(CalendarAccountKind.CalDav, AccountSlots.calendarAccountKind("bitfire.at.davdroid"))
        assertEquals(CalendarAccountKind.CalDav, AccountSlots.calendarAccountKind("at.bitfire.davx5"))
        assertEquals(CalendarAccountKind.Google, AccountSlots.calendarAccountKind("com.google"))
        assertEquals(CalendarAccountKind.Local, AccountSlots.calendarAccountKind("LOCAL"))
    }

    @Test
    fun normalize_refillsSlotByAccountIdentityAfterIdRemap() {
        val dirty = AccountSlotsState(
            slots = listOf(
                AccountSlot(
                    eventCalendarIds = listOf(10),
                    defaultEventCalendarId = 10,
                    taskCalendarId = 11,
                    accountName = "work@example.com",
                    accountType = "com.google.android.gm.exchange",
                ),
                AccountSlot(
                    eventCalendarIds = listOf(20),
                    defaultEventCalendarId = 20,
                    accountName = "personal@example.com",
                    accountType = "com.google",
                ),
                AccountSlot(),
            ),
            activeIndex = 0,
        )
        val remapped = listOf(
            cal(30, "Work", "work@example.com", "com.google.android.gm.exchange", primary = true),
            cal(31, "Tasks", "work@example.com", "com.google.android.gm.exchange"),
            cal(20, "Личное", "personal@example.com", "com.google"),
        )
        val clean = AccountSlots.normalize(dirty, remapped)
        assertEquals(listOf(30L), clean.slot(0).eventCalendarIds)
        assertEquals(30L, clean.slot(0).defaultEventCalendarId)
        assertEquals(31L, clean.slot(0).taskCalendarId)
        assertEquals(0, clean.activeIndex)
        assertEquals(setOf(30L), AccountSlots.eventFilterIds(clean))
        assertEquals(listOf(20L), clean.slot(1).eventCalendarIds)
    }

    @Test
    fun normalize_keepsReservedEmptySlotForMissingAccount() {
        val reserved = AccountSlotsState(
            slots = listOf(
                AccountSlot(
                    accountName = "gone@example.com",
                    accountType = "com.google",
                ),
                AccountSlot(
                    eventCalendarIds = listOf(4),
                    defaultEventCalendarId = 4,
                    accountName = "Local",
                    accountType = "LOCAL",
                ),
            ),
            activeIndex = 1,
        )
        val calendars = listOf(cal(4, "Локальный", "Local", "LOCAL"))
        val clean = AccountSlots.normalize(reserved, calendars)
        assertTrue(clean.slot(0).eventCalendarIds.isEmpty())
        assertEquals("gone@example.com", clean.slot(0).accountName)
        assertTrue(clean.slot(0).isReserved)
        // Новый аккаунт не занимает reserved-слот
        val withNew = calendars + cal(9, "DAVx", "dav@nc.local", "bitfire.at.davdroid", primary = true)
        val after = AccountSlots.normalize(clean, withNew)
        assertEquals("gone@example.com", after.slot(0).accountName)
        assertTrue(after.slot(0).eventCalendarIds.isEmpty())
        val davxSlotIndex = after.slots.indexOfFirst { 9L in it.eventCalendarIds }
        assertTrue(davxSlotIndex > 0)
    }

    private fun cal(
        id: Long,
        name: String,
        account: String,
        type: String,
        primary: Boolean = false,
    ) = WritableCalendar(
        id = id,
        displayName = name,
        accountName = account,
        accountType = type,
        color = null,
        isPrimary = primary,
        accessLevel = 700,
    )
}
