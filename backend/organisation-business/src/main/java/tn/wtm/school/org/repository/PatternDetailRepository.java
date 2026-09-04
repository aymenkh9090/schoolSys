package tn.wtm.school.org.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.wtm.school.common.repository.TenantAwareRepository;
import tn.wtm.school.org.entity.PatternDetail;
import tn.wtm.school.org.enums.RoomType;
import tn.wtm.school.org.enums.SessionType;
import tn.wtm.school.org.enums.WeekParity;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatternDetailRepository extends TenantAwareRepository<PatternDetail, Long> {

    // ── Par pattern ───────────────────────────────────────────────────────────

    List<PatternDetail> findByPattern_IdPatternOrderBySessionOrderAsc(Long patternId);

    void deleteByPattern_IdPattern(Long patternId);

    boolean existsByPattern_IdPattern(Long patternId);

    // ── Par type de séance ────────────────────────────────────────────────────

    List<PatternDetail> findByType(SessionType type);

    List<PatternDetail> findByPattern_IdPatternAndType(Long patternId, SessionType type);

    // ── Par type de salle requis (utile pour la génération) ───────────────────

    List<PatternDetail> findByRequiredRoomType(RoomType roomType);

    @Query("""
        SELECT pd FROM PatternDetail pd
        JOIN FETCH pd.pattern p
        JOIN FETCH p.subjectLevel sl
        WHERE pd.requiredRoomType = :roomType
          AND sl.level.idNiveau   = :levelId
        ORDER BY sl.subject.codeMatiere ASC
        """)
    List<PatternDetail> findByRoomTypeAndLevel(
            @Param("roomType") RoomType roomType,
            @Param("levelId")  Long levelId
    );

    // ── Séances divisées ──────────────────────────────────────────────────────

    List<PatternDetail> findByPattern_IdPatternAndIsSplitTrue(Long patternId);

    // ── Parité ────────────────────────────────────────────────────────────────

    List<PatternDetail> findByPattern_IdPatternAndWeekParity(
            Long patternId, WeekParity parity
    );

    // ── Reorder après suppression ─────────────────────────────────────────────

    @Modifying
    @Query("""
        UPDATE PatternDetail pd
        SET pd.sessionOrder = pd.sessionOrder - 1
        WHERE pd.pattern.idPattern = :patternId
          AND pd.sessionOrder > :deletedOrder
        """)
    void reorderAfterDelete(
            @Param("patternId")    Long patternId,
            @Param("deletedOrder") int deletedOrder
    );

}
