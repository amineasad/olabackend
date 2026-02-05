package tn.esprit.pfe.backendpfe.controllers;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.pfe.backendpfe.entities.KpiValue;
import tn.esprit.pfe.backendpfe.services.KpiService;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class KpiController {

    private final KpiService service;

    public KpiController(KpiService service) {
        this.service = service;
    }

    @PostMapping(value = "/kpis/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> importExcel(@RequestParam("file") MultipartFile file) {
        try {
            int count = service.importExcel(file);
            return ResponseEntity.ok("Import réussi. Lignes insérées: " + count);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erreur import: " + e.getMessage());
        }
    }

    @GetMapping("/kpis")
    public List<KpiValue> getKpis(@RequestParam String affiliate,
                                  @RequestParam String month,
                                  @RequestParam int year,
                                  @RequestParam(required = false) String category) {
        return service.getKpis(affiliate, month, year, category);
    }
    @GetMapping("/filters/categories")
    public List<String> categories(@RequestParam String affiliate, @RequestParam int year) {
        return service.getCategories(affiliate, year);
    }


    @GetMapping("/filters/affiliates")
    public List<String> affiliates() { return service.getAffiliates(); }

    @GetMapping("/filters/years")
    public List<Integer> years() { return service.getYears(); }

    @GetMapping("/filters/months")
    public List<String> months(@RequestParam String affiliate, @RequestParam int year) {
        return service.getMonths(affiliate, year);
    }
    @GetMapping("/kpis/average")
    public List<?> getKpisAverage(@RequestParam String affiliate,
                                  @RequestParam int year,
                                  @RequestParam(required = false) String category) {
        return service.getKpisAverage(affiliate, year, category);
    }


}
