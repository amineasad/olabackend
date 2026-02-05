package tn.esprit.pfe.backendpfe.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.pfe.backendpfe.dto.LoginRequest;
import tn.esprit.pfe.backendpfe.dto.RegisterRequest;
import tn.esprit.pfe.backendpfe.entities.User;
import tn.esprit.pfe.backendpfe.services.UserService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin("*")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // ================= REGISTER =================
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            User user = new User();
            user.setNom(request.getNom());
            user.setPrenom(request.getPrenom());
            user.setEmail(request.getEmail());
            user.setPoste(request.getPoste());
            user.setDepartement(request.getDepartement());
            user.setRegion(request.getRegion());
            user.setTelephone(request.getTelephone());
            user.setRole(request.getRole()); // optionnel

            User savedUser = userService.register(user, request.getMotDePasse());

            return ResponseEntity.ok(savedUser);

        } catch (RuntimeException e) {
            // Renvoie un objet JSON avec le message d'erreur
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    // ================= LOGIN =================
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            User user = userService.login(request.getEmail(), request.getMotDePasse());
            return ResponseEntity.ok(user);

        } catch (RuntimeException e) {
            // Renvoie un objet JSON avec le message d'erreur
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }
}