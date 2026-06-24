package com.example.backend.storage.exception;

public class StoredFileNotFoundException extends RuntimeException {

    public StoredFileNotFoundException(String message) {
        super(message);
    }
}
