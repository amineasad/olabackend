package tn.esprit.pfe.backendpfe.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.pfe.backendpfe.services.KpiSummaryService;

@RestController
@RequestMapping("/api/kpi")
@CrossOrigin(origins = "*")
public class KpiSummaryController {

    private final KpiSummaryService summaryService;

    public KpiSummaryController(KpiSummaryService summaryService) {
        this.summaryService = summaryService;
    }

    /**
     * GET /api/kpi/summary?affiliate=OERE&year=2025&category=ALL&month=ALL
     *
     * month = "ALL" ou absent  → résumé annuel  (tendances 12 mois)
     * month = "Jan","Feb"...   → résumé mensuel (valeur mois vs moyenne)
     */
    @GetMapping("/summary")
    public ResponseEntity<SummaryResponse> getSummary(
            @RequestParam String affiliate,
            @RequestParam int    year,
            @RequestParam(defaultValue = "ALL")  String category,
            @RequestParam(defaultValue = "ALL")  String month) {

        if (affiliate == null || affiliate.isBlank())
            return ResponseEntity.badRequest()
                    .body(new SummaryResponse("Affilié requis.", false));

        String summary = summaryService.generateSummary(affiliate, year, category, month);
        return ResponseEntity.ok(new SummaryResponse(summary, true));
    }

    public record SummaryResponse(String summary, boolean success) {}
}