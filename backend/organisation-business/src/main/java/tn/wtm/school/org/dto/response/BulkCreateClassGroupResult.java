package tn.wtm.school.org.dto.response;

import java.util.List;

public record BulkCreateClassGroupResult(
        int created,
        int skipped,
        List<ClassGroupResponse> classes,
        List<String> skippedCodes
) {}
