package org.gipsybuho.recetasfamiliares.activity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.IdClass;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "family_section_activity")
@IdClass(FamilySectionActivityEntity.Key.class)
public class FamilySectionActivityEntity {

    @Id
    @Column(name = "family_id")
    private String familyId;

    @Id
    @Enumerated(EnumType.STRING)
    private FamilySection section;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    protected FamilySectionActivityEntity() {
    }

    public FamilySectionActivityEntity(String familyId, FamilySection section, Instant lastActivityAt) {
        this.familyId = familyId;
        this.section = section;
        this.lastActivityAt = lastActivityAt;
    }

    public String getFamilyId() {
        return familyId;
    }

    public FamilySection getSection() {
        return section;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public void touch(Instant now) {
        this.lastActivityAt = now;
    }

    public static class Key implements java.io.Serializable {
        private String familyId;
        private FamilySection section;

        public Key() {
        }

        public Key(String familyId, FamilySection section) {
            this.familyId = familyId;
            this.section = section;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return familyId.equals(key.familyId) && section == key.section;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(familyId, section);
        }
    }
}
