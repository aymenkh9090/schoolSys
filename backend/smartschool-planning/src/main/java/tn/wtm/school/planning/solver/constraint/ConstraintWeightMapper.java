package tn.wtm.school.planning.solver.constraint;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tn.wtm.school.planning.constraints.entity.ConstraintSetting;
import tn.wtm.school.planning.constraints.enums.ImportanceLevel;
import tn.wtm.school.planning.constraints.repository.ConstraintSettingRepository;
import tn.wtm.school.planning.constraints.service.ConstraintParameters;

import java.util.List;
import java.util.Map;

/**
 * Maps ImportanceLevel to Timefold score levels and to integer DB weights.
 * Also loads per-solve {@link ActiveConstraintParam} problem facts from the DB.
 *
 * Score levels (PLANNING_MODULE.md §16):
 *   CRITICAL → HARD   (structural violation — must be zero for a valid timetable)
 *   HIGH     → MEDIUM (Ministry-mandated rule — strongly penalized)
 *   MEDIUM   → SOFT*10 (school preference — optimized but flexible)
 *   LOW      → SOFT*1  (minor preference — minimized if possible)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConstraintWeightMapper {

    private final ConstraintSettingRepository settingRepository;
    private final ConstraintParameters        constraintParameters;

    // ── per-solve loading ─────────────────────────────────────────────────────

    /**
     * Loads the active constraint settings for a given tenant and profile and converts
     * them to {@link ActiveConstraintParam} problem facts ready to be stored in
     * {@link tn.wtm.school.planning.solver.domain.TimetableSolution}.
     *
     * Called once by {@link tn.wtm.school.planning.solver.builder.TimetableProblemBuilder}
     * before each solve invocation. Thread-safe — returns a new immutable list each time.
     */
    public List<ActiveConstraintParam> load(String tenantId, Long profileId) {
        List<ConstraintSetting> settings =
                settingRepository.findActiveByProfileAndTenantId(profileId, tenantId);
        return settings.stream()
                .map(this::toParam)
                .toList();
    }

    private ActiveConstraintParam toParam(ConstraintSetting s) {
        String code = s.getDefinition().getCode();
        Map<String, Object> values = constraintParameters.readValues(s.getParametersJson());
        int intParam = constraintParameters.firstInt(values);

        // Le poids saisi par l'établissement prime : c'est le levier de réglage
        // fin de l'interface. L'importance ne sert que de valeur de repli, pour
        // les réglages créés avant que le poids ne soit modifiable.
        int softWeight = s.getWeight() != null && s.getWeight() > 0
                ? s.getWeight()
                : weightOf(s.getImportance());

        return new ActiveConstraintParam(code, intParam, softWeight, values);
    }

    // ── static score mappings ─────────────────────────────────────────────────

    /**
     * Converts an ImportanceLevel to the matching HardMediumSoftScore level.
     * Used when a constraint wants to apply a single-unit penalty at the right level.
     */
    public HardMediumSoftScore scoreOf(ImportanceLevel level) {
        if (level == null) {
            return HardMediumSoftScore.ZERO;
        }
        return switch (level) {
            case CRITICAL -> HardMediumSoftScore.ONE_HARD;
            case HIGH     -> HardMediumSoftScore.ONE_MEDIUM;
            case MEDIUM   -> HardMediumSoftScore.ofSoft(10);
            case LOW      -> HardMediumSoftScore.ofSoft(1);
        };
    }

    /**
     * Converts an ImportanceLevel to the integer weight stored in constraint_setting.weight.
     * Matches the scale used in ConstraintProfileServiceImpl.importanceToWeight().
     */
    public int weightOf(ImportanceLevel level) {
        if (level == null) {
            return 0;
        }
        return switch (level) {
            case CRITICAL -> 1000;
            case HIGH     -> 100;
            case MEDIUM   -> 10;
            case LOW      -> 1;
        };
    }
}
