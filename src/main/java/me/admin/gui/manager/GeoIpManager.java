package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Performs privacy-sensitive IP geolocation only after an authorized moderator requests it. */
public final class GeoIpManager {

    public record GeoIpResult(String ip, String country, String countryCode, String region, String city,
                              double latitude, double longitude, String timezone, String isp,
                              String organization, String connectionType, Boolean proxy, Boolean vpn,
                              Boolean tor, Boolean hosting, long fetchedAt) {
        public String locationLine() {
            return java.util.stream.Stream.of(country, region, city)
                    .filter(value -> value != null && !value.isBlank())
                    .distinct().collect(java.util.stream.Collectors.joining(", "));
        }
    }

    private record CacheEntry(GeoIpResult result, long expiresAt) {}

    private static final Pattern JSON_STRING = Pattern.compile("\\\"%s\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"");
    private static final Pattern JSON_NUMBER = Pattern.compile("\\\"%s\\\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern JSON_BOOLEAN = Pattern.compile("\\\"%s\\\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private final AdvancedModeratorGUI plugin;
    private final HttpClient client;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<GeoIpResult>> inFlight = new ConcurrentHashMap<>();

    public GeoIpManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("ip-geolocation.enabled", true);
    }

    public void reload() {
        cache.clear();
    }

    public CompletableFuture<GeoIpResult> lookup(String input) {
        if (!isEnabled()) return CompletableFuture.failedFuture(new IllegalStateException("Геолокация IP отключена в config.yml"));
        final String ip;
        try {
            ip = normalizePublicIp(input);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(e);
        }

        long now = System.currentTimeMillis();
        CacheEntry cached = cache.get(ip);
        if (cached != null && cached.expiresAt() > now) return CompletableFuture.completedFuture(cached.result());
        if (cached != null) cache.remove(ip, cached);

        int maxInFlight = Math.clamp(plugin.getConfig().getInt("ip-geolocation.max-in-flight", 8), 1, 64);
        if (!inFlight.containsKey(ip) && inFlight.size() >= maxInFlight) {
            return CompletableFuture.failedFuture(new IllegalStateException("Слишком много одновременных GeoIP-запросов"));
        }

        return inFlight.computeIfAbsent(ip, ignored -> request(ip).whenComplete((result, error) -> inFlight.remove(ip)));
    }

    public GeoIpResult getCached(String input) {
        try {
            CacheEntry entry = cache.get(normalizePublicIp(input));
            if (entry != null && entry.expiresAt() > System.currentTimeMillis()) return entry.result();
        } catch (IllegalArgumentException ignored) {
            // Invalid and private addresses are deliberately never cached or queried.
        }
        return null;
    }

    public void invalidate(String input) {
        try { cache.remove(normalizePublicIp(input)); }
        catch (IllegalArgumentException ignored) { /* Nothing to invalidate. */ }
    }

    private CompletableFuture<GeoIpResult> request(String ip) {
        URI uri;
        try {
            String template = plugin.getConfig().getString("ip-geolocation.endpoint",
                    "https://ipwho.is/{ip}?lang={language}&fields=success,message,ip,country,country_code,region,city,latitude,longitude,timezone.id,connection.isp,connection.org,type");
            if (template == null || !template.contains("{ip}")) throw new IllegalArgumentException("В endpoint отсутствует {ip}");
            String language = plugin.getConfig().getString("ip-geolocation.language", "ru");
            language = language == null ? "ru" : language.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
            String encodedIp = URLEncoder.encode(ip, StandardCharsets.UTF_8).replace("+", "%20");
            uri = URI.create(template.replace("{ip}", encodedIp).replace("{language}", language));
            if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("GeoIP endpoint должен использовать HTTPS");
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(new IllegalStateException("Неверный GeoIP endpoint: " + e.getMessage(), e));
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(timeoutSeconds()))
                .header("Accept", "application/json")
                .header("User-Agent", plugin.getName() + "/" + plugin.getPluginMeta().getVersion())
                .GET().build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() != 200) throw new IllegalStateException("GeoIP API вернул HTTP " + response.statusCode());
                    String body = response.body();
                    if (body == null || body.isBlank() || body.length() > MAX_RESPONSE_BYTES) {
                        throw new IllegalStateException("GeoIP API вернул пустой или слишком большой ответ");
                    }
                    if (body.matches("(?s).*\\\"success\\\"\\s*:\\s*false.*")) {
                        throw new IllegalStateException(value(body, "message", "адрес не найден"));
                    }
                    GeoIpResult result = parse(ip, body);
                    long ttl = Math.max(60, plugin.getConfig().getLong("ip-geolocation.cache-seconds", 86400)) * 1000L;
                    cache.put(ip, new CacheEntry(result, System.currentTimeMillis() + ttl));
                    trimCache();
                    return result;
                });
    }

    private void trimCache() {
        int maxEntries = Math.clamp(plugin.getConfig().getInt("ip-geolocation.max-cache-entries", 2000), 50, 20000);
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        int remove = cache.size() - maxEntries;
        if (remove <= 0) return;
        cache.entrySet().stream()
                .sorted(java.util.Comparator.comparingLong(entry -> entry.getValue().result().fetchedAt()))
                .limit(remove)
                .map(Map.Entry::getKey)
                .toList().forEach(cache::remove);
    }

    public int cacheSize() { return cache.size(); }
    public int inFlightSize() { return inFlight.size(); }

    private int timeoutSeconds() {
        return Math.clamp(plugin.getConfig().getInt("ip-geolocation.timeout-seconds", 5), 2, 15);
    }

    static GeoIpResult parse(String requestedIp, String json) {
        String returnedIp = value(json, "ip", requestedIp);
        return new GeoIpResult(returnedIp,
                value(json, "country", ""), value(json, "country_code", ""),
                value(json, "region", ""), value(json, "city", ""),
                number(json, "latitude"), number(json, "longitude"),
                value(json, "id", ""), value(json, "isp", ""),
                value(json, "org", ""), value(json, "type", ""), bool(json, "proxy"), bool(json, "vpn"),
                bool(json, "tor"), bool(json, "hosting"), System.currentTimeMillis());
    }

    static String value(String json, String key, String fallback) {
        Matcher matcher = Pattern.compile(JSON_STRING.pattern().formatted(Pattern.quote(key))).matcher(json);
        return matcher.find() ? unescapeJson(matcher.group(1)) : fallback;
    }

    static double number(String json, String key) {
        Matcher matcher = Pattern.compile(JSON_NUMBER.pattern().formatted(Pattern.quote(key))).matcher(json);
        if (!matcher.find()) return Double.NaN;
        try { return Double.parseDouble(matcher.group(1)); }
        catch (NumberFormatException ignored) { return Double.NaN; }
    }

    static Boolean bool(String json, String key) {
        Matcher matcher = Pattern.compile(JSON_BOOLEAN.pattern().formatted(Pattern.quote(key)), Pattern.CASE_INSENSITIVE).matcher(json);
        return matcher.find() ? Boolean.valueOf(matcher.group(1)) : null;
    }

    public static double distanceKm(GeoIpResult first, GeoIpResult second) {
        if (first == null || second == null || Double.isNaN(first.latitude()) || Double.isNaN(first.longitude())
                || Double.isNaN(second.latitude()) || Double.isNaN(second.longitude())) return Double.NaN;
        double lat1 = Math.toRadians(first.latitude()), lat2 = Math.toRadians(second.latitude());
        double deltaLat = Math.toRadians(second.latitude() - first.latitude());
        double deltaLon = Math.toRadians(second.longitude() - first.longitude());
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static String unescapeJson(String input) {
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c != '\\' || i + 1 >= input.length()) { out.append(c); continue; }
            char escaped = input.charAt(++i);
            switch (escaped) {
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case '\\', '/', '"' -> out.append(escaped);
                case 'u' -> {
                    if (i + 4 >= input.length()) throw new IllegalArgumentException("Повреждён Unicode в JSON");
                    out.append((char) Integer.parseInt(input.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                default -> out.append(escaped);
            }
        }
        return out.toString();
    }

    public static String normalizePublicIp(String input) {
        if (input == null) throw new IllegalArgumentException("IP не указан");
        String value = input.trim();
        if (value.startsWith("[") && value.endsWith("]")) value = value.substring(1, value.length() - 1);
        if (value.isBlank() || value.length() > 45 || !value.matches("[0-9a-fA-F:.]+")) {
            throw new IllegalArgumentException("Укажите числовой IPv4 или IPv6 адрес");
        }
        if (!value.contains(":")) {
            String[] parts = value.split("\\.", -1);
            if (parts.length != 4) throw new IllegalArgumentException("Некорректный IPv4 адрес");
            for (String part : parts) {
                if (part.isEmpty() || part.length() > 3) throw new IllegalArgumentException("Некорректный IPv4 адрес");
                int octet;
                try { octet = Integer.parseInt(part); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("Некорректный IPv4 адрес"); }
                if (octet > 255) throw new IllegalArgumentException("Некорректный IPv4 адрес");
            }
        }
        try {
            InetAddress address = InetAddress.getByName(value);
            if (!isPublic(address)) throw new IllegalArgumentException("Локальные и служебные IP не отправляются во внешний сервис");
            return address.getHostAddress();
        } catch (java.net.UnknownHostException e) {
            throw new IllegalArgumentException("Некорректный IP адрес");
        }
    }

    private static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] b = address.getAddress();
        if (address instanceof Inet6Address) return (b[0] & 0xfe) != 0xfc;
        int first = b[0] & 0xff, second = b[1] & 0xff, third = b[2] & 0xff;
        if (first == 0 || first >= 224) return false;
        if (first == 100 && second >= 64 && second <= 127) return false;
        if (first == 169 && second == 254) return false;
        if (first == 192 && second == 0 && (third == 0 || third == 2)) return false;
        if (first == 198 && (second == 18 || second == 19 || second == 51)) return false;
        return !(first == 203 && second == 0 && third == 113);
    }
}
