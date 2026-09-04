package tn.wtm.school.org.dto.response;

import java.util.List;

public record AssignSubjectResult(
        int assigned,
        int skipped,
        List<TeachingAssignmentResponse> assignments,
        List<SkippedEntry> skipped_details
) {
    public record SkippedEntry(Long classGroupId, String reason) {}
}
