package tn.wtm.school.planning.solver.ref;

import lombok.*;
import tn.wtm.school.planning.solver.enums.RoomType;

/**
 * Lightweight snapshot of a Room for the Timefold solver.
 * The type drives both room-conflict detection and special-room-required constraints.
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RoomRef {

    @EqualsAndHashCode.Include
    private Long id;

    private String code;       // e.g. "ph1", "sp3", "tch2"
    private RoomType type;
    private int capacity;

    public boolean isSpecialRoom() {
        return type != null && type != RoomType.NORMALE;
    }

    /**
     * True when this room can satisfy the given requirement.
     * A null or NORMALE requirement means any room is acceptable.
     */
    public boolean canAccommodate(RoomType requiredType) {
        if (requiredType == null || requiredType == RoomType.NORMALE) {
            return true;
        }
        return type == requiredType;
    }
}
