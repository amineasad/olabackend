package tn.esprit.pfe.backendpfe.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import tn.esprit.pfe.backendpfe.dto.SendMailRequest;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendDashboardReadyMail(SendMailRequest req) {

        String year = (req.getYear() == null || req.getYear().isBlank()) ? "cette année" : req.getYear();
        String url  = (req.getDashboardUrl() == null || req.getDashboardUrl().isBlank())
                ? "http://localhost:4200/login"
                : req.getDashboardUrl();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(req.getTo());
        message.setSubject("Dashboard " + year + " prêt – Connexion requise");

        message.setText(
                "Bonjour,\n\n" +
                        "Le dashboard logistique de " + year + " est désormais prêt.\n" +
                        "Merci de vous connecter pour le consulter via le lien ci-dessous :\n\n" +
                        url + "\n\n" +
                        "Cordialement.(kounji aal faza)"
        );

        mailSender.send(message);
    }
}
