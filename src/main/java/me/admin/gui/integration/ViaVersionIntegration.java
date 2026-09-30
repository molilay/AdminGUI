package me.admin.gui.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

public class ViaVersionIntegration {

    private boolean enabled = false;
    private Object viaAPI;
    private Method getPlayerVersionMethod;
    private Method getServerVersionMethod;
    private Object connectionManager;
    private Method getConnectedClientMethod;

    public boolean setup() {
        if (Bukkit.getPluginManager().getPlugin("ViaVersion") == null) return false;
        try {
            Class<?> viaClass = Class.forName("com.viaversion.viaversion.api.Via");
            viaAPI = viaClass.getMethod("getAPI").invoke(null);
            if (viaAPI == null) return false;

            Class<?> apiClass = viaAPI.getClass();
            getPlayerVersionMethod = tryGetMethod(apiClass, "getPlayerVersion", Player.class);
            getServerVersionMethod = tryGetMethod(apiClass, "getServerVersion");

            try {
                Object manager = viaClass.getMethod("getManager").invoke(null);
                if (manager != null) {
                    connectionManager = manager.getClass().getMethod("getConnectionManager").invoke(manager);
                    if (connectionManager != null) {
                        getConnectedClientMethod = tryGetMethod(connectionManager.getClass(), "getConnectedClient", UUID.class);
                    }
                }
            } catch (Exception ignored) {}

            enabled = true;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Method tryGetMethod(Class<?> clazz, String name, Class<?>... params) {
        try { return clazz.getMethod(name, params); } catch (Exception e) { return null; }
    }

    public boolean isEnabled() { return enabled; }

    public int getProtocolVersion(Player player) {
        if (!enabled || player == null || getPlayerVersionMethod == null) return -1;
        try {
            Object result = getPlayerVersionMethod.invoke(viaAPI, player);
            if (result instanceof Integer) return (int) result;
        } catch (Exception ignored) {}
        return -1;
    }

    public int getServerProtocolVersion() {
        if (!enabled || getServerVersionMethod == null) return -1;
        try {
            Object result = getServerVersionMethod.invoke(viaAPI);
            if (result != null) {
                Method m = result.getClass().getMethod("getVersion");
                Object ver = m.invoke(result);
                if (ver instanceof Integer) return (int) ver;
            }
        } catch (Exception ignored) {}
        return -1;
    }

    public String getMinecraftVersion(int protocolVersion) {
        if (protocolVersion <= 0) return "§7Неизвестно";
        String known = switch (protocolVersion) {
            case 769 -> "1.21.4";
            case 768 -> "1.21.2-1.21.3";
            case 767 -> "1.21.1";
            case 766 -> "1.21";
            case 765 -> "1.20.5-1.20.6";
            case 764 -> "1.20.3-1.20.4";
            case 763 -> "1.20.2";
            case 762 -> "1.20-1.20.1";
            case 761 -> "1.19.4";
            case 760 -> "1.19.3";
            case 759 -> "1.19-1.19.2";
            case 758 -> "1.18.2";
            case 757 -> "1.18-1.18.1";
            case 756 -> "1.17.1";
            case 755 -> "1.17";
            case 754 -> "1.16.5";
            case 753 -> "1.16.4-1.16.5";
            default -> protocolVersion >= 756 ? "1.17+" : protocolVersion >= 700 ? "1.16.x" : "1.15-";
        };
        return "§f" + known + " §8(§7" + protocolVersion + "§8)";
    }

    public ClientInfoResult getRichClientInfo(Player player) {
        ClientInfoResult result = new ClientInfoResult();
        if (player == null) return result;

        try { result.brandRaw = player.getClientBrandName(); } catch (Exception ignored) {}
        try { result.locale = player.locale().toLanguageTag(); } catch (Exception ignored) {}
        try { result.ping = player.getPing(); } catch (Exception ignored) {}

        if (!enabled) return result;

        int proto = getProtocolVersion(player);
        if (proto > 0) {
            result.protocolVersion = proto;
            result.minecraftVersion = getMinecraftVersion(proto);
        }

        int serverProto = getServerProtocolVersion();
        if (serverProto > 0) result.serverProtocol = serverProto;

        result.clientType = detectViaClientType(player);
        result.clientModLoader = detectViaModLoader(player);

        return result;
    }

    public String detectViaClientType(Player player) {
        return detectViaClientType(player.getUniqueId());
    }

    public String detectViaClientType(UUID uuid) {
        if (!enabled || getConnectedClientMethod == null || connectionManager == null) return null;
        try {
            Object userConn = getConnectedClientMethod.invoke(connectionManager, uuid);
            if (userConn == null) return null;
            try {
                Method getOsMethod = userConn.getClass().getMethod("getClientBrand");
                Object brand = getOsMethod.invoke(userConn);
                if (brand instanceof String) return (String) brand;
            } catch (Exception ignored) {}

            try {
                Method getProtocolInfo = userConn.getClass().getMethod("getProtocolInfo");
                Object protocolInfo = getProtocolInfo.invoke(userConn);
                if (protocolInfo == null) return null;
                try {
                    Method getClientBrand = protocolInfo.getClass().getMethod("getClientBrand");
                    Object brand = getClientBrand.invoke(protocolInfo);
                    if (brand instanceof String && !((String)brand).isEmpty()) return (String) brand;
                } catch (Exception ignored2) {}
            } catch (Exception ignored3) {}
        } catch (Exception ignored) {}
        return null;
    }

    public String detectViaModLoader(Player player) {
        return detectViaModLoader(player.getUniqueId());
    }

    public String detectViaModLoader(UUID uuid) {
        if (!enabled || getConnectedClientMethod == null || connectionManager == null) return null;
        try {
            Object userConn = getConnectedClientMethod.invoke(connectionManager, uuid);
            if (userConn == null) return null;

            try {
                Method getFMLVersion = userConn.getClass().getMethod("getFMLVersion");
                Object fmlVer = getFMLVersion.invoke(userConn);
                if (fmlVer instanceof String) {
                    String str = (String) fmlVer;
                    if (!str.isEmpty() && !str.equals("0")) {
                        return str;
                    }
                }
            } catch (Exception ignored) {}

            try {
                Method getMinecraftVersion = userConn.getClass().getMethod("getMinecraftVersion");
                Object mcVer = getMinecraftVersion.invoke(userConn);
                if (mcVer instanceof String) return (String) mcVer;
            } catch (Exception ignored) {}

            try {
                Method getProtocolInfo = userConn.getClass().getMethod("getProtocolInfo");
                Object protocolInfo = getProtocolInfo.invoke(userConn);
                if (protocolInfo == null) return null;
                try {
                    Method getClientProtocolMethod = tryGetMethod(protocolInfo.getClass(), "getServerProtocolVersion");
                    if (getClientProtocolMethod != null) {
                        Object result = getClientProtocolMethod.invoke(protocolInfo);
                        if (result instanceof Integer) return String.valueOf(result);
                    }
                } catch (Exception ignored) {}
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
        return null;
    }

    public static String detectBrandFamily(Player player) {
        try {
            String brand = player.getClientBrandName();
            if (brand == null || brand.isEmpty()) return "§7Неизвестно";
            String lower = brand.toLowerCase();

            if (lower.contains("forge") || lower.contains("fml")) return "§cForge";
            if (lower.contains("fabric")) return "§bFabric";
            if (lower.contains("quilt")) return "§aQuilt";
            if (lower.contains("neoforge")) return "§4NeoForge";
            if (lower.contains("lunar")) return "§dLunarClient";
            if (lower.contains("badlion")) return "§eBadlion";
            if (lower.contains("labymod") || lower.contains("laby")) return "§5LabyMod";
            if (lower.contains("optifine")) return "§aOptiFine";
            if (lower.contains("vanilla")) return "§aVanilla";
            return "§f" + brand;
        } catch (Exception e) {
            return "§7Неизвестно";
        }
    }

    public static String detectServerModLoader() {
        try {
            Class.forName("net.minecraftforge.fml.common.Loader");
            return "§cForge";
        } catch (Exception ignored) {}
        try {
            Class.forName("net.fabricmc.loader.api.FabricLoader");
            return "§bFabric";
        } catch (Exception ignored) {}
        try {
            Class.forName("org.quiltmc.loader.api.QuiltLoader");
            return "§aQuilt";
        } catch (Exception ignored) {}
        return null;
    }

    public static class ClientInfoResult {
        public String brandRaw;
        public String locale;
        public int ping;
        public int protocolVersion = -1;
        public int serverProtocol = -1;
        public String minecraftVersion;
        public String clientType;
        public String clientModLoader;

        public boolean hasAnyInfo() {
            return brandRaw != null || protocolVersion > 0 || ping > 0 || clientType != null;
        }

        public static ClientInfoResult fromPlayer(Player player) {
            ClientInfoResult r = new ClientInfoResult();
            if (player == null) return r;
            try { r.brandRaw = player.getClientBrandName(); } catch (Exception ignored) {}
            try { r.locale = player.locale().toLanguageTag(); } catch (Exception ignored) {}
            try { r.ping = player.getPing(); } catch (Exception ignored) {}
            return r;
        }
    }
}
