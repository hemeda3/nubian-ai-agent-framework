package com.nubian.ai.agentpress.util.config;

/**
 * Environment mode enumeration.
 * Represents the possible deployment environments for the application.
 */
public enum EnvMode {
    LOCAL("local"),
    STAGING("staging"),
    PRODUCTION("production");

    private final String value;

    EnvMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Convert a string to EnvMode enum.
     *
     * @param value String value to convert
     * @return Corresponding EnvMode or LOCAL if not found
     */
    public static EnvMode fromString(String value) {
        if (value == null) {
            return LOCAL;
        }

        for (EnvMode mode : EnvMode.values()) {
            if (mode.value.equalsIgnoreCase(value)) {
                return mode;
            }
        }

        return LOCAL;
    }
}
