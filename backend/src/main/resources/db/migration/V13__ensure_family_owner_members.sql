UPDATE family_members
SET role = 'OWNER',
    sync_version = sync_version + 1,
    updated_at = now()
WHERE id IN (
    SELECT repair.owner_member_id
    FROM (
        SELECT candidate.family_id, MIN(candidate.id) AS owner_member_id
        FROM family_members candidate
        JOIN users candidate_user
          ON candidate_user.id = candidate.user_id
         AND candidate_user.deleted = FALSE
        LEFT JOIN family_members existing_owner
          ON existing_owner.family_id = candidate.family_id
         AND existing_owner.role = 'OWNER'
         AND existing_owner.deleted = FALSE
        WHERE candidate.deleted = FALSE
          AND existing_owner.id IS NULL
          AND candidate.created_at = (
              SELECT MIN(oldest.created_at)
              FROM family_members oldest
              JOIN users oldest_user
                ON oldest_user.id = oldest.user_id
               AND oldest_user.deleted = FALSE
              WHERE oldest.family_id = candidate.family_id
                AND oldest.deleted = FALSE
          )
        GROUP BY candidate.family_id
    ) repair
);
