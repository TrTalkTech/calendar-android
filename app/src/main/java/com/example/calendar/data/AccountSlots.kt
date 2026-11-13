package com.example.calendar.data

const val ACCOUNT_SLOT_COUNT = 3

enum class CalendarAccountKind {
    Exchange,
    Google,
    Local,
    Other,
}

data class CalendarAccountGroup(
    val kind: CalendarAccountKind,
    val accountName: String,
    val calendars: List<WritableCalendar>,
)

data class AccountSlot(
    val eventCalendarIds: List<Long> = emptyList(),
    val defaultEventCalendarId: Long? = null,
    val taskCalendarId: Long? = null,
    /** Стабильный ключ аккаунта: переживает смену provider `_ID` после remove/add. */
    val accountName: String? = null,
    val accountType: String? = null,
) {
    val isConfigured: Boolean get() = eventCalendarIds.isNotEmpty()

    val accountKey: String?
        get() {
            val name = accountName?.trim().orEmpty()
            val type = accountType?.trim().orEmpty()
            if (name.isEmpty() && type.isEmpty()) return null
            return "$type\u0000$name"
        }
}

data class AccountSlotsState(
    val slots: List<AccountSlot> = List(ACCOUNT_SLOT_COUNT) { AccountSlot() },
    val activeIndex: Int = 0,
) {
    fun slot(index: Int): AccountSlot =
        slots.getOrElse(index.coerceIn(0, ACCOUNT_SLOT_COUNT - 1)) { AccountSlot() }

    fun active(): AccountSlot = slot(activeIndex)
}

object AccountSlots {

    fun calendarAccountKind(accountType: String?): CalendarAccountKind {
        val type = accountType.orEmpty().lowercase()
        return when {
            type.contains("exchange") ||
                type.contains("eas") ||
                type.contains("outlook") ||
                type.contains("office") ||
                type.contains("microsoft") -> CalendarAccountKind.Exchange
            type.contains("google") -> CalendarAccountKind.Google
            type.isBlank() || type.contains("local") -> CalendarAccountKind.Local
            else -> CalendarAccountKind.Other
        }
    }

    fun groupedByAccount(calendars: List<WritableCalendar>): List<CalendarAccountGroup> {
        return calendars
            .groupBy { "${it.accountType.orEmpty()}\u0000${it.accountName.orEmpty()}" }
            .map { (_, items) ->
                val first = items.first()
                CalendarAccountGroup(
                    kind = calendarAccountKind(first.accountType),
                    accountName = first.accountName.orEmpty(),
                    calendars = items.sortedWith(
                        compareByDescending<WritableCalendar> { it.isPrimary }
                            .thenBy { it.displayName.lowercase() },
                    ),
                )
            }
            .sortedWith(
                compareBy<CalendarAccountGroup> { it.kind.ordinal }
                    .thenBy { it.accountName.lowercase() },
            )
    }

    fun usedIds(state: AccountSlotsState, exceptSlot: Int? = null): Set<Long> {
        val result = mutableSetOf<Long>()
        state.slots.forEachIndexed { index, slot ->
            if (exceptSlot != null && index == exceptSlot) return@forEachIndexed
            result += slot.eventCalendarIds
            slot.taskCalendarId?.let { result += it }
        }
        return result
    }

    fun eventFilterIds(state: AccountSlotsState): Set<Long>? {
        if (state.slots.none { it.isConfigured }) return null
        val ids = state.active().eventCalendarIds.toSet()
        return ids
    }

    fun isExchangeSlot(slot: AccountSlot, calendars: List<WritableCalendar>): Boolean {
        val byId = calendars.associateBy { it.id }
        val ids = slot.eventCalendarIds + listOfNotNull(slot.defaultEventCalendarId, slot.taskCalendarId)
        return ids.any { id -> calendarAccountKind(byId[id]?.accountType) == CalendarAccountKind.Exchange }
    }

    fun exchangeTaskWriteCalendar(
        slot: AccountSlot,
        calendars: List<WritableCalendar>,
    ): WritableCalendar? {
        val byId = calendars.associateBy { it.id }
        return slot.defaultEventCalendarId?.let { byId[it] }
            ?: slot.eventCalendarIds.firstOrNull()?.let { byId[it] }
    }

