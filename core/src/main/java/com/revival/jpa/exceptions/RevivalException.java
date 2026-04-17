package com.revival.jpa.exceptions;

/**
 * Custom unchecked exception for the Revival JPA framework.
 * Wraps low-level database errors into human-readable messages for Junior
 * developers.
 */
public class RevivalException extends RuntimeException {

    // Constructor para mensajes simples
    public RevivalException(String message) {
        super("Revival Error: " + message);
    }

    // Constructor para cuando capturamos otra excepción por debajo (ej.
    // SQLException)
    public RevivalException(String message, Throwable cause) {
        super("Revival Error: " + message, cause);
    }
}