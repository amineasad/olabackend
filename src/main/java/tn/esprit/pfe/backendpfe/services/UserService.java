package tn.esprit.pfe.backendpfe.services;



import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.pfe.backendpfe.entities.Role;

import tn.esprit.pfe.backendpfe.entities.User;
import tn.esprit.pfe.backendpfe.repositories.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * ✅ Enregistrement (Manager Ola Energy)
     * - Vérifie email unique
     * - Valide champs obligatoires (poste, departement, nom, prenom, email, motDePasse)
     * - Hash le mot de passe -> motDePasseHash
     * - Définit Role par défaut si null
     */
    @Transactional
    public User register(User user, String motDePasseClair) {
        // Normaliser email
        if (user.getEmail() != null) {
            user.setEmail(user.getEmail().trim().toLowerCase());
        }

        // Email unique
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Cet email est déjà utilisé");
        }

        // Validation champs requis
        validateUserRequirements(user, motDePasseClair);

        // Rôle par défaut
        if (user.getRole() == null) {
            user.setRole(Role.MANAGER);
        }

        // Hash mot de passe
        user.setMotDePasseHash(passwordEncoder.encode(motDePasseClair));

        // Sauvegarder
        return userRepository.save(user);
    }

    private void validateUserRequirements(User user, String motDePasseClair) {
        if (user.getNom() == null || user.getNom().isBlank()) {
            throw new RuntimeException("Le nom est obligatoire");
        }
        if (user.getPrenom() == null || user.getPrenom().isBlank()) {
            throw new RuntimeException("Le prénom est obligatoire");
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new RuntimeException("L'email est obligatoire");
        }
        if (motDePasseClair == null || motDePasseClair.isBlank()) {
            throw new RuntimeException("Le mot de passe est obligatoire");
        }
        if (motDePasseClair.length() < 8) {
            throw new RuntimeException("Le mot de passe doit contenir au moins 8 caractères");
        }
        if (user.getPoste() == null || user.getPoste().isBlank()) {
            throw new RuntimeException("Le poste est obligatoire");
        }
        if (user.getDepartement() == null || user.getDepartement().isBlank()) {
            throw new RuntimeException("Le département est obligatoire");
        }
        // region et telephone sont optionnels
    }

    /**
     * ✅ Login
     */
    public User login(String email, String password) {
        String normalizedEmail = (email == null) ? null : email.trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        if (!passwordEncoder.matches(password, user.getMotDePasseHash())) {
            throw new RuntimeException("Mot de passe incorrect");
        }

        return user;
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé: " + id));
    }
}

