package ru.bloodhelper.client.hud;

import java.util.Optional;
import java.util.regex.Pattern;

/** Matches only the two Bloodstone FFA receipts supplied by the user, not general coin messages. */
record FfaKillReceipt(boolean rewarded, String fingerprint) {
    private static final Pattern FORMAT = Pattern.compile("(?i)§[0-9a-fk-orx]");
    private static final Pattern TIMESTAMP = Pattern.compile("^\\[(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]]\\s*");
    private static final Pattern REWARD = Pattern.compile("Ты получил [1-9][0-9]{0,8} монет за убийство игрока "
            + "[\\p{Co}\\p{So}\\p{Sk} ]*[A-Za-z0-9_]{1,16} с множителем x[0-9]{1,3}(?:[.,][0-9]{1,3})?[.!]?");
    private static final Pattern NO_REWARD = Pattern.compile("За этого игрока пока нельзя получить монеты\\. Подожди [0-9]{1,7} секунд[.!]?");
    static Optional<FfaKillReceipt> parse(String text) {
        if (text == null || text.length() > 768) return Optional.empty();
        String plain = FORMAT.matcher(text).replaceAll("").replaceAll("[\\p{Cf}]", "")
                .replaceAll("[\\s\\p{Zs}]+", " ").strip();
        plain = TIMESTAMP.matcher(plain).replaceFirst("");
        if (REWARD.matcher(plain).matches()) return Optional.of(new FfaKillReceipt(true, plain));
        if (NO_REWARD.matcher(plain).matches()) return Optional.of(new FfaKillReceipt(false, plain));
        return Optional.empty();
    }
}
