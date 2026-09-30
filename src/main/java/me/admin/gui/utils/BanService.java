package me.admin.gui.utils;

import io.papermc.paper.ban.BanListType;
import org.bukkit.BanEntry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.ban.IpBanList;
import org.bukkit.ban.ProfileBanList;
import com.destroystokyo.paper.profile.PlayerProfile;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class BanService {

    public record ProfileBan(String name, String reason, String source, Instant expiration) {}
    public record IpBan(String address, String reason, String source, Instant expiration) {}

    private BanService() {}

    public static ProfileBanList profiles() {
        return Bukkit.getBanList(BanListType.PROFILE);
    }

    public static IpBanList ips() {
        return Bukkit.getBanList(BanListType.IP);
    }

    public static PlayerProfile profile(String name) {
        return Bukkit.createProfile(name);
    }

    public static PlayerProfile profile(UUID uuid, String name) {
        return uuid == null ? profile(name) : Bukkit.createProfile(uuid, name);
    }

    public static PlayerProfile profile(OfflinePlayer player) {
        return player.getPlayerProfile();
    }

    public static void banProfile(String name, String reason, Instant expiration, String source) {
        profiles().addBan(profile(name), reason, expiration, source);
    }

    public static void banProfile(OfflinePlayer player, String reason, Instant expiration, String source) {
        profiles().addBan(profile(player), reason, expiration, source);
    }

    public static boolean isProfileBanned(String name) {
        return profiles().isBanned(profile(name));
    }

    public static boolean isProfileBanned(OfflinePlayer player) {
        return profiles().isBanned(profile(player));
    }

    public static void pardonProfile(String name) {
        profiles().pardon(profile(name));
    }

    public static void pardonProfile(OfflinePlayer player) {
        profiles().pardon(profile(player));
    }

    public static Set<BanEntry<? super PlayerProfile>> profileEntries() {
        return profiles().getEntries();
    }

    public static int activeProfileBanCount() {
        return profiles().getEntries().size();
    }

    public static List<ProfileBan> profileBans() {
        return profiles().getEntries().stream().map(entry -> {
            Object target = entry.getBanTarget();
            String name = target instanceof PlayerProfile profile ? profile.getName() : String.valueOf(target);
            return new ProfileBan(name == null ? "?" : name, value(entry.getReason()), value(entry.getSource()),
                    entry.getExpiration() == null ? null : entry.getExpiration().toInstant());
        }).toList();
    }

    public static void banIp(String address, String reason, Instant expiration, String source) {
        ips().addBan(parseAddress(address), reason, expiration, source);
    }

    public static boolean isIpBanned(String address) {
        return ips().isBanned(parseAddress(address));
    }

    public static void pardonIp(String address) {
        ips().pardon(parseAddress(address));
    }

    public static Set<BanEntry<? super InetAddress>> ipEntries() {
        return ips().getEntries();
    }

    public static List<IpBan> ipBans() {
        return ips().getEntries().stream().map(entry -> {
            Object target = entry.getBanTarget();
            String address = target instanceof InetAddress inetAddress
                    ? inetAddress.getHostAddress() : String.valueOf(target);
            return new IpBan(address, value(entry.getReason()), value(entry.getSource()),
                    entry.getExpiration() == null ? null : entry.getExpiration().toInstant());
        }).toList();
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? "?" : value;
    }

    private static InetAddress parseAddress(String address) {
        try {
            return InetAddress.getByName(address);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Некорректный IP-адрес: " + address, e);
        }
    }
}
