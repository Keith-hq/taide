package edu.cst3492.taide.ui.settings;

/** Run with main(); no network, IDE instance, or test-framework dependency is required. */
public final class SettingsValidationTest {
    private static int checks;

    private static void require(boolean condition, String behavior) {
        checks++;
        if (!condition) throw new AssertionError(behavior);
    }

    public static void main(String[] args) {
        SettingsSnapshot original = SettingsSnapshot.defaults();
        require(SettingsValidation.validate(original, original).isEmpty(), "First-run demo works without credentials");
        require(SettingsValidation.endpointError("https://api.deepseek.com") == null, "DeepSeek HTTPS URL");
        require(SettingsValidation.endpointError("https://agi.example.edu/v1") == null, "AGI versioned URL");
        require(SettingsValidation.endpointError("http://localhost:8000/v1") == null, "Local development proxy");
        require(SettingsValidation.endpointError("http://127.0.0.1:8000") == null, "IPv4 loopback proxy");
        require(SettingsValidation.endpointError("http://[::1]:8000") == null, "IPv6 loopback proxy");
        require(SettingsValidation.endpointError("http://api.example.edu") != null, "Remote credentials require TLS");
        require(SettingsValidation.endpointError("https://token@api.example.edu") != null, "No credentials in XML URL");
        require(SettingsValidation.endpointError("https://api.example.edu?key=secret") != null, "No query secrets in XML URL");
        require(SettingsValidation.endpointError("https://api.example.edu#key") != null, "No URL fragments");
        SettingsSnapshot agi = new SettingsSnapshot("AGI", "", "", "", false);
        require(SettingsValidation.validate(agi, original).size() == 2, "Live AGI needs endpoint and model");
        require(SettingsValidation.validate(agi, agi).isEmpty(), "Unchanged legacy fields do not block unrelated edits");
        SettingsSnapshot demoAgi = new SettingsSnapshot("AGI", "", "", "", true);
        require(SettingsValidation.validate(demoAgi, original).isEmpty(), "Demo mode needs no live connection");
        System.out.println("Settings validation checks passed: " + checks);
    }
}
