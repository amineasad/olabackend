package tn.esprit.pfe.backendpfe.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.pfe.backendpfe.entities.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
