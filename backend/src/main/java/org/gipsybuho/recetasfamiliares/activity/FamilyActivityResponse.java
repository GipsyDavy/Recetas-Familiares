package org.gipsybuho.recetasfamiliares.activity;

public record FamilyActivityResponse(boolean recipe, boolean note, boolean stock) {

    static FamilyActivityResponse from(java.util.Map<FamilySection, Boolean> unseen) {
        return new FamilyActivityResponse(
                Boolean.TRUE.equals(unseen.get(FamilySection.RECIPE)),
                Boolean.TRUE.equals(unseen.get(FamilySection.NOTE)),
                Boolean.TRUE.equals(unseen.get(FamilySection.STOCK))
        );
    }
}
