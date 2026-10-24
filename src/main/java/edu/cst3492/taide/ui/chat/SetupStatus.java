package edu.cst3492.taide.ui.chat;

import java.util.Objects;

/** A detached, inexpensive snapshot; filesystem and credential checks belong off the EDT. */
public record SetupStatus(boolean checked, boolean modelReady, boolean apiReady,
                          boolean indexReady, boolean preparing, String detail) {
    public SetupStatus { detail = Objects.requireNonNullElse(detail, ""); }
    public static SetupStatus unknown() { return new SetupStatus(false, false, false, false, false, ""); }
    public boolean ready() { return checked && modelReady && apiReady && indexReady && !preparing; }
}
