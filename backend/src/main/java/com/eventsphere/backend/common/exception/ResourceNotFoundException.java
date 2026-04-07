package com.eventsphere.backend.common.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String resourceName, Object id) {
        return new ResourceNotFoundException(resourceName + " not found with id: " + id);
    }
}