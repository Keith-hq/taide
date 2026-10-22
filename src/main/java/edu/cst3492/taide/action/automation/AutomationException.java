package edu.cst3492.taide.action.automation;

public final class AutomationException extends RuntimeException {
    public AutomationException(String message) {
        super(message);
    }

    public AutomationException(String message, Throwable cause) {
        super(message, cause);
    }
}
