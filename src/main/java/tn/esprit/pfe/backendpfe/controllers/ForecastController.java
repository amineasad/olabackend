package tn.esprit.pfe.backendpfe.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.pfe.backendpfe.services.ForecastService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/forecast")
@CrossOrigin
public class ForecastController {

    private final ForecastService forecastService;

    public ForecastController(ForecastService forecastService) {
        this.forecastService = forecastService;
    }

    // =========================================================
    // GET /api/forecast
    // Paramètres :
    //   affiliate : "OETN"
    //   kpiCode   : "Number_of_deliveries"
    //   years     : "2024,2025"
    //   periods   : 1 (défaut) | 2 | 3
    //
    // Exemple :
    // GET /api/forecast?affiliate=OETN&kpiCode=Number_of_deliveries&years=2024,2025&periods=1
    // =========================================================
    @GetMapping
    public ResponseEntity<Map<String, Object>> forecast(
            @RequestParam String affiliate,
            @RequestParam String kpiCode,
            @RequestParam String years,
            @RequestParam(defaultValue = "1") int periods
    ) {
        try {
            // Convertir "2024,2025" → [2024, 2025]
            List<Integer> yearList = java.util.Arrays.stream(years.split(","))
                    .map(String::trim)
                    .map(Integer::parseInt)
                    .toList();

            if (periods < 1 || periods > 3) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "periods doit être entre 1 et 3"));
            }

            Map<String, Object> result = forecastService.forecast(
                    affiliate, kpiCode, yearList, periods
            );

            if (result.containsKey("error")) {
                return ResponseEntity.badRequest().body(result);
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Erreur serveur : " + e.getMessage()));
        }
    }
}