CREATE TABLE family_section_activity (
    family_id VARCHAR(36) NOT NULL,
    section VARCHAR(20) NOT NULL,
    last_activity_at timestamptz NOT NULL,
    PRIMARY KEY (family_id, section),
    CONSTRAINT fk_family_section_activity_family FOREIGN KEY (family_id) REFERENCES families (id)
);

CREATE TABLE user_section_last_seen (
    user_id VARCHAR(36) NOT NULL,
    family_id VARCHAR(36) NOT NULL,
    section VARCHAR(20) NOT NULL,
    last_seen_at timestamptz NOT NULL,
    PRIMARY KEY (user_id, family_id, section),
    CONSTRAINT fk_user_section_last_seen_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_section_last_seen_family FOREIGN KEY (family_id) REFERENCES families (id)
);
