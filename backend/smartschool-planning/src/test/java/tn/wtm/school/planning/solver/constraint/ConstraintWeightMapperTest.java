package tn.wtm.school.planning.solver.constraint;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.wtm.school.planning.constraints.enums.ImportanceLevel;
import tn.wtm.school.planning.constraints.repository.ConstraintSettingRepository;
import tn.wtm.school.planning.constraints.service.ConstraintParameters;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ConstraintWeightMapperTest {

    @Mock private ConstraintSettingRepository settingRepository;

    private ConstraintWeightMapper mapper;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        mapper = new ConstraintWeightMapper(settingRepository, new ConstraintParameters(new ObjectMapper()));
    }

    // ── scoreOf ───────────────────────────────────────────────────────────────

    @Test
    void criticalMapsToOneHard() {
        assertThat(mapper.scoreOf(ImportanceLevel.CRITICAL))
                .isEqualTo(HardMediumSoftScore.ONE_HARD);
    }

    @Test
    void highMapsToOneMedium() {
        assertThat(mapper.scoreOf(ImportanceLevel.HIGH))
                .isEqualTo(HardMediumSoftScore.ONE_MEDIUM);
    }

    @Test
    void mediumMapsToSoftTen() {
        assertThat(mapper.scoreOf(ImportanceLevel.MEDIUM))
                .isEqualTo(HardMediumSoftScore.ofSoft(10));
    }

    @Test
    void lowMapsToSoftOne() {
        assertThat(mapper.scoreOf(ImportanceLevel.LOW))
                .isEqualTo(HardMediumSoftScore.ofSoft(1));
    }

    @Test
    void nullImportanceMapsToZeroScore() {
        assertThat(mapper.scoreOf(null)).isEqualTo(HardMediumSoftScore.ZERO);
    }

    // ── weightOf ──────────────────────────────────────────────────────────────

    @Test
    void criticalWeightIs1000() {
        assertThat(mapper.weightOf(ImportanceLevel.CRITICAL)).isEqualTo(1000);
    }

    @Test
    void highWeightIs100() {
        assertThat(mapper.weightOf(ImportanceLevel.HIGH)).isEqualTo(100);
    }

    @Test
    void mediumWeightIs10() {
        assertThat(mapper.weightOf(ImportanceLevel.MEDIUM)).isEqualTo(10);
    }

    @Test
    void lowWeightIs1() {
        assertThat(mapper.weightOf(ImportanceLevel.LOW)).isEqualTo(1);
    }

    @Test
    void nullImportanceWeightIsZero() {
        assertThat(mapper.weightOf(null)).isEqualTo(0);
    }

    // ── consistency ───────────────────────────────────────────────────────────

    @Test
    void scoreAndWeightAreConsistentForAllLevels() {
        for (ImportanceLevel level : ImportanceLevel.values()) {
            HardMediumSoftScore score = mapper.scoreOf(level);
            int weight = mapper.weightOf(level);
            assertThat(score).isNotNull();
            assertThat(weight).isPositive();
        }
    }
}
