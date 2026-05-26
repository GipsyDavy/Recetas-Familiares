package org.gipsybuho.recetasfamiliares.common.api;

import java.time.Instant;
import java.util.List;

public record ApiError(
        String code,
        String message,
        int status,
        String path,
        Instant timestamp,
        List<FieldErrorDetail> fields
) {
    public static ApiError of(String code, String message, int status, String path) {
        return new ApiError(code, message, status, path, Instant.now(), List.of());
    }
}
