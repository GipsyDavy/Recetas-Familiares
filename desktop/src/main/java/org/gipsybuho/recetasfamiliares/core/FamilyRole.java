package org.gipsybuho.recetasfamiliares.core;

public enum FamilyRole {
    OWNER, ADMIN, MEMBER;

    /** Returns true for roles that have family-administration privileges. */
    public boolean isAdminOrAbove() {
        return this == OWNER || this == ADMIN;
    }

    public String displayName() {
        return switch (this) {
            case OWNER  -> "Propietario";
            case ADMIN  -> "Administrador";
            case MEMBER -> "Miembro";
        };
    }
}
