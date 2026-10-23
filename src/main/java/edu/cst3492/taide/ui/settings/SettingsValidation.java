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
        boolean connectionChanged = original == null || original.demoMode() != value.demoMode()
                || !value.provider().equals(original.provider()) || !value.model().equals(original.model())
                || !value.baseUrl().equals(original.baseUrl());
        if (!value.demoMode() && connectionChanged) {
            if (value.model().isBlank()) errors.add("Enter the model ID supplied by your provider.");
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
