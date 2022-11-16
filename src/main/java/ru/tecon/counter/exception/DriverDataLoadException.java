package ru.tecon.counter.exception;

public class DriverDataLoadException extends Exception {

    public DriverDataLoadException(String message) {
        super(message);
    }

    public DriverDataLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
