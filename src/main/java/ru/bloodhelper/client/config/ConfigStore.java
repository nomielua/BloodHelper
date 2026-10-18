package ru.bloodhelper.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import org.slf4j.LoggerFactory;

public final class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path directory;
    private static ClientConfig current = new ClientConfig();
    private ConfigStore() {}
    public static void init(Path configDirectory) {
        current = new ClientConfig();
        directory = configDirectory.resolve("bloodhelper");
        Path path = directory.resolve("client.json");
        if (Files.isRegularFile(path)) {
            try { current = read(path); }
            catch (Exception error) {
                LoggerFactory.getLogger("BloodHelper").warn("Cannot read config; preserving it as a backup", error);
                try { Files.copy(path, directory.resolve("client-invalid-" + System.currentTimeMillis() + ".json")); }
                catch (IOException ignored) {}
            }
        }
        current.sanitize();
    }
    public static ClientConfig get() { return current; }
    public static synchronized void save() {
        current.sanitize();
        try { write(directory.resolve("client.json"), current); }
        catch (IOException error) { LoggerFactory.getLogger("BloodHelper").error("Could not save settings", error); }
    }
    public static synchronized void saveProfile(String name) throws IOException {
        current.sanitize();
        write(profile(name), current);
    }
    public static synchronized boolean loadProfile(String name) {
        try { current = read(profile(name)); save(); return true; }
        catch (Exception error) { return false; }
    }
    public static synchronized boolean deleteProfile(String name) {
        try { return Files.deleteIfExists(profile(name)); }
        catch (Exception error) { return false; }
    }
    public static List<String> profiles() {
        Path path = directory.resolve("profiles");
        if (!Files.isDirectory(path)) return List.of();
        try (var stream = Files.list(path)) {
            return stream.filter(Files::isRegularFile).map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json")).map(n -> n.substring(0, n.length() - 5)).sorted().toList();
        } catch (IOException error) { return List.of(); }
    }
    private static Path profile(String name) {
        if (name == null || !name.matches("[\\p{L}\\p{N}_ -]{1,40}") || name.isBlank())
            throw new IllegalArgumentException("Use 1–40 letters, digits, spaces, _ or -");
        return directory.resolve("profiles").resolve(name.trim() + ".json");
    }
    private static ClientConfig read(Path path) throws IOException {
        ClientConfig result = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), ClientConfig.class);
        if (result == null) throw new IOException("Empty config");
        result.sanitize(); return result;
    }
    private static void write(Path path, ClientConfig value) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "settings-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(value), StandardCharsets.UTF_8);
            try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
