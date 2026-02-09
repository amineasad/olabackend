package tn.esprit.pfe.backendpfe.controllers;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.pfe.backendpfe.dto.SendMailRequest;
import tn.esprit.pfe.backendpfe.services.EmailService;

@RestController
@RequestMapping("/api/mail")
@CrossOrigin(origins = "http://localhost:4200")
public class MailController {

    private final EmailService emailService;

    public MailController(EmailService emailService) {
        this.emailService = emailService;
    }

    @PostMapping("/send-dashboard-ready")
    public ResponseEntity<String> sendDashboardReady(@Valid @RequestBody SendMailRequest request) {
        emailService.sendDashboardReadyMail(request);
        return ResponseEntity.ok("Mail envoyé à : " + request.getTo());
    }
}
