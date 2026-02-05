package tn.esprit.pfe.backendpfe.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "kpi_value",
        uniqueConstraints = @UniqueConstraint(columnNames = {"kpi_code","affiliate","month","year"})
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class KpiValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="kpi_code")
    private String kpiCode;
    @Column(name = "category")
    private String category;

    private String affiliate; // OETN, OEMA, ...

    private String month;     // Jan, Feb...

    private int year;

    private Double value;
}
