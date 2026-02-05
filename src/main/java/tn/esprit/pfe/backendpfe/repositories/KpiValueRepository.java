package tn.esprit.pfe.backendpfe.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tn.esprit.pfe.backendpfe.entities.KpiValue;

import java.util.List;

public interface KpiValueRepository extends JpaRepository<KpiValue, Long> {

    List<KpiValue> findByAffiliateAndMonthAndYear(String affiliate, String month, int year);

    @Query("select distinct k.affiliate from KpiValue k order by k.affiliate")
    List<String> findDistinctAffiliates();

    @Query("select distinct k.year from KpiValue k order by k.year desc")
    List<Integer> findDistinctYears();

    @Query("select distinct k.month from KpiValue k where k.affiliate = :affiliate and k.year = :year order by k.month")
    List<String> findDistinctMonths(String affiliate, int year);

    void deleteByAffiliateAndYear(String affiliate, int year);
    @Query("""
select distinct k.category from KpiValue k
where k.affiliate = :affiliate and k.year = :year
order by k.category
""")
    List<String> findDistinctCategoriesByAffiliateAndYear(String affiliate, int year);
    @Query("""
select k from KpiValue k
where lower(trim(k.affiliate)) = lower(trim(:affiliate))
  and lower(trim(k.month)) = lower(trim(:month))
  and k.year = :year
  and (:category is null or :category = '' or lower(trim(k.category)) = lower(trim(:category)))
order by k.kpiCode
""")
    List<KpiValue> findKpisWithOptionalCategory(String affiliate, String month, int year, String category);
    @Query("""
select k.kpiCode, avg(k.value)
from KpiValue k
where lower(trim(k.affiliate)) = lower(trim(:affiliate))
  and k.year = :year
  and (:category is null or :category = '' or lower(trim(k.category)) = lower(trim(:category)))
group by k.kpiCode
order by k.kpiCode
""")
    List<Object[]> avgByKpi(String affiliate, int year, String category);


}