    /** Куда писать и что показать в форме: у Exchange — основной календарь (его видит Outlook). */
    fun taskFormCalendars(
        state: AccountSlotsState,
        calendars: List<WritableCalendar>,
    ): List<WritableCalendar> {
        val slot = state.active()
        if (!slot.isConfigured) return emptyList()
        if (isExchangeSlot(slot, calendars)) {
            return listOfNotNull(exchangeTaskWriteCalendar(slot, calendars))
        }
        val byId = calendars.associateBy { it.id }
        return listOfNotNull(slot.taskCalendarId?.let { byId[it] })
    }

    /** Список задач: форма + папка Tasks карточки, если она отдельно. */
    fun taskCalendarsForActive(
        state: AccountSlotsState,
        calendars: List<WritableCalendar>,
    ): List<WritableCalendar> {
        val result = linkedMapOf<Long, WritableCalendar>()
        taskFormCalendars(state, calendars).forEach { result[it.id] = it }
        val byId = calendars.associateBy { it.id }
        state.active().taskCalendarId?.let { byId[it] }?.let { result[it.id] = it }
        return result.values.toList()
    }

    fun preferredWriteTaskCalendarId(
        state: AccountSlotsState,
        calendars: List<WritableCalendar>,
    ): Long? = taskFormCalendars(state, calendars).firstOrNull()?.id

    fun exchangeTaskWriteCalendarIds(
        state: AccountSlotsState,
        calendars: List<WritableCalendar>,
    ): Set<Long> = state.slots.mapNotNull { slot ->
        if (!isExchangeSlot(slot, calendars)) null
        else exchangeTaskWriteCalendar(slot, calendars)?.id
    }.toSet()

    fun seed(calendars: List<WritableCalendar>): AccountSlotsState {
        val eventGroups = groupedByAccount(calendars)
            .filter { group -> group.calendars.eventCalendars().isNotEmpty() }
        val slots = MutableList(ACCOUNT_SLOT_COUNT) { AccountSlot() }
        val used = mutableSetOf<Long>()
        eventGroups.take(ACCOUNT_SLOT_COUNT).forEachIndexed { index, group ->
            val events = group.calendars.eventCalendars()
            val task = group.calendars.taskCalendars()
                .firstOrNull { it.canModifyEvents }
                ?: group.calendars.taskCalendars().firstOrNull()
            events.forEach { used += it.id }
            task?.let { used += it.id }
            val defaultId = events.firstOrNull { it.isPrimary }?.id ?: events.firstOrNull()?.id
            slots[index] = AccountSlot(
                eventCalendarIds = events.map { it.id },
                defaultEventCalendarId = defaultId,
                taskCalendarId = task?.id,
                accountName = group.accountName.takeIf { it.isNotEmpty() },
                accountType = group.calendars.firstOrNull()?.accountType,
            )
        }
        val byId = calendars.associateBy { it.id }
        calendars.taskCalendars().filter { it.id !in used }.forEach { leftover ->
            val leftoverKind = calendarAccountKind(leftover.accountType)
            val sameKind = slots.indexOfFirst { slot ->
                slot.isConfigured &&
                    slot.taskCalendarId == null &&
                    slot.eventCalendarIds.any { id ->
                        calendarAccountKind(byId[id]?.accountType) == leftoverKind
                    }
            }
            val index = if (sameKind >= 0) {
                sameKind
            } else {
                slots.indexOfFirst { it.isConfigured && it.taskCalendarId == null }
            }
            if (index >= 0) {
                slots[index] = slots[index].copy(taskCalendarId = leftover.id)
                used += leftover.id
            }
        }
        val active = slots.indexOfFirst { it.isConfigured }.takeIf { it >= 0 } ?: 0
        return normalize(AccountSlotsState(slots, active), calendars)
    }

