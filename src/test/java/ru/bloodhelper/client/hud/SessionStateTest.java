package ru.bloodhelper.client.hud;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SessionStateTest {
    private static final String REWARD = "[21:39:30] Ты получил 18 монет за убийство игрока \ue001HumsterMangok с множителем x1.5";
    private static final String DENIED = "[21:39:40] За этого игрока пока нельзя получить монеты. Подожди 340 секунд";
    private static void tick(SessionState s, boolean alive, long seconds) { s.tick("bloodstone|uuid", "Me", "SMP", alive, true, false, seconds * 1_000_000_000); }
    private static void ffa(SessionState s, long seconds) { s.tick("bloodstone|uuid", "Me", "FFA", true, true, false, seconds * 1_000_000_000); }

    @Test void deathIsOnePerAliveToDeadTransitionAndRespawnStartsAnotherLife() {
        var s = new SessionState();
        tick(s, true, 0); tick(s, false, 1); tick(s, false, 2); tick(s, false, 3);
        assertEquals(1, s.snapshot().deaths());
        tick(s, true, 4); tick(s, false, 5);
        assertEquals(2, s.snapshot().deaths());
        s.disconnect(false); tick(s, false, 10);
        assertEquals(2, s.snapshot().deaths()); // reconnect while dead must not invent a death
    }
    @Test void onlyServerConfirmedKillsCountAndFastSeparateKillsArePreserved() {
        var s = new SessionState(); tick(s, true, 0);
        var kill = new KillMessageTemplate.Kill("Me", "Victim");
        assertFalse(s.snapshot().killFeedAvailable());
        assertFalse(s.confirm(kill, false, false, 1));
        assertFalse(s.confirm(kill, true, true, 1));
        assertFalse(s.snapshot().killFeedAvailable());
        assertTrue(s.confirm(kill, true, false, 1));
        assertTrue(s.confirm(kill, true, false, 1_000_000_001));
        assertTrue(s.confirm(kill, true, false, 2_000_000_001L));
        assertFalse(s.confirm(new KillMessageTemplate.Kill("Me", "Me"), true, false, 3_000_000_000L));
        assertFalse(s.confirm(new KillMessageTemplate.Kill("Someone", "Victim"), true, false, 3_000_000_000L));
        assertEquals(3, s.snapshot().kills()); assertEquals(3, s.snapshot().streak());
        tick(s, false, 4); assertEquals(0, s.snapshot().streak());
        assertEquals(3, s.snapshot().kills());
    }
    @Test void literalTemplatesCannotBeRegexOrContainPrefixSuffixSpoofs() {
        var template = KillMessageTemplate.compile("[PvP] {killer} убил {victim} (+1).");
        var kill = template.parse("[PvP] Me убил Victim (+1).").orElseThrow();
        assertEquals("Me", kill.killer()); assertEquals("Victim", kill.victim());
        assertTrue(template.parse("<Player> [PvP] Me убил Victim (+1).").isEmpty());
        assertTrue(template.parse("[PvP] Me убил Victim (+1). extra").isEmpty());
        assertTrue(template.parse("PvP Me убил Victim +1Z").isEmpty());
        assertTrue(KillMessageTemplate.compile("{killer} {killer} {victim}").parse("Me Me Victim").isEmpty());
        assertTrue(KillMessageTemplate.compile("{killer} killed {victim}").parse("Me killed PlayerWithANameOver16").isEmpty());
    }
    @Test void sessionTimePausesWhileDisabledPausedAndDisconnectedAndNeverMixesIdentity() {
        var s = new SessionState(); tick(s, true, 0); tick(s, true, 5);
        assertEquals(5, s.snapshot().playSeconds());
        s.tick("bloodstone|uuid", "Me", "SMP", true, false, false, 6_000_000_000L);
        s.tick("bloodstone|uuid", "Me", "SMP", false, false, false, 9_000_000_000L);
        tick(s, false, 10); tick(s, true, 12);
        assertEquals(7, s.snapshot().playSeconds()); assertEquals(0, s.snapshot().deaths());
        s.tick("bloodstone|uuid", "Me", "SMP", true, true, true, 13_000_000_000L);
        s.tick("bloodstone|uuid", "Me", "SMP", true, true, true, 20_000_000_000L);
        tick(s, true, 21); tick(s, true, 22);
        assertEquals(8, s.snapshot().playSeconds());
        s.disconnect(false); tick(s, true, 30); tick(s, true, 32);
        assertEquals(10, s.snapshot().playSeconds());
        s.tick("other-server|uuid", "Me", "SMP", true, true, false, 40_000_000_000L);
        assertEquals(0, s.snapshot().playSeconds());
        s.confirm(new KillMessageTemplate.Kill("Me", "Victim"), true, false, 41_000_000_000L);
        s.tick("other-server|different-uuid", "Me", "SMP", true, true, false, 42_000_000_000L);
        assertEquals(0, s.snapshot().kills());
        s.reset(); assertFalse(s.snapshot().killFeedAvailable());
        s.disconnect(true); assertEquals(0, s.snapshot().playSeconds());
    }
    @Test void distinctIdenticalMessagesAreNotLostButSameDeliveryObjectIsIgnored() {
        var guard = new MessageDeliveryGuard();
        var first = new String(DENIED); var second = new String(DENIED);
        assertTrue(guard.accept(first)); assertFalse(guard.accept(first));
        assertTrue(guard.accept(second)); // two targets may have exactly the same reward cooldown
        guard.clear(); assertTrue(guard.accept(first));
        var state = new SessionState(); ffa(state, 0);
        var receipt = FfaKillReceipt.parse(DENIED).orElseThrow();
        assertTrue(state.confirmFfa(receipt, true, false, 1));
        assertTrue(state.confirmFfa(receipt, true, false, 2));
        assertEquals(2, state.snapshot().kills()); assertEquals(0, state.snapshot().rewardedKills());
    }
    @Test void ffaMatchesOnlyVerifiedRewardAndDenialPhrases() {
        assertTrue(FfaKillReceipt.parse(REWARD).orElseThrow().rewarded());
        assertTrue(FfaKillReceipt.parse(REWARD.replace("\ue001", "\ue001 ")).orElseThrow().rewarded());
        assertFalse(FfaKillReceipt.parse(DENIED).orElseThrow().rewarded());
        assertTrue(FfaKillReceipt.parse("§a" + REWARD.replace("HumsterMangok", "§6HumsterMangok")).isPresent());
        assertTrue(FfaKillReceipt.parse(REWARD.replace("18 монет", "36 монет").replace("x1.5", "x3.0")).isPresent());
        assertTrue(FfaKillReceipt.parse(REWARD.replace(" с множителем x1.5", "")).isEmpty());
        assertTrue(FfaKillReceipt.parse("Ты получил 18 монет за покупку предмета").isEmpty());
        assertTrue(FfaKillReceipt.parse("<Player> " + REWARD).isEmpty());
        assertTrue(FfaKillReceipt.parse(DENIED + " extra").isEmpty());
        assertTrue(FfaKillReceipt.parse("[25:99:30] " + DENIED).isEmpty());
    }
    @Test void ffaSeparatesTotalAndRewardedAndIgnoresVanillaAndChat() {
        var s = new SessionState(); ffa(s, 0);
        var reward = FfaKillReceipt.parse(REWARD).orElseThrow();
        var denied = FfaKillReceipt.parse(DENIED).orElseThrow();
        assertFalse(s.confirmFfa(reward, false, false, 0));
        assertFalse(s.confirmFfa(reward, true, true, 0));
        assertTrue(s.confirmFfa(reward, true, false, 1));
        assertTrue(s.confirmFfa(reward, true, false, 1_000_000_001));
        assertTrue(s.confirmFfa(denied, true, false, 10_000_000_000L));
        assertFalse(s.confirm(new KillMessageTemplate.Kill("Me", "Victim"), true, false, 11_000_000_000L));
        assertEquals(3, s.snapshot().kills()); assertEquals(2, s.snapshot().rewardedKills());
        tick(s, true, 12); // mode switch resets instead of mixing FFA receipts with SMP deaths
        assertEquals(0, s.snapshot().kills());
        assertFalse(s.confirmFfa(reward, true, false, 13_000_000_000L));
        assertTrue(s.confirm(new KillMessageTemplate.Kill("Me", "Victim"), true, false, 14_000_000_000L));
        s.tick("bloodstone|uuid", "Me", "MEETUPS", true, true, false, 15_000_000_000L);
        assertEquals(0, s.snapshot().kills());
        assertFalse(s.confirmFfa(denied, true, false, 16_000_000_000L));
    }
}
