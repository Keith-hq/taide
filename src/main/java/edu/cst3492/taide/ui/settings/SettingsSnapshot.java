package edu.cst3492.taide.ui.settings;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

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

    /** PasswordSafe keys are bound to an endpoint, so changing hosts cannot reuse a secret. */
    public String credentialScope() {
        String identity = baseUrl.replaceAll("/+$", "");
        try {
            URI uri = new URI(identity).normalize();
            if (uri.getHost() != null && uri.getScheme() != null) {
                String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
                int port = uri.getPort();
                if (("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80)) port = -1;
                identity = new URI(scheme, null, uri.getHost().toLowerCase(Locale.ROOT), port,
                        uri.getPath(), uri.getQuery(), uri.getFragment()).toString();
            }
        } catch (URISyntaxException ignored) {
            // Invalid legacy state is isolated too; validation prevents its use for requests.
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
