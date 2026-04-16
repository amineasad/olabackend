package tn.esprit.pfe.backendpfe.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.esprit.pfe.backendpfe.repositories.KpiValueRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

@Service
public class KpiSummaryService {

    private final KpiValueRepository kpiRepo;

    @Value("${groq.api.key}")
    private String groqApiKey;

    private static final String GROQ_URL   = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "llama-3.3-70b-versatile";

    private static final Map<String, Integer> MONTH_INDEX = new LinkedHashMap<>();
    static {
        MONTH_INDEX.put("Jan",0);      MONTH_INDEX.put("Feb",1);
        MONTH_INDEX.put("Mar",2);      MONTH_INDEX.put("Apr",3);
        MONTH_INDEX.put("May",4);      MONTH_INDEX.put("June",5);
        MONTH_INDEX.put("July",6);     MONTH_INDEX.put("August",7);
        MONTH_INDEX.put("September",8);MONTH_INDEX.put("October",9);
        MONTH_INDEX.put("November",10);MONTH_INDEX.put("December",11);
    }

    private static final List<String> MONTHS = List.of(
            "Jan","Feb","Mar","Apr","May","June",
            "July","Aug","Sep","Oct","Nov","Dec"
    );

    private static final Map<String, String> KEY_KPIS = new LinkedHashMap<>();
    static {
        KEY_KPIS.put("On_time_delivery_rate",    "rate");
        KEY_KPIS.put("Payment_incident_rate",     "rate");
        KEY_KPIS.put("Driver_compliance_rate",    "rate");
        KEY_KPIS.put("Fleet_utilization_rate",    "rate");
        KEY_KPIS.put("Total_volume_m3",           "volume");
        KEY_KPIS.put("Unit_Cost_per_m3",          "cost");
        KEY_KPIS.put("Plan_Unit_Cost_per_m3",     "cost");
        KEY_KPIS.put("Contamination_incidents",   "count");
        KEY_KPIS.put("Total_Fleet_OPEX",          "currency");
    }

    public KpiSummaryService(KpiValueRepository kpiRepo) {
        this.kpiRepo = kpiRepo;
    }

    // =========================================================
    // Point d'entrée UNIQUE
    // month = null ou "ALL" → résumé annuel (tendances sur 12 mois)
    // month = "Jan", "Feb"... → résumé ciblé sur ce mois
    // =========================================================
    public String generateSummary(String affiliate, int year, String category, String month) {
        boolean isMonthly = month != null && !month.isBlank() && !"ALL".equals(month);

        if (isMonthly) {
            // Résumé mensuel : valeurs du mois + comparaison avec moyenne annuelle
            Map<String, double[]> annualData = aggregateKpiData(affiliate, year, category);
            if (annualData.isEmpty()) return "Aucune donnée disponible.";

            Integer monthIdx = resolveMonthIndex(month);
            if (monthIdx == null) return "Mois invalide : " + month;

            return callGroqApi(buildMonthlyPrompt(affiliate, year, category, month, monthIdx, annualData));
        } else {
            // Résumé annuel : stats sur toute l'année
            Map<String, double[]> annualData = aggregateKpiData(affiliate, year, category);
            if (annualData.isEmpty()) return "Aucune donnée disponible.";
            return callGroqApi(buildAnnualPrompt(affiliate, year, category, annualData));
        }
    }

    // =========================================================
    // Agrégation via seriesByMonth — retourne double[12] par KPI
    // =========================================================
    private Map<String, double[]> aggregateKpiData(String affiliate, int year, String category) {
        Map<String, double[]> result = new LinkedHashMap<>();
        String cat = (category == null || category.isBlank()) ? "ALL" : category;

        for (String kpiCode : KEY_KPIS.keySet()) {
            try {
                List<Object[]> rows = kpiRepo.seriesByMonth(affiliate, year, kpiCode, cat);
                if (rows == null || rows.isEmpty()) continue;

                double[] monthly = new double[12];
                boolean  hasData = false;
                for (Object[] row : rows) {
                    Integer idx = resolveMonthIndex((String) row[0]);
                    if (idx != null && row[1] != null) {
                        monthly[idx] = ((Number) row[1]).doubleValue();
                        hasData = true;
                    }
                }
                if (hasData) result.put(kpiCode, monthly);
            } catch (Exception e) {
                System.err.println("Skipping KPI " + kpiCode + ": " + e.getMessage());
            }
        }
        return result;
    }

