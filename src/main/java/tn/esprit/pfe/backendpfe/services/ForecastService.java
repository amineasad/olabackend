package tn.esprit.pfe.backendpfe.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import tn.esprit.pfe.backendpfe.repositories.KpiValueRepository;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@Service
public class ForecastService {

    private final KpiValueRepository kpiRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, String> MONTH_TO_NUM = new LinkedHashMap<>();
    static {
        MONTH_TO_NUM.put("Jan",       "01");
        MONTH_TO_NUM.put("Feb",       "02");
        MONTH_TO_NUM.put("Mar",       "03");
        MONTH_TO_NUM.put("Apr",       "04");
        MONTH_TO_NUM.put("May",       "05");
        MONTH_TO_NUM.put("June",      "06");
        MONTH_TO_NUM.put("July",      "07");
        MONTH_TO_NUM.put("August",    "08");
        MONTH_TO_NUM.put("September", "09");
        MONTH_TO_NUM.put("October",   "10");
        MONTH_TO_NUM.put("November",  "11");
        MONTH_TO_NUM.put("December",  "12");
    }

    private static final List<String> MONTH_ORDER = new ArrayList<>(MONTH_TO_NUM.keySet());

    public ForecastService(KpiValueRepository kpiRepo) {
        this.kpiRepo = kpiRepo;
    }

    public Map<String, Object> forecast(String affiliate,
                                        String kpiCode,
                                        List<Integer> years,
                                        int periods) {
        try {
            // ── 1. Charger historique depuis PostgreSQL ─────────────────
            List<Map<String, Object>> history = loadHistory(affiliate, kpiCode, years);

            if (history.size() < 12) {
                return Map.of(
                        "error", "Minimum 12 mois requis. Disponible : " + history.size(),
                        "affiliate", affiliate,
                        "kpi", kpiCode
                );
            }

            // ── 2. Construire payload JSON ─────────────────────────────
            Map<String, Object> payload = new HashMap<>();
            payload.put("affiliate", affiliate);
            payload.put("kpi",       kpiCode);
            payload.put("history",   history);
            payload.put("periods",   periods);

            String json = mapper.writeValueAsString(payload);

            // ── 3. Appeler Python via fichier temporaire ───────────────
            // On passe par un fichier pour éviter les problèmes
            // de guillemets sur Windows PowerShell
            return callPythonViaFile(json);

        } catch (Exception e) {
            System.err.println("ForecastService error: " + e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    // =========================================================
    // Charge l'historique depuis PostgreSQL
    // =========================================================
    private List<Map<String, Object>> loadHistory(String affiliate,
                                                  String kpiCode,
                                                  List<Integer> years) {
        List<Map<String, Object>> history = new ArrayList<>();

        for (int year : years) {
            List<Object[]> rows = kpiRepo.seriesByMonth(affiliate, year, kpiCode, "ALL");

            rows.sort(Comparator.comparingInt(r -> MONTH_ORDER.indexOf((String) r[0])));

            for (Object[] row : rows) {
                String month = (String) row[0];
                Double value = row[1] != null ? ((Number) row[1]).doubleValue() : null;
                if (value == null) continue;

                String monthNum = MONTH_TO_NUM.get(month);
                if (monthNum == null) continue;

                String ds = year + "-" + monthNum + "-01";

                Map<String, Object> point = new HashMap<>();
                point.put("ds", ds);
                point.put("y",  value);
                history.add(point);
            }
        }

        return history;
    }

    // =========================================================
    // Appel Python via fichier temporaire JSON
    // Évite les problèmes de guillemets Windows
    // =========================================================
    private Map<String, Object> callPythonViaFile(String json) throws Exception {
        // Écrire le JSON dans un fichier temporaire
        Path tempFile = Files.createTempFile("forecast_input_", ".json");
        Files.writeString(tempFile, json);

        try {
            String scriptPath = getScriptPath();
            String pythonCmd  = getPythonCommand();

            // Passer le chemin du fichier au lieu du JSON directement
            ProcessBuilder pb = new ProcessBuilder(
                    pythonCmd, scriptPath, "--file", tempFile.toString()
            );
            pb.redirectErrorStream(false);

            Process process = pb.start();
            String stdout   = new String(process.getInputStream().readAllBytes());
            String stderr   = new String(process.getErrorStream().readAllBytes());
            int exitCode    = process.waitFor();

            if (!stderr.isBlank()) {
                System.out.println("=== Python logs ===\n" + stderr + "\n==================");
            }

            if (stdout.isBlank()) {
                return Map.of("error", "Aucune réponse Python. Exit=" + exitCode);
            }

            return mapper.readValue(stdout, Map.class);

        } finally {
            // Supprimer le fichier temporaire
            Files.deleteIfExists(tempFile);
        }
    }

    private String getScriptPath() {
        try {
            var resource = getClass().getClassLoader().getResource("scripts/forecaster.py");
            if (resource != null) return Paths.get(resource.toURI()).toString();
        } catch (Exception ignored) {}
        return "src/main/resources/scripts/forecaster.py";
    }

    private String getPythonCommand() {
        return System.getProperty("os.name").toLowerCase().contains("win") ? "python" : "python3";
    }
}