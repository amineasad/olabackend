package tn.esprit.pfe.backendpfe.dto;
import java.util.List;

public record ChartSeriesDto(List<String> labels, List<Double> values) {}