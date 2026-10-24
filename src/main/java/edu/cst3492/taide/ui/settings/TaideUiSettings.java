package edu.cst3492.taide.ui.settings;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/** B-owned preferences. No API key is ever part of the XML state. */
@State(name = "TaideUiSettings", storages = @Storage("taide-ui.xml"))
public final class TaideUiSettings implements PersistentStateComponent<TaideUiSettings.Preferences> {
    private final Project project;
    private volatile Preferences preferences = new Preferences();
    private final AtomicLong revision = new AtomicLong();

    public TaideUiSettings(Project project) { this.project = project; }

    public static TaideUiSettings getInstance(Project project) {
        return project.getService(TaideUiSettings.class);
    }

    public static final class Preferences {
        public String provider = SettingsSnapshot.DEEPSEEK;
        public String baseUrl = SettingsSnapshot.DEFAULT_URL;
        public String model = "deepseek-chat";
        public String courseDirectory = "";
        public boolean demoMode = true;
    }

    @Override public @NotNull Preferences getState() { return copy(snapshot()); }

    @Override public void loadState(@NotNull Preferences state) {
        preferences = copy(new SettingsSnapshot(state.provider, state.baseUrl, state.model,
                state.courseDirectory, state.demoMode));
        revision.incrementAndGet();
    }

    public SettingsSnapshot snapshot() {
        Preferences current = preferences;
        return new SettingsSnapshot(current.provider, current.baseUrl, current.model,
                current.courseDirectory, current.demoMode);
    }

    public void update(SettingsSnapshot value) {
        preferences = copy(value);
        revision.incrementAndGet();
    }

    /** Changes after every successful preference or credential update; services can invalidate caches. */
    public long revision() { return revision.get(); }

    /** Blocking PasswordSafe access: callers must use a worker thread. */
    public @Nullable String readApiKey() { return readApiKey(snapshot()); }

    public @Nullable String readApiKey(String provider) {
        return readApiKey(forProvider(provider));
    }

    public @Nullable String readApiKey(SettingsSnapshot value) {
        ApplicationManager.getApplication().assertIsNonDispatchThread();
        return PasswordSafe.getInstance().getPassword(attributes(value));
    }

    /** Consumes and clears the provided array even if secure storage fails. */
    public void saveApiKey(String provider, char[] key) {
        saveApiKey(forProvider(provider), key);
    }

    public void saveApiKey(SettingsSnapshot value, char[] key) {
        try {
            ApplicationManager.getApplication().assertIsNonDispatchThread();
            PasswordSafe.getInstance().setPassword(attributes(value), new String(key));
            revision.incrementAndGet();
        }
        finally { Arrays.fill(key, '\0'); }
    }

    public void clearApiKey(String provider) {
        clearApiKey(forProvider(provider));
    }

    public void clearApiKey(SettingsSnapshot value) {
        ApplicationManager.getApplication().assertIsNonDispatchThread();
        PasswordSafe.getInstance().setPassword(attributes(value), null);
        revision.incrementAndGet();
    }

    private SettingsSnapshot forProvider(String provider) {
        SettingsSnapshot current = snapshot();
        return new SettingsSnapshot(provider, current.baseUrl(), current.model(), current.courseDirectory(), current.demoMode());
    }

    private CredentialAttributes attributes(SettingsSnapshot value) {
        return new CredentialAttributes(CredentialAttributesKt.generateServiceName(
                "TAIDE", project.getLocationHash() + "/" + value.provider() + "/" + value.credentialScope() + "/API key"));
    }

    private static Preferences copy(SettingsSnapshot value) {
        Preferences result = new Preferences();
        result.provider = value.provider();
        result.baseUrl = value.baseUrl();
        result.model = value.model();
        result.courseDirectory = value.courseDirectory();
        result.demoMode = value.demoMode();
        return result;
    }
}
