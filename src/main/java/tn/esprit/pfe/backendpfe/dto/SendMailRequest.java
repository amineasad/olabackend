package tn.esprit.pfe.backendpfe.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class SendMailRequest {

    @NotBlank
    @Email
    private String to;

    // optionnel
    private String year;

    // optionnel
    private String dashboardUrl;

    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }

    public String getYear() { return year; }
    public void setYear(String year) { this.year = year; }

    public String getDashboardUrl() { return dashboardUrl; }
    public void setDashboardUrl(String dashboardUrl) { this.dashboardUrl = dashboardUrl; }
}
