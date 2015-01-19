package com.github.danfickle.cpptojavasourceconverter.helper;

public class ParsingException extends Exception {
    private static final long serialVersionUID = 1L;

    public ParsingException(String message) {
        super(message);
    }
    
    public ParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
