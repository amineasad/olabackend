package tn.esprit.pfe.backendpfe.services;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.pfe.backendpfe.dto.KpiAvgDto;
import tn.esprit.pfe.backendpfe.entities.KpiValue;
import tn.esprit.pfe.backendpfe.repositories.KpiValueRepository;
import tn.esprit.pfe.backendpfe.util.ExcelReader;

import java.io.IOException;
import java.util.List;

@Service
public class KpiService {

    private final KpiValueRepository repository;
    private final ExcelReader excelReader;

    public KpiService(KpiValueRepository repository, ExcelReader excelReader) {
        this.repository = repository;
        this.excelReader = excelReader;
    }

    /**
     * Import Excel :
     * - lit toutes les feuilles
     * - transforme en KPI mensuels
     * - supprime l’existant
     * - insère les nouvelles données
     */
    public int importExcel(MultipartFile file) throws IOException {

        // 1️⃣ Lire toutes les données Excel
        List<KpiValue> kpis = excelReader.readAllKpis(file);

        if (kpis.isEmpty()) {
            throw new RuntimeException("Aucune donnée KPI trouvée dans le fichier");
        }

        // 2️⃣ Nettoyage (MVP)
        repository.deleteAll();

        // 3️⃣ Sauvegarde
        repository.saveAll(kpis);

        return kpis.size();
    }

    /**
     * Récupérer les KPI pour le dashboard
     */
    public List<KpiValue> getKpis(String affiliate, String month, int year) {
        return repository.findByAffiliateAndMonthAndYear(affiliate, month, year);
    }

    /**
     * Filtres dynamiques
     */
    public List<String> getAffiliates() {
        return repository.findDistinctAffiliates();
    }

    public List<Integer> getYears() {
        return repository.findDistinctYears();
    }

    public List<String> getMonths(String affiliate, int year) {
        return repository.findDistinctMonths(affiliate, year);
    }
    public List<String> getCategories(String affiliate, int year) {
        return repository.findDistinctCategoriesByAffiliateAndYear(affiliate, year);
    }

    public List<KpiValue> getKpis(String affiliate, String month, int year, String category) {
        return repository.findKpisWithOptionalCategory(affiliate, month, year, category);
    }
    public List<KpiAvgDto> getKpisAverage(String affiliate, int year, String category) {
        return repository.avgByKpi(affiliate, year, category).stream()
                .map(o -> new KpiAvgDto(
                        (String) o[0],
                        affiliate,
                        "AVG",
                        year,
                        ((Number) o[1]).doubleValue()
                ))
                .toList();
    }

}
