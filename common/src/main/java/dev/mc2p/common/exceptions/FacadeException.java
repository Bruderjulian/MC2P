package dev.mc2p.common.exceptions;

/** Raised by facade operations when the server-side action fails. */
public final class FacadeException extends RuntimeException {

    public FacadeException(final String message) {
        super(message);
    }

    public FacadeException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
