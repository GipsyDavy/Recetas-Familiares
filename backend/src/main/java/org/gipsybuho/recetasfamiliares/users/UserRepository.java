package org.gipsybuho.recetasfamiliares.users;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, String> {

    boolean existsByEmailIgnoreCaseAndDeletedFalse(String email);

    Optional<UserEntity> findByEmailIgnoreCaseAndDeletedFalse(String email);

    Optional<UserEntity> findByIdAndDeletedFalse(String id);
}
