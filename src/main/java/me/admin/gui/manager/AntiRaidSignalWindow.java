package me.admin.gui.manager;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure, deterministic AntiRaid signal window. A UUID occupies at most one slot,
 * so reconnects refresh a sample without inflating the counters.
 */
public final class AntiRaidSignalWindow {

    public record Settings(long windowMillis, int joinsThreshold, int newPlayerThreshold,
                           int subnetThreshold, long newPlayerAgeMillis, long trustedAgeMillis,
                           double hysteresisRatio, long cooldownMillis) {
        public Settings {
            windowMillis = Math.max(1_000L, windowMillis);
            joinsThreshold = Math.max(1, joinsThreshold);
            newPlayerThreshold = Math.max(1, newPlayerThreshold);
            subnetThreshold = Math.max(1, subnetThreshold);
            newPlayerAgeMillis = Math.max(0L, newPlayerAgeMillis);
            trustedAgeMillis = Math.max(newPlayerAgeMillis, trustedAgeMillis);
            hysteresisRatio = Math.clamp(hysteresisRatio, 0.1D, 0.95D);
            cooldownMillis = Math.max(0L, cooldownMillis);
        }
    }

    public record Snapshot(int uniqueJoins, int untrustedJoins, int newPlayers,
                           int largestSubnet, String largestSubnetKey, int ignoredAddresses) {
        public static Snapshot empty() { return new Snapshot(0, 0, 0, 0, "", 0); }
    }

    public record Detection(boolean triggered, String trigger, String explanation, Snapshot snapshot) {
        static Detection none(Snapshot snapshot) { return new Detection(false, "", "", snapshot); }
    }

    private record Sample(long timestamp, boolean newPlayer, boolean trusted, String network) {}

    private final Map<UUID, Sample> samples = new LinkedHashMap<>();
    private boolean armed = true;
    private long lastTrigger;

    public synchronized Detection record(UUID uuid, long now, long firstPlayed, String address, Settings settings) {
        prune(now, settings.windowMillis());
        long age = firstPlayed <= 0L ? 0L : Math.max(0L, now - firstPlayed);
        boolean newPlayer = firstPlayed <= 0L || age <= settings.newPlayerAgeMillis();
        boolean trusted = firstPlayed > 0L && age >= settings.trustedAgeMillis();
        samples.put(uuid, new Sample(now, newPlayer, trusted, networkGroup(address).orElse("")));

        Snapshot snapshot = snapshot();
        if (!armed) {
            boolean cooledDown = now - lastTrigger >= settings.cooldownMillis();
            boolean belowHysteresis = snapshot.untrustedJoins() < rearmAt(settings.joinsThreshold(), settings.hysteresisRatio())
                    && snapshot.newPlayers() < rearmAt(settings.newPlayerThreshold(), settings.hysteresisRatio())
                    && snapshot.largestSubnet() < rearmAt(settings.subnetThreshold(), settings.hysteresisRatio());
            if (cooledDown && belowHysteresis) armed = true;
            else return Detection.none(snapshot);
        }

        String trigger = "";
        String explanation = "";
        if (snapshot.untrustedJoins() >= settings.joinsThreshold()) {
            trigger = "mass-join";
            explanation = "untrusted=" + snapshot.untrustedJoins() + "/" + settings.joinsThreshold()
                    + "; unique=" + snapshot.uniqueJoins();
        } else if (snapshot.newPlayers() >= settings.newPlayerThreshold()) {
            trigger = "new-accounts";
            explanation = "new=" + snapshot.newPlayers() + "/" + settings.newPlayerThreshold()
                    + "; unique=" + snapshot.uniqueJoins();
        } else if (snapshot.largestSubnet() >= settings.subnetThreshold()) {
            trigger = "same-network";
            explanation = "network=" + snapshot.largestSubnetKey() + "; count=" + snapshot.largestSubnet()
                    + "/" + settings.subnetThreshold();
        }
        if (trigger.isEmpty()) return Detection.none(snapshot);

        armed = false;
        lastTrigger = now;
        return new Detection(true, trigger, explanation, snapshot);
    }

    public synchronized Snapshot current(long now, long windowMillis) {
        prune(now, Math.max(1_000L, windowMillis));
        return snapshot();
    }

    private Snapshot snapshot() {
        int untrusted = 0;
        int newPlayers = 0;
        int ignored = 0;
        int largest = 0;
        String largestKey = "";
        Map<String, Integer> networks = new HashMap<>();
        for (Sample sample : samples.values()) {
            if (!sample.trusted()) untrusted++;
            if (sample.newPlayer()) newPlayers++;
            // Trusted accounts remain visible in total joins, but cannot form an automatic subnet trigger.
            if (sample.trusted()) continue;
            if (sample.network().isEmpty()) {
                ignored++;
                continue;
            }
            int count = networks.merge(sample.network(), 1, Integer::sum);
            if (count > largest) {
                largest = count;
                largestKey = sample.network();
            }
        }
        return new Snapshot(samples.size(), untrusted, newPlayers, largest, largestKey, ignored);
    }

    private void prune(long now, long windowMillis) {
        Iterator<Sample> iterator = samples.values().iterator();
        while (iterator.hasNext()) {
            Sample sample = iterator.next();
            if (now - sample.timestamp() > windowMillis) iterator.remove();
        }
    }

    private static int rearmAt(int threshold, double ratio) {
        return Math.max(1, (int) Math.floor(threshold * ratio));
    }

    /** Returns an IPv4 /24 or IPv6 /64 identity; placeholders are ignored. */
    public static Optional<String> networkGroup(String rawAddress) {
        if (rawAddress == null) return Optional.empty();
        String value = rawAddress.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.equals("unknown") || value.equals("null") || value.equals("n/a")) {
            return Optional.empty();
        }
        if (value.startsWith("/")) value = value.substring(1);
        int closingBracket = value.indexOf(']');
        if (value.startsWith("[") && closingBracket > 0) value = value.substring(1, closingBracket);

        if (value.indexOf(':') < 0) {
            String[] parts = value.split("\\.", -1);
            if (parts.length != 4) return Optional.empty();
            int[] octets = new int[4];
            try {
                for (int i = 0; i < 4; i++) {
                    if (parts[i].isEmpty() || parts[i].length() > 3) return Optional.empty();
                    octets[i] = Integer.parseInt(parts[i]);
                    if (octets[i] < 0 || octets[i] > 255) return Optional.empty();
                }
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
            return Optional.of(octets[0] + "." + octets[1] + "." + octets[2] + ".0/24");
        }

        try {
            int zone = value.indexOf('%');
            if (zone >= 0) value = value.substring(0, zone);
            InetAddress address = InetAddress.getByName(value);
            if (!(address instanceof Inet6Address)) return Optional.empty();
            byte[] bytes = address.getAddress();
            StringBuilder network = new StringBuilder();
            for (int i = 0; i < 8; i += 2) {
                if (!network.isEmpty()) network.append(':');
                network.append(Integer.toHexString((bytes[i] & 0xff) << 8 | bytes[i + 1] & 0xff));
            }
            return Optional.of(network + "::/64");
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
