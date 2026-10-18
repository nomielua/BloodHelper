package ru.bloodhelper.client.music;

import java.util.concurrent.TimeUnit;

/** Explicit diagnostic only. Never invokes playback controls and never prints titles or artists. */
public final class MediaLifecycleReadOnlyProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !"--windows-read-only".equals(args[0])) {
            throw new IllegalArgumentException("Explicit --windows-read-only required; this reads the current Windows media session");
        }
        try (MediaService service = new MediaService()) {
            if (!service.isSupported()) throw new IllegalStateException("Run this probe with Windows Java");
            service.setEnabled(true);
            awaitObservation(service);
            MediaSnapshot first = service.snapshot();
            System.out.println("first status=" + service.status() + " available=" + first.available()
                    + " playing=" + first.playing() + " artwork=" + (first.artwork() != null));
            service.setEnabled(false);
            check(!service.isEnabled() && !service.snapshot().available() && "stopped".equals(service.status()), "Disable must clear state immediately");
            MediaSnapshot disabled = service.snapshot();
            for (int i = 0; i < 100; i++) service.setEnabled(false);
            check(service.snapshot() == disabled, "Repeated disable must be idempotent");
            awaitNoBridge();
            check(service.snapshot() == disabled, "Stopped worker must not publish stale data");

            service.setEnabled(true);
            awaitObservation(service);
            MediaSnapshot second = service.snapshot();
            check(second.revision() > first.revision(), "Re-enabled session must invalidate old artwork revision");
            System.out.println("restart status=" + service.status() + " available=" + second.available()
                    + " playing=" + second.playing() + " artwork=" + (second.artwork() != null));
            service.close();
            service.setEnabled(true);
            check(!service.isEnabled() && !service.snapshot().available(), "Close must prevent restart");
            awaitNoBridge();
            // Exercise cancel-during-start races; stopped instances must not leave child processes.
            for (int i = 0; i < 12; i++) {
                try (MediaService racing = new MediaService()) {
                    racing.setEnabled(true);
                    racing.setEnabled(false);
                    racing.setEnabled(true);
                }
            }
            awaitNoBridge();
            System.out.println("MediaLifecycleReadOnlyProbe: passed; no playback commands sent.");
        }
    }

    private static void awaitObservation(MediaService service) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            String status = service.status();
            if (status.equals("playing") || status.equals("paused") || status.equals("no_session")) return;
            Thread.sleep(50);
        }
        throw new AssertionError("No media observation: " + service.status() + "; " + service.diagnostic());
    }

    private static void awaitNoBridge() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        long clearSince = 0;
        while (System.nanoTime() < deadline) {
            boolean any = ProcessHandle.current().descendants().anyMatch(p ->
                    p.isAlive() && p.info().command().orElse("").toLowerCase().endsWith("powershell.exe"));
            if (any) clearSince = 0;
            else if (clearSince == 0) clearSince = System.nanoTime();
            else if (System.nanoTime() - clearSince >= TimeUnit.MILLISECONDS.toNanos(500)) return;
            Thread.sleep(50);
        }
        throw new AssertionError("Stopped media service retained its PowerShell child");
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
