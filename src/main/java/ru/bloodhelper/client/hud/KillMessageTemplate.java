package ru.bloodhelper.client.hud;

import java.util.Optional;
import java.util.regex.Pattern;

/** Literal, anchored server-message template, never a user-supplied regular expression. */
final class KillMessageTemplate {
    record Kill(String killer, String victim) {}
    private static final String NAME = "[A-Za-z0-9_]{1,16}";
    private final Pattern pattern;
    private KillMessageTemplate(Pattern pattern) { this.pattern = pattern; }
    static KillMessageTemplate compile(String template) {
        if (template == null || template.length() > 256 || template.isBlank()) return new KillMessageTemplate(null);
        if (occurrences(template, "{killer}") != 1 || occurrences(template, "{victim}") != 1) return new KillMessageTemplate(null);
        StringBuilder expression = new StringBuilder("\\A");
        for (int pos = 0; pos < template.length();) {
            int killer = template.indexOf("{killer}", pos), victim = template.indexOf("{victim}", pos);
            int next = killer < 0 ? victim : victim < 0 ? killer : Math.min(killer, victim);
            if (next < 0) { expression.append(Pattern.quote(template.substring(pos))); break; }
            expression.append(Pattern.quote(template.substring(pos, next)));
            boolean isKiller = next == killer;
            expression.append("(?<").append(isKiller ? "killer" : "victim").append('>').append(NAME).append(')');
            pos = next + (isKiller ? "{killer}".length() : "{victim}".length());
        }
        return new KillMessageTemplate(Pattern.compile(expression.append("\\z").toString()));
    }
    Optional<Kill> parse(String message) {
        if (pattern == null || message == null || message.length() > 512) return Optional.empty();
        var match = pattern.matcher(message);
        if (!match.matches()) return Optional.empty();
        return Optional.of(new Kill(match.group("killer"), match.group("victim")));
    }
    static boolean isName(String value) { return value != null && value.matches(NAME); }
    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int from = 0; (from = text.indexOf(needle, from)) >= 0; from += needle.length()) count++;
        return count;
    }
}
