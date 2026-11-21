package com.blazify.buds.ui

import com.blazify.buds.core.model.Battery
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.ListeningMode
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RulesTest {
    @Test
    fun everyKindOfRuleIsWrittenAndReadBackTheSame() {
        val rules = listOf(
            Rule("a", Trigger.Connect, Action.PutScene("focus")),
            Rule("b", Trigger.At(22 * 60 + 5, WEEKDAYS), Action.PutMode(ListeningMode.Transparency), on = false),
            Rule("c", Trigger.BatteryBelow(20), Action.PutMode(ListeningMode.Normal)),
        )
        rules.forEach { assertEquals(it, Rule.parse(it.line())) }
        // Lines from a newer or broken version are skipped, not crashed on.
        assertEquals(null, Rule.parse("x|1|Q|S,focus"))
        assertEquals(null, Rule.parse("x|1|C|S,nosuchscene"))
    }

    @Test
    fun theListIsKeptAndPausingHoldsEveryRule() {
        val store = MapSettings()
        val book = RuleBook(store)
        book.save(Rule("a", Trigger.Connect, Action.PutScene("focus")))
        assertEquals(1, RuleBook(store).rules.size)
        book.pause(true)
        assertTrue(RuleBook(store).forConnect(1_000_000).isEmpty())
    }

    @Test
    fun aConnectRuleRunsOnceEvenWhenThePhoneAndTheAppBothHearIt() {
        val book = RuleBook(MapSettings())
        book.save(Rule("a", Trigger.Connect, Action.PutScene("focus")))
        assertEquals(1, book.forConnect(1_000_000).size)
        assertEquals(0, book.forConnect(1_030_000).size)
        // A real reconnect later runs it again.
        assertEquals(1, book.forConnect(1_200_000).size)
    }

    @Test
    fun aTimeRuleRunsOnItsDaysOnceADayEvenALittleLate() {
        val book = RuleBook(MapSettings())
        book.save(Rule("t", Trigger.At(22 * 60, WEEKDAYS), Action.PutScene("relax")))
        assertEquals(0, book.dueAt(LocalMoment(1, 21 * 60 + 59, "2026-11-23")).size)
        assertEquals(1, book.dueAt(LocalMoment(1, 22 * 60 + 3, "2026-11-23")).size)
        assertEquals(0, book.dueAt(LocalMoment(1, 22 * 60 + 4, "2026-11-23")).size)
        // Too late, or the weekend: nothing.
        assertEquals(0, book.dueAt(LocalMoment(2, 23 * 60, "2026-11-24")).size)
        assertEquals(0, book.dueAt(LocalMoment(6, 22 * 60, "2026-11-28")).size)
        assertEquals(1, book.dueAt(LocalMoment(2, 22 * 60, "2026-11-24")).size)
    }

    @Test
    fun aBatteryRuleRunsOncePerRunDownAndAgainAfterACharge() {
        val book = RuleBook(MapSettings())
        book.save(Rule("b", Trigger.BatteryBelow(20), Action.PutMode(ListeningMode.Normal)))
        fun state(l: Int, r: Int, charging: Boolean = false) =
            BudsState("Buds", "x", left = Battery(l, charging), right = Battery(r, charging))
        assertEquals(0, book.forBattery(state(60, 40)).size)
        assertEquals(1, book.forBattery(state(20, 40)).size)
        assertEquals(0, book.forBattery(state(20, 40)).size)
        assertEquals(0, book.forBattery(state(0, 0)).size)
        book.forBattery(state(30, 30, charging = true))
        assertEquals(1, book.forBattery(state(10, 30)).size)
    }

    @Test
    fun aSceneBecomesItsChanges() {
        val changes = Action.PutScene("talk").changes()
        assertTrue(changes.isNotEmpty())
        assertEquals(listOf(Change.Mode(ListeningMode.Normal)), Action.PutMode(ListeningMode.Normal).changes())
    }

    @Test
    fun rulesReadAsPlainSentences() {
        assertEquals("At 08:30 on weekdays", Trigger.At(8 * 60 + 30, WEEKDAYS).words())
        assertEquals("At 22:00", Trigger.At(22 * 60).words())
        assertEquals("Relax scene", Action.PutScene("relax").words())
    }
}
