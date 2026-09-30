package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.List;

/** Explainable heuristic risk indicator; it never applies punishments automatically. */
public final class PlayerRiskManager {

    public record RiskFactor(String name, int points, String details) {}
    public record RiskProfile(int score, String level, List<RiskFactor> factors) {}

    private final AdvancedModeratorGUI plugin;

    public PlayerRiskManager(AdvancedModeratorGUI plugin) {
        this.plugin = plugin;
    }

    public RiskProfile calculate(OfflinePlayer target) {
        List<RiskFactor> factors = new ArrayList<>();
        int warns = plugin.getWarnManager().getWarnCount(target.getUniqueId());
        add(factors, "Предупреждения", Math.min(25, warns * 7), warns + " активных");

        long reports = plugin.getReportManager().getActive().stream()
                .filter(report -> target.getName() != null && report.target().equalsIgnoreCase(target.getName())).count();
        add(factors, "Активные жалобы", (int) Math.min(25, reports * 6), reports + " активных");

        long cases = plugin.getModerationCaseManager().getOpen().stream()
                .filter(value -> value.targetUuid() != null && value.targetUuid().equals(target.getUniqueId())
                        || target.getName() != null && value.targetName().equalsIgnoreCase(target.getName())).count();
        add(factors, "Открытые дела", (int) Math.min(20, cases * 5), cases + " открытых");

        int related = plugin.getAltDetector().findRelatedAccounts(target.getUniqueId()).size();
        add(factors, "Связанные аккаунты", Math.min(20, related * 3), related + " аккаунтов");

        int ips = plugin.getAltDetector().getIpData(target.getUniqueId()).size();
        if (ips >= 5) add(factors, "Смена IP", Math.min(10, ips), ips + " адресов");

        String flag = plugin.getFlagManager().getFlag(target.getUniqueId());
        if (flag != null && !flag.equalsIgnoreCase("trusted") && !flag.equalsIgnoreCase("trust")) {
            add(factors, "Флаг персонала", 15, flag);
        }
        if (flag != null && (flag.equalsIgnoreCase("trusted") || flag.equalsIgnoreCase("trust"))) {
            add(factors, "Доверенный игрок", -15, flag);
        }

        long firstPlayed = target.getFirstPlayed();
        long newAccountWindow = Math.max(1, plugin.getConfig().getLong("risk-profile.new-player-hours", 24)) * 3600_000L;
        if (firstPlayed > 0 && System.currentTimeMillis() - firstPlayed < newAccountWindow) {
            add(factors, "Новый игрок", 8, "на сервере менее " + newAccountWindow / 3600_000L + " ч");
        }

        int score = Math.clamp(factors.stream().mapToInt(RiskFactor::points).sum(), 0, 100);
        String level = score >= 70 ? "CRITICAL" : score >= 45 ? "HIGH" : score >= 20 ? "MEDIUM" : "LOW";
        return new RiskProfile(score, level, List.copyOf(factors));
    }

    private static void add(List<RiskFactor> factors, String name, int points, String details) {
        if (points != 0) factors.add(new RiskFactor(name, points, details));
    }
}
