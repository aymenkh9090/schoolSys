package tn.wtm.school.org.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.wtm.school.org.entity.NationalPatternDetail;

import java.util.List;

@Repository
public interface NationalPatternDetailRepository extends JpaRepository<NationalPatternDetail, Long> {

    List<NationalPatternDetail> findByNationalPattern_IdNationalPattern(Long nationalPatternId);
}
