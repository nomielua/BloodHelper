package ru.bloodhelper.client.hud;


/** Pure session state. Death transitions and message confirmations are intentionally separate. */
final class SessionState {
    record Snapshot(String mode, int kills, int rewardedKills, int deaths, int streak, long playSeconds, boolean killFeedAvailable) {}
    private String identity = "", localName = "", mode = "FFA";
    private int kills, rewardedKills, deaths, streak;
    private long elapsed, lastTick = -1;
    private boolean connected, alive, active, killFeed;

    void tick(String newIdentity, String name, String newMode, boolean aliveNow, boolean enabled, boolean paused, long now) {
        if (!newIdentity.equals(identity) || !newMode.equals(mode)) { clear(); active = false; identity = newIdentity; mode = newMode; }
        boolean nowActive = enabled && !paused;
        if (!connected) { connected = true; alive = aliveNow; lastTick = now; active = false; }
        if (active && nowActive && lastTick >= 0 && now >= lastTick) elapsed += now - lastTick;
        if (active && nowActive && alive && !aliveNow) { deaths++; streak = 0; }
        localName = name;
        alive = aliveNow;
        active = nowActive;
        lastTick = now;
    }
    void disconnect(boolean clear) {
        connected = active = false;
        lastTick = -1;
        if (clear) { clear(); identity = ""; localName = ""; }
    }
    void reset() { clear(); }
    private void clear() {
        kills = rewardedKills = deaths = streak = 0; elapsed = 0; killFeed = false; lastTick = -1;
    }
    boolean confirm(KillMessageTemplate.Kill kill, boolean serverGameMessage, boolean overlay, long now) {
        if ("FFA".equals(mode) || !connected || !active || !serverGameMessage || overlay || kill == null
                || !KillMessageTemplate.isName(kill.killer()) || !KillMessageTemplate.isName(kill.victim())
                || kill.killer().equals(kill.victim())) return false;
        killFeed = true;
        if (!kill.killer().equals(localName)) return false;
        kills++;
        if (alive) streak++;
        return true;
    }
    boolean confirmFfa(FfaKillReceipt receipt, boolean serverGameMessage, boolean overlay, long now) {
        if (!"FFA".equals(mode) || !connected || !active || !serverGameMessage || overlay || receipt == null) return false;
        kills++;
        if (receipt.rewarded()) rewardedKills++;
        killFeed = true;
        return true;
    }
    Snapshot snapshot() { return new Snapshot(mode, kills, rewardedKills, deaths, streak, elapsed / 1_000_000_000L, killFeed); }
}