    // =========================================================
    // PROMPT ANNUEL — stats sur 12 mois + tendances S1/S2
    // =========================================================
    private String buildAnnualPrompt(String affiliate, int year, String category,
                                     Map<String, double[]> kpiData) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tu es un expert en logistique pétrolière pour OLA Energy (Afrique).\n");
        sb.append("Pays:").append(affiliate)
                .append(" | Année:").append(year)
                .append(" | Catégorie:").append("ALL".equals(category) ? "Toutes" : category)
                .append("\n\nDONNÉES ANNUELLES (12 mois):\n");

        appendKpiStats(sb, kpiData, -1); // -1 = mode annuel

        sb.append("\nRédige un résumé exécutif ANNUEL en français (200-250 mots):\n");
        sb.append("🎯 Vue d'ensemble | ✅ Points forts | ⚠️ Alertes | 📈 Tendances S1→S2 | 💡 Recommandations\n");
        sb.append("Chiffres précis. Ton professionnel.");
        return sb.toString();
    }

    // =========================================================
    // PROMPT MENSUEL — valeur du mois + écart vs moyenne annuelle
    // =========================================================
    private String buildMonthlyPrompt(String affiliate, int year, String category,
                                      String month, int monthIdx,
                                      Map<String, double[]> kpiData) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tu es un expert en logistique pétrolière pour OLA Energy (Afrique).\n");
        sb.append("Pays:").append(affiliate)
                .append(" | Mois:").append(month)
                .append(" | Année:").append(year)
                .append(" | Catégorie:").append("ALL".equals(category) ? "Toutes" : category)
                .append("\n\nDONNÉES DU MOIS DE ").append(month.toUpperCase())
                .append(" (valeur mois vs moyenne annuelle):\n");

        appendKpiStats(sb, kpiData, monthIdx);

        sb.append("\nRédige un résumé exécutif MENSUEL en français (180-220 mots) pour le mois de ")
                .append(month).append(":\n");
        sb.append("🎯 Bilan du mois | ✅ Points forts | ⚠️ Alertes | 📊 Écarts vs moyenne | 💡 Actions prioritaires\n");
        sb.append("Chiffres précis. Compare le mois à la moyenne annuelle. Ton professionnel.");
        return sb.toString();
    }

    // =========================================================
    // Construction des stats KPI — partagée annuel et mensuel
    // monthIdx = -1 → mode annuel (stats 12 mois + tendance)
    // monthIdx = 0..11 → mode mensuel (valeur mois + écart vs moy)
    // =========================================================
    private void appendKpiStats(StringBuilder sb, Map<String, double[]> kpiData, int monthIdx) {
        for (Map.Entry<String, double[]> entry : kpiData.entrySet()) {
            String   kpiCode = entry.getKey();
            double[] vals    = entry.getValue();
            String   type    = KEY_KPIS.get(kpiCode);

            double avg  = Arrays.stream(vals).filter(v -> v > 0).average().orElse(0);
            double max  = Arrays.stream(vals).max().orElse(0);
            double sum  = Arrays.stream(vals).sum();
            int    peak = indexOfMax(vals);

            sb.append("- ").append(kpiCode.replace("_", " ")).append(": ");

            if (monthIdx >= 0) {
                // ── MODE MENSUEL : valeur du mois + écart vs moyenne annuelle ──
                double monthVal = vals[monthIdx];
                if (monthVal == 0) {
                    sb.append("aucune donnée ce mois\n");
                    continue;
                }
                if ("rate".equals(type)) {
                    sb.append(String.format("valeur=%.1f%% | moy_annuelle=%.1f%%",
                            monthVal * 100, avg * 100));
                    if (avg > 0) {
                        double ecart = ((monthVal - avg) / avg) * 100;
                        sb.append(String.format(" | écart=%+.1f%%", ecart));
                    }
                } else {
                    sb.append(String.format("valeur=%.0f | moy_annuelle=%.0f", monthVal, avg));
                    if (avg > 0) {
                        double ecart = ((monthVal - avg) / avg) * 100;
                        sb.append(String.format(" | écart=%+.1f%%", ecart));
                    }
                }

            } else {
                // ── MODE ANNUEL : stats + tendance S1/S2 ──
                double s1 = Arrays.stream(vals, 0, 6).filter(v -> v > 0).average().orElse(0);
                double s2 = Arrays.stream(vals, 6, 12).filter(v -> v > 0).average().orElse(0);

                if ("rate".equals(type)) {
                    sb.append(String.format("moy=%.1f%% max=%.1f%%(%s)",
                            avg * 100, max * 100, MONTHS.get(peak)));
                } else {
                    sb.append(String.format("total=%.0f moy=%.0f max=%.0f(%s)",
                            sum, avg, max, MONTHS.get(peak)));
                }
                if (s1 > 0 && s2 > 0) {
                    double trend = ((s2 - s1) / s1) * 100;
                    sb.append(String.format(" S1→S2:%+.1f%%", trend));
                }
            }
            sb.append("\n");
        }

        // Écart ACT vs PLAN (pertinent dans les deux modes)
        if (kpiData.containsKey("Unit_Cost_per_m3") && kpiData.containsKey("Plan_Unit_Cost_per_m3")) {
            double[] act  = kpiData.get("Unit_Cost_per_m3");
            double[] plan = kpiData.get("Plan_Unit_Cost_per_m3");

            double actVal  = monthIdx >= 0 ? act[monthIdx]  : Arrays.stream(act).filter(v->v>0).average().orElse(0);
            double planVal = monthIdx >= 0 ? plan[monthIdx] : Arrays.stream(plan).filter(v->v>0).average().orElse(0);

            if (planVal > 0 && actVal > 0) {
                double ecart = ((actVal - planVal) / planVal) * 100;
                sb.append(String.format("- Écart coût ACT/PLAN: %+.1f%%\n", ecart));
            }
        }
    }

    // =========================================================
    // Appel Groq API
    // =========================================================
    private String callGroqApi(String prompt) {
        try {
            String body = "{"
                    + "\"model\":\"" + GROQ_MODEL + "\","
                    + "\"messages\":[{\"role\":\"user\",\"content\":" + escapeJson(prompt) + "}],"
                    + "\"temperature\":0.7,"
                    + "\"max_tokens\":600"
                    + "}";

            HttpClient  client = HttpClient.newHttpClient();
            HttpRequest req    = HttpRequest.newBuilder()
                    .uri(URI.create(GROQ_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + groqApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() == 200) return extractGroqText(res.body());

            System.err.println("Groq error " + res.statusCode() + ": " + res.body());
            return "Erreur API (" + res.statusCode() + ").";

        } catch (Exception e) {
            System.err.println("Groq exception: " + e.getMessage());
            return "Service IA indisponible.";
        }
    }

    // Structure Groq/OpenAI : {"choices":[{"message":{"content":"..."}}]}
    private String extractGroqText(String json) {
        try {
            String key = "\"content\":\"";
            int s = json.indexOf(key);
            if (s == -1) return "Réponse non parseable.";
            s += key.length();
            int e = s;
            while (e < json.length()) {
                if (json.charAt(e) == '"' && json.charAt(e - 1) != '\\') break;
                e++;
            }
            return json.substring(s, e)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        } catch (Exception ex) {
            return "Erreur parsing réponse.";
        }
    }

    private Integer resolveMonthIndex(String m) {
        if (m == null) return null;
        for (Map.Entry<String, Integer> e : MONTH_INDEX.entrySet())
            if (e.getKey().equalsIgnoreCase(m.trim())) return e.getValue();
        if (m.trim().length() >= 3) {
            String p = m.trim().substring(0, 3).toLowerCase();
            for (Map.Entry<String, Integer> e : MONTH_INDEX.entrySet())
                if (e.getKey().toLowerCase().startsWith(p)) return e.getValue();
        }
        return null;
    }

    private String escapeJson(String t) {
        return "\"" + t
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }

    private int indexOfMax(double[] a) {
        int idx = 0;
        for (int i = 1; i < a.length; i++) if (a[i] > a[idx]) idx = i;
        return idx;
    }

    private int indexOfMinPositive(double[] a) {
        int idx = -1;
        for (int i = 0; i < a.length; i++)
            if (a[i] > 0 && (idx == -1 || a[i] < a[idx])) idx = i;
        return idx == -1 ? 0 : idx;
    }
}