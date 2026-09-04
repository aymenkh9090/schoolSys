package tn.wtm.school.planning.constraints.dto.response;

import lombok.*;

import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ConstraintProfileResponse {

    private Long idConstraintProfile;
    private String name;
    private Long academicYearId;
    private Boolean active;
    private List<ConstraintSettingResponse> settings;
}
