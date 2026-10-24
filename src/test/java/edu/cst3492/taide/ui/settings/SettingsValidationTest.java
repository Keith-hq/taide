package edu.cst3492.taide.ui.settings;

/** Run with main(); no network, IDE instance, or test-framework dependency is required. */
public final class SettingsValidationTest {
    private static int checks;

    private static void require(boolean condition, String behavior) {
        checks++;
        if (!condition) throw new AssertionError(behavior);
    }

    public static void main(String[] args) {
        checks = 0;
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
        require(SettingsValidation.validate(agi, agi).size() == 2, "Invalid legacy connection cannot bypass live validation");
        SettingsSnapshot demoAgi = new SettingsSnapshot("AGI", "", "", "", true);
        require(SettingsValidation.validate(demoAgi, original).isEmpty(), "Demo mode needs no live connection");
        require(SettingsValidation.validate(new SettingsSnapshot("AGI", "https://host.example?token=secret", "", "", true),
                original).size() == 1, "Demo mode cannot persist a query secret in settings XML");
        require(SettingsValidation.endpointError("https://host.example:65536/v1") != null, "Reject overflowing API port");
        require(SettingsValidation.endpointError("https://host.example:0/v1") != null, "Reject zero API port");
        require(SettingsValidation.endpointError("https://host.example:8443/v1") == null, "Allow HTTPS alternate port");
        require(SettingsValidation.endpointError("https://localhost.evil.example") == null, "TLS works for remote hostname");
        require(SettingsValidation.endpointError("http://localhost.evil.example") != null, "Loopback suffix is not loopback");
        SettingsSnapshot mixedCase = new SettingsSnapshot("AGI", "HTTPS://API.EXAMPLE.EDU:443/v1/", "m", "", false);
        SettingsSnapshot canonical = new SettingsSnapshot("AGI", "https://api.example.edu/v1", "m", "", false);
        require(mixedCase.credentialScope().equals(canonical.credentialScope()), "Equivalent URLs use the same key scope");
        require(!new SettingsSnapshot("AGI", "https://other.example.edu/v1", "m", "", false).credentialScope()
                .equals(canonical.credentialScope()), "Different hosts never share API keys");
        require(!new SettingsSnapshot("AGI", "https://api.example.edu/other", "m", "", false).credentialScope()
                .equals(canonical.credentialScope()), "Different API paths have separate API keys");
        require(!new SettingsSnapshot("AGI", "https://api.example.edu:8443/v1", "m", "", false).credentialScope()
                .equals(canonical.credentialScope()), "Different ports have separate API keys");
        System.out.println("Settings validation checks passed: " + checks);
    }
}
