package tn.wtm.school.org.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.wtm.school.org.entity.NationalPattern;

import java.util.List;
import java.util.Optional;

@Repository
public interface NationalPatternRepository extends JpaRepository<NationalPattern, Long> {

    boolean existsByCountryCode(String countryCode);

    Optional<NationalPattern> findByCode(String code);

    List<NationalPattern> findByCountryCodeAndActiveTrue(String countryCode);

    List<NationalPattern> findByCountryCodeAndAcademicYearAndActiveTrue(String countryCode, Integer academicYear);

    @Query("""
        SELECT DISTINCT np FROM NationalPattern np
        LEFT JOIN FETCH np.details
        WHERE np.countryCode = :countryCode AND np.levelCode = :levelCode AND np.active = true
        ORDER BY np.version DESC
        """)
    List<NationalPattern> findActiveWithDetailsByCountryAndLevel(
            @Param("countryCode") String countryCode,
            @Param("levelCode") String levelCode);

    @Query("""
        SELECT DISTINCT np FROM NationalPattern np
        LEFT JOIN FETCH np.details
        WHERE np.idNationalPattern = :id
        """)
    Optional<NationalPattern> findByIdWithDetails(@Param("id") Long id);

    @Query("""
        SELECT DISTINCT np FROM NationalPattern np
        LEFT JOIN FETCH np.details
        WHERE np.countryCode = :countryCode AND np.active = true
        """)
    List<NationalPattern> findAllActiveWithDetails(@Param("countryCode") String countryCode);
}
