package dev.mc2p.common.exceptions;

/** Thrown when an audit entry cannot be persisted. */
public class AuditWriteException extends RuntimeException {

  public AuditWriteException(final String message) {
    super(message);
  }

  public AuditWriteException(final String message, final Throwable cause) {
    super(message, cause);
  }
}