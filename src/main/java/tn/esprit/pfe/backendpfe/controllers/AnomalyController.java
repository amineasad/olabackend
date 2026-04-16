package tn.esprit.pfe.backendpfe.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.pfe.backendpfe.services.AnomalyService;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/anomalies")
@CrossOrigin
public class AnomalyController {

    private final AnomalyService anomalyService;

    public AnomalyController(AnomalyService anomalyService) {
        this.anomalyService = anomalyService;
    }

    // =========================================================
    // GET /api/anomalies
    //
    // Paramètres :
    //   affiliate   : "OETN"
    //   years       : "2023,2024,2025"
    //   targetMonth : "2025-08" (optionnel)
    //
    // Exemple :
    // GET /api/anomalies?affiliate=OETN&years=2023,2024,2025
    // GET /api/anomalies?affiliate=OETN&years=2023,2024,2025&targetMonth=2025-08
    // =========================================================
    @GetMapping
    public ResponseEntity<Map<String, Object>> detect(
            @RequestParam String affiliate,
            @RequestParam String years,
            @RequestParam(required = false) String targetMonth
    ) {
        try {
            // Parser les années "2023,2024,2025" → [2023, 2024, 2025]
            String cleanYears = years.replace("%2C", ",").replace(" ", "");
            List<Integer> yearList = Arrays.stream(cleanYears.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());

            if (yearList.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Aucune année valide. Exemple: 2023,2024,2025"));
            }

            Map<String, Object> result = anomalyService.detectAnomalies(
                    affiliate, yearList, targetMonth
            );

            return ResponseEntity.ok(result);

        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Format années invalide. Exemple: 2023,2024,2025"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Erreur serveur : " + e.getMessage()));
        }
    }
}