    fun normalize(state: AccountSlotsState, calendars: List<WritableCalendar>): AccountSlotsState {
        val available = calendars.map { it.id }.toSet()
        val eventIds = calendars.eventCalendars().map { it.id }.toSet()
        val taskIds = calendars.taskCalendars().map { it.id }.toSet()
        val byId = calendars.associateBy { it.id }
        val groupsByKey = groupedByAccount(calendars).associateBy { groupAccountKey(it) }
        val padded = (state.slots + List(ACCOUNT_SLOT_COUNT) { AccountSlot() }).take(ACCOUNT_SLOT_COUNT)

        // 1) Снять мёртвые id, сохранить/восстановить account identity.
        val dropped = padded.map { slot ->
            val identity = resolveIdentity(slot, byId)
            val events = slot.eventCalendarIds.filter { id ->
                id in available && id in eventIds
            }
            val task = slot.taskCalendarId?.takeIf { id ->
                id in available && id in taskIds
            }
            val default = when {
                slot.defaultEventCalendarId in events -> slot.defaultEventCalendarId
                else -> events.firstOrNull()
            }
            AccountSlot(
                eventCalendarIds = events,
                defaultEventCalendarId = default,
                taskCalendarId = task,
                accountName = identity?.first,
                accountType = identity?.second,
            )
        }

        // 2) Пустой слот с identity → заново наполнить календарями этого аккаунта (remove/add).
        val claimedKeys = mutableSetOf<String>()
        val refilled = dropped.map { slot ->
            val key = slot.accountKey
            if (slot.isConfigured) {
                key?.let { claimedKeys += it }
                return@map ensureIdentity(slot, byId)
            }
            if (key == null || key in claimedKeys) return@map slot
            val group = groupsByKey[key] ?: return@map slot
            claimedKeys += key
            refillSlotFromGroup(group)
        }

        // 3) Эксклюзивность id между слотами.
        val seen = mutableSetOf<Long>()
        val exclusive = refilled.map { slot ->
            val events = slot.eventCalendarIds.filter { id -> seen.add(id) }
            val task = slot.taskCalendarId?.takeIf { id -> seen.add(id) }
            val default = when {
                slot.defaultEventCalendarId in events -> slot.defaultEventCalendarId
                else -> events.firstOrNull()
            }
            slot.copy(
                eventCalendarIds = events,
                defaultEventCalendarId = default,
                taskCalendarId = task,
            )
        }

        val configured = exclusive.indexOfFirst { it.isConfigured }
        val active = if (exclusive.getOrNull(state.activeIndex)?.isConfigured == true) {
            state.activeIndex
        } else {
            configured.takeIf { it >= 0 } ?: 0
        }
        return AccountSlotsState(exclusive, active)
    }

    private fun groupAccountKey(group: CalendarAccountGroup): String =
        "${group.calendars.firstOrNull()?.accountType.orEmpty()}\u0000${group.accountName}"

    private fun resolveIdentity(
        slot: AccountSlot,
        byId: Map<Long, WritableCalendar>,
    ): Pair<String, String>? {
        val storedName = slot.accountName?.trim().orEmpty()
        val storedType = slot.accountType?.trim().orEmpty()
        if (storedName.isNotEmpty() || storedType.isNotEmpty()) {
            return storedName to storedType
        }
        val sampleId = slot.eventCalendarIds.firstOrNull()
            ?: slot.defaultEventCalendarId
            ?: slot.taskCalendarId
            ?: return null
        val cal = byId[sampleId] ?: return null
        return cal.accountName.orEmpty() to cal.accountType.orEmpty()
    }

    private fun ensureIdentity(
        slot: AccountSlot,
        byId: Map<Long, WritableCalendar>,
    ): AccountSlot {
        if (slot.accountKey != null) return slot
        val identity = resolveIdentity(slot, byId) ?: return slot
        return slot.copy(accountName = identity.first, accountType = identity.second)
    }

