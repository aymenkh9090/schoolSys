package tn.wtm.school.planning.solver.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import tn.wtm.school.planning.solver.enums.RoomType;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class MoveSessionRequest {

    @NotNull(message = "Le jour est obligatoire")
    private DayOfWeek day;

    @NotNull(message = "L'heure de debut est obligatoire")
    private LocalTime startTime;

    /** Optionnels — conservent la salle/le type actuels si absents. */
    private String roomCode;
    private RoomType roomType;
}
