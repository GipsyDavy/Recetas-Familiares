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
@Table(name = "user_section_last_seen")
@IdClass(UserSectionLastSeenEntity.Key.class)
public class UserSectionLastSeenEntity {

    @Id
    @Column(name = "user_id")
    private String userId;

    @Id
    @Column(name = "family_id")
    private String familyId;

    @Id
    @Enumerated(EnumType.STRING)
    private FamilySection section;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected UserSectionLastSeenEntity() {
    }

    public UserSectionLastSeenEntity(String userId, String familyId, FamilySection section, Instant lastSeenAt) {
        this.userId = userId;
        this.familyId = familyId;
        this.section = section;
        this.lastSeenAt = lastSeenAt;
    }

    public String getUserId() {
        return userId;
    }

    public String getFamilyId() {
        return familyId;
    }

    public FamilySection getSection() {
        return section;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void touch(Instant now) {
        this.lastSeenAt = now;
    }

    public static class Key implements java.io.Serializable {
        private String userId;
        private String familyId;
        private FamilySection section;

        public Key() {
        }

        public Key(String userId, String familyId, FamilySection section) {
            this.userId = userId;
            this.familyId = familyId;
            this.section = section;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return userId.equals(key.userId) && familyId.equals(key.familyId) && section == key.section;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(userId, familyId, section);
        }
    }
}
