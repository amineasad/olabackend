package tn.esprit.pfe.backendpfe.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
    @Query("""
  SELECT k.month, AVG(k.value)
  FROM KpiValue k
  WHERE k.affiliate = :affiliate
    AND k.year = :year
    AND k.kpiCode = :kpiCode
    AND (:category IS NULL OR :category = 'ALL' OR k.category = :category)
  GROUP BY k.month
""")
    List<Object[]> avgByMonth(String affiliate, int year, String kpiCode, String category);

    @Query("""
  select k.month, avg(k.value)
  from KpiValue k
  where k.affiliate = :affiliate
    and k.year = :year
    and k.kpiCode = :kpiCode
    and (:category is null or :category = 'ALL' or k.category = :category)
  group by k.month
""")
    List<Object[]> seriesByMonth(@Param("affiliate") String affiliate,
                                 @Param("year") int year,
                                 @Param("kpiCode") String kpiCode,
                                 @Param("category") String category);
}

