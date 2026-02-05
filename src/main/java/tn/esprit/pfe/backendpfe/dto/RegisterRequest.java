package tn.esprit.pfe.backendpfe.dto;

import tn.esprit.pfe.backendpfe.entities.Role;

public class RegisterRequest {

    private String nom;
    private String prenom;
    private String email;
    private String motDePasse;

    private String poste;
    private String departement;
    private String region;
    private String telephone;

    private Role role; // optionnel

    // getters / setters
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getMotDePasse() { return motDePasse; }
    public void setMotDePasse(String motDePasse) { this.motDePasse = motDePasse; }

    public String getPoste() { return poste; }
    public void setPoste(String poste) { this.poste = poste; }

    public String getDepartement() { return departement; }
    public void setDepartement(String departement) { this.departement = departement; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
}