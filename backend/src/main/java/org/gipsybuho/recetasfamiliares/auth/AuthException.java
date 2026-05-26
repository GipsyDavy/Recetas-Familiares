package org.gipsybuho.recetasfamiliares.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class AuthException extends ResponseStatusException {

    public AuthException(HttpStatus status, String reason) {
        super(status, reason);
    }
}
