package dev.mc2p.plugin.facade;

/** Raised by facade operations when the server-side action fails. */
public final class FacadeException extends RuntimeException {

    public FacadeException(String message) {
        super(message);
    }

    public FacadeException(String message, Throwable cause) {
        super(message, cause);
    }
}