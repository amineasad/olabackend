package tn.esprit.pfe.backendpfe.services;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.pfe.backendpfe.dto.KpiAvgDto;
import tn.esprit.pfe.backendpfe.entities.KpiValue;
import tn.esprit.pfe.backendpfe.repositories.KpiValueRepository;
import tn.esprit.pfe.backendpfe.util.ExcelReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class KpiService {

    private final KpiValueRepository repository;
    private final ExcelReader excelReader;

    // ✅ Ordre chronologique des mois
    private static final List<String> MONTH_ORDER = List.of(
            "Jan", "Feb", "Mar", "Apr", "May", "June",
            "July", "August", "September", "October", "November", "December"
    );

    public KpiService(KpiValueRepository repository, ExcelReader excelReader) {
        this.repository = repository;
        this.excelReader = excelReader;
    }

    public int importExcel(MultipartFile file) throws IOException {
        List<KpiValue> kpis = excelReader.readAllKpis(file);

        if (kpis.isEmpty()) {
            throw new RuntimeException("Aucune donnée KPI trouvée dans le fichier");
        }

        repository.deleteAll();
        repository.saveAll(kpis);

        return kpis.size();
    }

    public List<KpiValue> getKpis(String affiliate, String month, int year) {
        return repository.findByAffiliateAndMonthAndYear(affiliate, month, year);
    }

    public List<String> getAffiliates() {
        return repository.findDistinctAffiliates();
    }

    public List<Integer> getYears() {
        return repository.findDistinctYears();
    }

    // ✅ Tri chronologique Jan → Dec en Java (évite l'erreur PostgreSQL avec DISTINCT + ORDER BY CASE)
    public List<String> getMonths(String affiliate, int year) {
        List<String> months = new ArrayList<>(repository.findDistinctMonths(affiliate, year));
        months.sort(Comparator.comparingInt(m -> {
            int idx = MONTH_ORDER.indexOf(m);
            return idx == -1 ? 99 : idx;
        }));
        return months;
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