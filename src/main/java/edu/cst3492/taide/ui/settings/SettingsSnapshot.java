package edu.cst3492.taide.ui.settings;

/** Immutable, non-secret settings supplied to the UI and A's service implementation. */
public record SettingsSnapshot(String provider, String baseUrl, String model,
                               String courseDirectory, boolean demoMode) {
    public static final String DEEPSEEK = "DeepSeek";
    public static final String AGI = "AGI";
    public static final String DEFAULT_URL = "https://api.deepseek.com";

    public SettingsSnapshot {
        provider = clean(provider);
        baseUrl = clean(baseUrl);
        model = clean(model);
        courseDirectory = clean(courseDirectory);
    }

    public static SettingsSnapshot defaults() {
        return new SettingsSnapshot(DEEPSEEK, DEFAULT_URL, "deepseek-chat", "", true);
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
