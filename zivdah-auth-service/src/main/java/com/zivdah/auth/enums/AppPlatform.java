package com.zivdah.auth.enums;

import java.util.Locale;

public enum AppPlatform {
    ANDROID,
    IOS;

    // Clients send "android"/"ios" in any case; anything else is a 400 (see GlobalExceptionHandler).
    public static AppPlatform from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("platform is required (android or ios)");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid platform '" + value + "'. Allowed values: android, ios");
        }
    }

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
