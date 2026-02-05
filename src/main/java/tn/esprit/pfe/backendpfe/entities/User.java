package tn.esprit.pfe.backendpfe.entities;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
        indexes = @Index(name = "idx_users_email", columnList = "email")
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ====== IDENTITÉ ======
    @Column(nullable = false, length = 100)
    private String nom;

    @Column(nullable = false, length = 100)
    private String prenom;

    @Column(nullable = false, length = 150)
    private String email;

    /**
     * IMPORTANT : Stocke uniquement un HASH (BCrypt), jamais le mot de passe en clair.
     * Exemple valeur : "$2a$10$..."
     */
    @JsonIgnore
    @Column(nullable = false, length = 255)
    private String motDePasseHash;

    // ====== PROFIL OLA ENERGY ======
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role; // MANAGER / ADMIN

    @Column(nullable = false, length = 100)
    private String poste; // ex: "Manager Logistique", "Directeur Commercial"

    @Column(nullable = false, length = 100)
    private String departement; // ex: "Ventes", "Logistique", "Finance"

    @Column(length = 100)
    private String region; // ex: "Tunis", "Sfax", "National"

    @Column(length = 20)
    private String telephone;


    /**
     * Stockage photo éventuelle (Base64) ou URL.
     * Pour PostgreSQL : TEXT
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String photoProfil;











    public String getFullName() {
        return prenom + " " + nom;
    }




}
