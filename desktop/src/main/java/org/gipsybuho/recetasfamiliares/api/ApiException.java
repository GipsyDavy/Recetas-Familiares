package org.gipsybuho.recetasfamiliares.api;

public class ApiException extends RuntimeException {

    private final int httpStatus;

    public ApiException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() { return httpStatus; }

    public boolean isUnauthorized() { return httpStatus == 401; }
    public boolean isForbidden() { return httpStatus == 403; }
}
