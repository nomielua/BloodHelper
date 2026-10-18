package ru.bloodhelper.client.hud;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;

/** Suppresses re-delivery of one message object, never separate kills with identical text. */
final class MessageDeliveryGuard {
    private final IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
    private final ArrayDeque<Object> order = new ArrayDeque<>();
    boolean accept(Object message) {
        if (message == null || seen.put(message, Boolean.TRUE) != null) return false;
        order.addLast(message);
        if (order.size() > 128) seen.remove(order.removeFirst());
        return true;
    }
    void clear() { seen.clear(); order.clear(); }
}
