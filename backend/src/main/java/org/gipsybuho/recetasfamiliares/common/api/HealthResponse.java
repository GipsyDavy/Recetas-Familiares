package org.gipsybuho.recetasfamiliares.common.api;

import java.time.Instant;

public record HealthResponse(String status, Instant checkedAt) {
}
