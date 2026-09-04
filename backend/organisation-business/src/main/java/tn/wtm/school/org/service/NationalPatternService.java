package tn.wtm.school.org.service;

import tn.wtm.school.org.dto.request.ApplyNationalPatternRequest;
import tn.wtm.school.org.dto.response.NationalPatternResponse;

import java.util.List;

public interface NationalPatternService {

    List<NationalPatternResponse> findAllActive(String countryCode, Integer academicYear);

    NationalPatternResponse findById(Long id);

    NationalPatternResponse findByLevelCode(String countryCode, String levelCode);

    NationalPatternApplyResult applyToTenant(Long nationalPatternId, Long schoolYearId);

    ApplyNationalResult applyByRequest(ApplyNationalPatternRequest request);

    record NationalPatternApplyResult(int created, int skipped, String message) {}

    record ApplyNationalResult(int totalCreated, int totalSkipped, List<LevelResult> levels) {}

    record LevelResult(String levelCode, int created, int skipped, String message) {}
}
