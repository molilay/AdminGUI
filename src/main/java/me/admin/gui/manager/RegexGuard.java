package me.admin.gui.manager;

import java.util.Locale;

/** Conservative static guard for user-configurable Java regular expressions. */
public final class RegexGuard {
    private RegexGuard() {}

    public record Validation(boolean safe, String reason) {
        static Validation allow() { return new Validation(true, ""); }
        static Validation reject(String reason) { return new Validation(false, reason); }
    }

    public static Validation validate(String expression, int maxLength) {
        if (expression == null || expression.isBlank()) return Validation.reject("empty");
        if (expression.length() > Math.max(16, maxLength)) return Validation.reject("too-long");
        String pattern = expression.toLowerCase(Locale.ROOT);
        // A repeated DNS label has a literal dot delimiter, making its boundary unambiguous.
        String structural = pattern.replace("\\w+\\.", "label.");
        if (pattern.contains("(?r)") || pattern.matches(".*\\\\[1-9].*")) {
            return Validation.reject("recursion-or-backreference");
        }
        if (pattern.contains("(?<=") || pattern.contains("(?<!")) return Validation.reject("lookbehind");
        if (structural.matches(".*\\([^)]*[+*][^)]*\\)[+*{].*")) return Validation.reject("nested-quantifier");
        if (structural.matches(".*(\\.\\*|\\.\\+).*(\\.\\*|\\.\\+).*")) {
            return Validation.reject("multiple-wildcards");
        }
        if (structural.matches(".*\\([^)]*\\|[^)]*\\)[+*{].*")) return Validation.reject("quantified-alternation");
        if (structural.contains(".*.*") || structural.contains(".+.+")) return Validation.reject("ambiguous-wildcard");
        return Validation.allow();
    }
}
