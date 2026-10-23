package edu.cst3492.taide.service;

public class TaideException extends Exception {
    public TaideException(String message) { super(message); }
    public TaideException(String message, Throwable cause) { super(message, cause); }
}
