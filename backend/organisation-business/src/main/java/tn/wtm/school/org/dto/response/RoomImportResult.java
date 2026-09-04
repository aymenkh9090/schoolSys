package tn.wtm.school.org.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RoomImportResult {

    private final int imported;
    private final int skipped;
    private final int errors;
    private final List<RowError> rowErrors;

    @Getter
    @Builder
    public static class RowError {
        private final int line;
        private final String codeSalle;
        private final String reason;
    }
}
