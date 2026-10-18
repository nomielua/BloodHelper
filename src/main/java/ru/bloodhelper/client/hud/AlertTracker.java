package ru.bloodhelper.client.hud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure transition and lifetime state. Callers supply a monotonic clock; no game or sound access. */
public final class AlertTracker {
    public record Notice(String key, String title, String body, String icon, long createdAt, long expiresAt) {
        public float remaining(long now) {
            if (expiresAt <= createdAt) return 0;
            return (float)Math.max(0, Math.min(1, (double)(expiresAt - now) / (expiresAt - createdAt)));
        }
    }
    private record Observation(double value, double threshold) {}
    private final Map<String, Observation> observations = new HashMap<>();
    private final List<Notice> notices = new ArrayList<>();

    /** A first observation or a changed threshold establishes a baseline without an alert. */
    public boolean observe(String key, double value, double threshold) {
        if (key == null || !Double.isFinite(value) || !Double.isFinite(threshold)) {
            observations.remove(key);
            return false;
        }
        Observation previous = observations.put(key, new Observation(value, threshold));
        return previous != null && Double.compare(previous.threshold, threshold) == 0
                && previous.value > threshold && value <= threshold && value < previous.value;
    }

    /** Missing effects/equipment and disabled warning groups lose their old baseline. */
    public void retainObservations(Set<String> present) { observations.keySet().retainAll(present); }

    /** Merges a currently visible key without prolonging it. Returns true only for a new card. */
    public boolean push(String key, String title, String body, String icon, long now, long durationMs, int limit) {
        expire(now, limit);
        if (key == null || durationMs <= 0 || limit <= 0) return false;
        for (int i = 0; i < notices.size(); i++) {
            Notice current = notices.get(i);
            if (current.key.equals(key)) {
                notices.set(i, new Notice(key, title, body, icon, current.createdAt, current.expiresAt));
                return false;
            }
        }
        long expires = now > Long.MAX_VALUE - durationMs ? Long.MAX_VALUE : now + durationMs;
        notices.add(new Notice(key, title, body, icon, now, expires));
        trim(limit);
        return true;
    }

    public void expire(long now, int limit) {
        notices.removeIf(notice -> now >= notice.expiresAt);
        trim(limit);
    }

    public List<Notice> snapshot(long now) {
        notices.removeIf(notice -> now >= notice.expiresAt);
        return List.copyOf(notices);
    }

    private void trim(int limit) {
        int boundedLimit = Math.max(0, Math.min(4, limit));
        while (notices.size() > boundedLimit) notices.removeFirst();
    }

    public void clear() { observations.clear(); notices.clear(); }
}
