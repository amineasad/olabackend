package tn.esprit.pfe.backendpfe.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import tn.esprit.pfe.backendpfe.repositories.KpiValueRepository;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@Service
public class AnomalyService {

    private final KpiValueRepository kpiRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    // KPIs envoyés au modèle Python (alignés avec ton modèle final)
    private static final List<String> ALL_KPIS = List.of(
            "Number_of_deliveries",
            "Planned_orders",
            "On_time_delivery_rate",
            "Distance_km",
            "Total_Fleet_OPEX_EUR",
            "Unit_Cost_per_m3",
            "Plan_Unit_Cost_per_m3_EUR",
            "Total_volume_m3",
            "Fleet_utilization_rate",
            "Number_of_loadings",
            "Total_delivery_hours",

            // 🔥 EXACT NAMES FROM EXCEL
            "Number_of_trucks_operating_during_the_month",
            "Total Drivers",
            "Monthly Driver Violations",

            // règles métier
            "Delivery Truck Accident",
            "Spill/Cross-Fuel Incident"
    );

    private static final List<String> MONTH_ORDER = List.of(
            "Jan","Feb","Mar","Apr","May","June",
            "July","August","September","October","November","December"
    );

    private static final Map<String, String> MONTH_TO_NUM = new LinkedHashMap<>();
    static {
        MONTH_TO_NUM.put("Jan",      "01");
        MONTH_TO_NUM.put("Feb",      "02");
        MONTH_TO_NUM.put("Mar",      "03");
        MONTH_TO_NUM.put("Apr",      "04");
        MONTH_TO_NUM.put("May",      "05");
        MONTH_TO_NUM.put("June",     "06");
        MONTH_TO_NUM.put("July",     "07");
        MONTH_TO_NUM.put("August",   "08");
        MONTH_TO_NUM.put("September","09");
        MONTH_TO_NUM.put("October",  "10");
        MONTH_TO_NUM.put("November", "11");
        MONTH_TO_NUM.put("December", "12");
    }

    public AnomalyService(KpiValueRepository kpiRepo) {
        this.kpiRepo = kpiRepo;
    }

    // =========================================================
    // Méthode principale
    // =========================================================
    public Map<String, Object> detectAnomalies(String affiliate,
                                               List<Integer> years,
                                               String targetMonth) {
        try {
            List<Map<String, Object>> history = buildHistory(affiliate, years);

            if (history.isEmpty()) {
                return Map.of("error", "Aucune donnée trouvée pour " + affiliate);
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("history", history);
            payload.put("target_month", targetMonth);

            String json = mapper.writeValueAsString(payload);

            // DEBUG (optionnel)
            System.out.println("Affiliate: " + affiliate);
            System.out.println("Years: " + years);
            System.out.println("History size: " + history.size());

            return callPythonViaFile(json);

        } catch (Exception e) {
            System.err.println("AnomalyService error: " + e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    // =========================================================
    // Construction historique
    // =========================================================
    private List<Map<String, Object>> buildHistory(String affiliate, List<Integer> years) {

        List<Map<String, Object>> history = new ArrayList<>();

        for (int year : years) {

            Map<String, Map<String, Double>> yearData = new HashMap<>();

            for (String kpi : ALL_KPIS) {
                try {
                    List<Object[]> rows = kpiRepo.seriesByMonth(affiliate, year, kpi, "ALL");

                    Map<String, Double> monthMap = new HashMap<>();

                    for (Object[] row : rows) {
                        String month = (String) row[0];
                        Double value = row[1] != null ? ((Number) row[1]).doubleValue() : null;

                        if (value != null) {
                            monthMap.put(month, value);
                        }
                    }

                    yearData.put(kpi, monthMap);

                } catch (Exception e) {
                    yearData.put(kpi, new HashMap<>());
                }
            }

            for (String month : MONTH_ORDER) {

                String monthNum = MONTH_TO_NUM.get(month);
                String monthStr = year + "-" + monthNum;

                Map<String, Object> kpisMap = new HashMap<>();

                for (String kpi : ALL_KPIS) {
                    Double val = yearData.getOrDefault(kpi, new HashMap<>()).get(month);
                    if (val != null) {
                        kpisMap.put(kpi, val);
                    }
                }

                // éviter mois avec peu de données
                if (kpisMap.size() >= 3) {
                    Map<String, Object> point = new HashMap<>();
                    point.put("month", monthStr);
                    point.put("kpis", kpisMap);
                    history.add(point);
                }
            }
        }

        return history;
    }

    // =========================================================
    // Appel Python
    // =========================================================
    private Map<String, Object> callPythonViaFile(String json) throws Exception {

        Path tempFile = Files.createTempFile("anomaly_input_", ".json");
        Files.writeString(tempFile, json);

        try {
            String scriptPath = getScriptPath();
            String pythonCmd  = getPythonCommand();

            ProcessBuilder pb = new ProcessBuilder(pythonCmd, scriptPath);
            pb.redirectErrorStream(false);

            Process process = pb.start();

            try (OutputStream os = process.getOutputStream()) {
                os.write(Files.readAllBytes(tempFile));
            }

            String stdout = new String(process.getInputStream().readAllBytes());
            String stderr = new String(process.getErrorStream().readAllBytes());
            int exitCode  = process.waitFor();

            if (!stderr.isBlank()) {
                System.out.println("=== Python logs ===\n" + stderr + "\n==================");
            }

            if (stdout.isBlank()) {
                return Map.of("error", "Aucune réponse Python. Exit=" + exitCode);
            }

            return mapper.readValue(stdout, Map.class);

        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private String getScriptPath() {
        try {
            var resource = getClass().getClassLoader()
                    .getResource("scripts/anomaly_detector.py");

            if (resource != null) {
                return Paths.get(resource.toURI()).toString();
            }

        } catch (Exception ignored) {}

        return "src/main/resources/scripts/anomaly_detector.py";
    }

    private String getPythonCommand() {
        return System.getProperty("os.name").toLowerCase().contains("win")
                ? "python"
                : "python3";
    }
}