    private fun refillSlotFromGroup(group: CalendarAccountGroup): AccountSlot {
        val events = group.calendars.eventCalendars()
        val task = group.calendars.taskCalendars()
            .firstOrNull { it.canModifyEvents }
            ?: group.calendars.taskCalendars().firstOrNull()
        val defaultId = events.firstOrNull { it.isPrimary }?.id ?: events.firstOrNull()?.id
        return AccountSlot(
            eventCalendarIds = events.map { it.id },
            defaultEventCalendarId = defaultId,
            taskCalendarId = task?.id,
            accountName = group.accountName.takeIf { it.isNotEmpty() },
            accountType = group.calendars.firstOrNull()?.accountType,
        )
    }

    fun setActive(state: AccountSlotsState, index: Int): AccountSlotsState {
        if (index !in 0 until ACCOUNT_SLOT_COUNT) return state
        if (!state.slot(index).isConfigured) return state
        return state.copy(activeIndex = index)
    }

    fun toggleEventCalendar(state: AccountSlotsState, slotIndex: Int, calendarId: Long): AccountSlotsState {
        if (slotIndex !in 0 until ACCOUNT_SLOT_COUNT) return state
        val current = state.slot(slotIndex)
        if (calendarId in current.eventCalendarIds) {
            val events = current.eventCalendarIds.filterNot { it == calendarId }
            val default = when {
                current.defaultEventCalendarId == calendarId -> events.firstOrNull()
                else -> current.defaultEventCalendarId
            }
            return replaceSlot(
                state,
                slotIndex,
                current.copy(eventCalendarIds = events, defaultEventCalendarId = default),
            )
        }
        if (calendarId in usedIds(state, exceptSlot = slotIndex)) {
            val without = removeId(state, calendarId)
            return addEvent(without, slotIndex, calendarId)
        }
        return addEvent(state, slotIndex, calendarId)
    }

    fun setDefaultEventCalendar(state: AccountSlotsState, slotIndex: Int, calendarId: Long): AccountSlotsState {
        val current = state.slot(slotIndex)
        if (calendarId !in current.eventCalendarIds) return state
        return replaceSlot(state, slotIndex, current.copy(defaultEventCalendarId = calendarId))
    }

    fun setTaskCalendar(state: AccountSlotsState, slotIndex: Int, calendarId: Long?): AccountSlotsState {
        if (slotIndex !in 0 until ACCOUNT_SLOT_COUNT) return state
        val current = state.slot(slotIndex)
        if (calendarId == null || calendarId == current.taskCalendarId) {
            return replaceSlot(state, slotIndex, current.copy(taskCalendarId = null))
        }
        val cleared = removeId(state, calendarId)
        return replaceSlot(
            cleared,
            slotIndex,
            cleared.slot(slotIndex).copy(taskCalendarId = calendarId),
        )
    }

    private fun addEvent(state: AccountSlotsState, slotIndex: Int, calendarId: Long): AccountSlotsState {
        val current = state.slot(slotIndex)
        val events = current.eventCalendarIds + calendarId
        val default = current.defaultEventCalendarId ?: calendarId
        return replaceSlot(
            state,
            slotIndex,
            current.copy(eventCalendarIds = events, defaultEventCalendarId = default),
        )
    }

    private fun removeId(state: AccountSlotsState, calendarId: Long): AccountSlotsState {
        val slots = state.slots.map { slot ->
            val events = slot.eventCalendarIds.filterNot { it == calendarId }
            val default = when {
                slot.defaultEventCalendarId == calendarId -> events.firstOrNull()
                else -> slot.defaultEventCalendarId
            }
            val task = slot.taskCalendarId.takeIf { it != calendarId }
            slot.copy(
                eventCalendarIds = events,
                defaultEventCalendarId = default,
                taskCalendarId = task,
            )
        }
        return state.copy(slots = slots)
    }

    private fun replaceSlot(state: AccountSlotsState, index: Int, slot: AccountSlot): AccountSlotsState {
        val slots = state.slots.toMutableList()
        while (slots.size < ACCOUNT_SLOT_COUNT) slots += AccountSlot()
        slots[index] = slot
        val next = state.copy(slots = slots.take(ACCOUNT_SLOT_COUNT))
        return if (next.active().isConfigured) next else {
            val fallback = next.slots.indexOfFirst { it.isConfigured }
            next.copy(activeIndex = fallback.takeIf { it >= 0 } ?: 0)
        }
    }
}
