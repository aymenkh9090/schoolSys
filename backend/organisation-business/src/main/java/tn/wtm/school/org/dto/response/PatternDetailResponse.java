package tn.wtm.school.org.dto.response;

import lombok.*;
import tn.wtm.school.org.enums.RoomType;
import tn.wtm.school.org.enums.SessionType;
import tn.wtm.school.org.enums.WeekParity;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatternDetailResponse {

    Long idPatternDetail;
    Integer sessionOrder;
    Double duration;
    WeekParity weekParity;
    Boolean isSplit;
    Integer splitGroupIndex;
    SessionType type;
    RoomType requiredRoomType;
    // SubjectSessionType info
    Long subjectSessionTypeId;
    Double sessionTypeDuration;
    Boolean sessionTypeRequiresSplit;

}
