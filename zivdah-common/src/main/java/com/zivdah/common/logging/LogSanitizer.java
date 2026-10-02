package com.zivdah.common.logging;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogSanitizer {

    private record Rule(Pattern pattern, String replacement) {}

    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]*"), "[JWT]"),
            new Rule(Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]+"), "Bearer [REDACTED]"),
            new Rule(Pattern.compile("(?i)((?:access_?|refresh_?|id_?|device_?|fcm_?|internal-?)?token"
                    + "|password|passwd|pwd|secret(?:_?key)?|api_?key|x-internal-token|otp|pin|cvv)"
                    + "(\"?\\s*[:=]\\s*\"?)([^\\s\"'&,;}\\]]+)"), "$1$2[REDACTED]"),
            new Rule(Pattern.compile("\\b([A-Za-z0-9])[A-Za-z0-9._%+-]*@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})\\b"), "$1***@$2"),
            new Rule(Pattern.compile("(?<![\\d])(?:\\+?91[- ]?)?([6-9]\\d)\\d{6}(\\d{2})(?![\\d])"), "$1******$2")
    );

    private LogSanitizer() {
    }

    public static String sanitize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        for (Rule rule : RULES) {
            Matcher m = rule.pattern().matcher(result);
            if (m.find()) {
                result = m.replaceAll(rule.replacement());
            }
        }
        return result;
    }
}
