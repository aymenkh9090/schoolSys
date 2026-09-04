package tn.wtm.school.org.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class NationalPatternResponse {

    private Long idNationalPattern;
    private String code;
    private String name;
    private Integer version;
    private Integer academicYear;
    private Boolean active;
    private String countryCode;
    private String levelCode;
    private List<DetailResponse> details;

    @Getter
    @Builder
    public static class DetailResponse {
        private Long idNationalPatternDetail;
        private String subjectCode;
        private Double totalHoursPerWeek;
        private String repartition;
        private List<SessionResponse> sessions;
    }

    @Getter
    @Builder
    public static class SessionResponse {
        private Long idNationalPatternSession;
        private Integer sessionOrder;
        private String sessionType;
        private Double duration;
        private String groupingType;
        private String requiredRoomType;
        private String weekParity;
    }
}
