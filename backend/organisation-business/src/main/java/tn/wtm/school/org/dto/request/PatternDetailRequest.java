package tn.wtm.school.org.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import tn.wtm.school.org.enums.RoomType;
import tn.wtm.school.org.enums.SessionType;
import tn.wtm.school.org.enums.WeekParity;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatternDetailRequest {

    @NotNull(message = "L'ordre de la séance est obligatoire")
    @Min(value = 1,  message = "L'ordre doit être au moins 1")
    @Max(value = 20, message = "L'ordre ne peut pas dépasser 20")
    Integer sessionOrder;

    @NotNull(message = "La durée est obligatoire")
    @DecimalMin(value = "0.5", message = "La durée doit être au moins 0.5h")
    @DecimalMax(value = "4.0", message = "La durée ne peut pas dépasser 4h")
    Double duration;

    // PARITÉ — null = ALL par défaut
    WeekParity weekParity;

    Boolean isSplit;

    @Min(value = 1, message = "L'index du groupe doit être au moins 1")
    Integer splitGroupIndex;

    @NotNull(message = "Le type de séance est obligatoire")
    SessionType type;

    @Pattern(
            regexp = "^(NORMALE|LABSCIENCE|LABPHYSIQUE|LABINFORMATIQUE|SALLESPORT)$",
            message = "Type de salle invalide. Valeurs acceptées: NORMALE, LABSCIENCE, LABPHYSIQUE, LABINFORMATIQUE, SALLESPORT"
    )
    String requiredRoomType;

    Long subjectSessionTypeId;

        // Si isSplit=true → splitGroupIndex obligatoire
        @AssertTrue(message = "splitGroupIndex est obligatoire quand isSplit est true")
        public boolean isSplitIndexValidWhenSplit() {
            if (Boolean.TRUE.equals(isSplit)) {
                return splitGroupIndex != null && splitGroupIndex >= 1;
            }
            return true;
        }

        // Convertit le String vers l'enum après validation
        public RoomType getRoomTypeEnum() {
            return requiredRoomType != null ? RoomType.valueOf(requiredRoomType) : null;
        }





    }
