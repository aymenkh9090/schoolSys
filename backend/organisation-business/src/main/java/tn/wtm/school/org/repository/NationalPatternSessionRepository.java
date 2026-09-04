package tn.wtm.school.org.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.wtm.school.org.entity.NationalPatternSession;

import java.util.List;

@Repository
public interface NationalPatternSessionRepository extends JpaRepository<NationalPatternSession, Long> {

    List<NationalPatternSession> findByNationalPatternDetail_IdNationalPatternDetailOrderBySessionOrderAsc(
            Long detailId);
}
