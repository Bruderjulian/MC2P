package dev.mc2p.plugin.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class FacadeExceptionTest {

    @Test
    void plainConstructor() {
        FacadeException exception = new FacadeException("boom");
        assertEquals("boom", exception.getMessage());
    }

    @Test
    void causeConstructor() {
        IllegalStateException cause = new IllegalStateException("root cause");
        FacadeException exception = new FacadeException("boom", cause);
        assertEquals("boom", exception.getMessage());
        assertSame(cause, exception.getCause());
    }
}