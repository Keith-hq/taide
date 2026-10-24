package edu.cst3492.taide.ui.settings;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Pure syntax validation; directory access is deliberately left to a background task. */
public final class SettingsValidation {
    private SettingsValidation() { }

    public static List<String> validate(SettingsSnapshot value, SettingsSnapshot original) {
        List<String> errors = new ArrayList<>();
        if (!SettingsSnapshot.DEEPSEEK.equals(value.provider()) && !SettingsSnapshot.AGI.equals(value.provider())) {
            errors.add("Choose DeepSeek or AGI as the provider.");
        }
        // A live request must never inherit an invalid endpoint from older saved state.
        // Also keep URL credentials/query secrets out of the non-secret XML in demo mode.
        if (!value.demoMode()) {
            if (value.model().isBlank()) errors.add("Enter the model ID supplied by your provider.");
        }
        if (!value.demoMode() || !value.baseUrl().isBlank()) {
            String endpointError = endpointError(value.baseUrl());
            if (endpointError != null) errors.add(endpointError);
        }
        if (!value.courseDirectory().isEmpty()
                && (original == null || !value.courseDirectory().equals(original.courseDirectory()))) {
            try { Path.of(value.courseDirectory()); }
            catch (InvalidPathException ex) { errors.add("Choose a valid local course materials directory."); }
        }
        return List.copyOf(errors);
    }

    /** A base URL has no credentials or request parameters; HTTPS is required outside loopback. */
    public static String endpointError(String value) {
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            boolean loopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                    || "[::1]".equals(host) || "::1".equals(host);
            if (host == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535
                    || !("https".equalsIgnoreCase(uri.getScheme())
                    || (loopback && "http".equalsIgnoreCase(uri.getScheme())))) {
                return "Enter an HTTPS API base URL without credentials, query parameters or a fragment.";
            }
        } catch (URISyntaxException | NullPointerException ex) {
            return "Enter a valid API base URL, for example https://api.deepseek.com.";
        }
        return null;
    }
}
