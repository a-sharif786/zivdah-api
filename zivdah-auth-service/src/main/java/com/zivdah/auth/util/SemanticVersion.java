package com.zivdah.auth.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// MAJOR.MINOR.PATCH, compared numerically (so 1.10.0 > 1.9.0). Missing parts default to 0
// ("2" == "2.0" == "2.0.0"), and "+build" metadata (e.g. Flutter's "1.2.3+45") is ignored, as
// semver says it must be for precedence. Pre-release tags ("-beta") are rejected.
public record SemanticVersion(int major, int minor, int patch) implements Comparable<SemanticVersion> {

    private static final Pattern FORMAT = Pattern.compile("^[vV]?(\\d{1,6})(?:\\.(\\d{1,6}))?(?:\\.(\\d{1,6}))?(?:\\+[0-9A-Za-z.-]+)?$");

    public static SemanticVersion parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Version is required");
        }
        Matcher m = FORMAT.matcher(value.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid version '" + value + "'. Expected format MAJOR.MINOR.PATCH, e.g. 1.2.0");
        }
        return new SemanticVersion(
                Integer.parseInt(m.group(1)),
                m.group(2) == null ? 0 : Integer.parseInt(m.group(2)),
                m.group(3) == null ? 0 : Integer.parseInt(m.group(3)));
    }

    @Override
    public int compareTo(SemanticVersion other) {
        if (major != other.major) return Integer.compare(major, other.major);
        if (minor != other.minor) return Integer.compare(minor, other.minor);
        return Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
