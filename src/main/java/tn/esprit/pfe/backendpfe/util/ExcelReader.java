package tn.esprit.pfe.backendpfe.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.pfe.backendpfe.entities.KpiValue;

import java.io.IOException;
import java.util.*;

@Component
public class ExcelReader {

    public List<KpiValue> readAllKpis(MultipartFile file) throws IOException {

        List<KpiValue> result = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();

        // ✅ Anti-doublons (même combinaison KPI/pays/mois/année)
        Set<String> seen = new HashSet<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                String sheetName = sheet.getSheetName();

                // ✅ Lire seulement KPI_Data_*
                if (!sheetName.startsWith("KPI_Data_")) continue;

                Row headerRow = sheet.getRow(0);
                if (headerRow == null) continue;

                int firstMonthCol = 4; // Jan
                int lastMonthCol = headerRow.getLastCellNum() - 1;

                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;

                    String affiliate = formatter.formatCellValue(row.getCell(0), evaluator).trim();
                    String yearStr   = formatter.formatCellValue(row.getCell(1), evaluator).trim();
                    String kpiCode   = formatter.formatCellValue(row.getCell(3), evaluator).trim();
                    String category = formatter.formatCellValue(row.getCell(2), evaluator).trim();


                    if (affiliate.isEmpty() || yearStr.isEmpty() || kpiCode.isEmpty()) continue;

                    Integer year = tryParseYear(yearStr);
                    if (year == null) continue;

                    for (int c = firstMonthCol; c <= lastMonthCol; c++) {
                        String month = formatter.formatCellValue(headerRow.getCell(c), evaluator).trim();
                        String valueStr = formatter.formatCellValue(row.getCell(c), evaluator).trim();

                        if (month.isEmpty() || valueStr.isEmpty()) continue;

                        Double value = tryParseDouble(valueStr);
                        if (value == null) continue;

                        // ✅ clé unique
                        String key = kpiCode + "|" + affiliate + "|" + month + "|" + year;
                        if (!seen.add(key)) continue; // ignore doublon

                        KpiValue kpi = new KpiValue();
                        kpi.setAffiliate(affiliate);
                        kpi.setYear(year);
                        kpi.setKpiCode(kpiCode);
                        kpi.setMonth(month);
                        kpi.setValue(value);
                        kpi.setCategory(category);


                        result.add(kpi);
                    }
                }
            }
        }

        return result;
    }

    private Integer tryParseYear(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception ignored) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(19|20)\\d{2}").matcher(s);
            if (m.find()) return Integer.parseInt(m.group());
            return null;
        }
    }

    private Double tryParseDouble(String s) {
        try {
            return Double.parseDouble(s.replace(" ", "").replace(",", "."));
        } catch (Exception e) {
            return null;
        }
    }
}
