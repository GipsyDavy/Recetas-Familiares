package org.gipsybuho.recetasfamiliares.families;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateFamilyMemberRequest(
        @NotBlank @Size(max = 120) String displayName,
        @Email @NotBlank @Size(max = 254) String email,
        PasswordAction passwordAction,
        @Size(min = 12, max = 128) String temporaryPassword
) {
    public enum PasswordAction {
        NONE,
        SEND_RESET,
        SET_TEMPORARY
    }
}
