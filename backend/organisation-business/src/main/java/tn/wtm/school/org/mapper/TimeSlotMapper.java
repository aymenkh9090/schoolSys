package tn.wtm.school.org.mapper;

import org.mapstruct.*;
import tn.wtm.school.org.dto.response.TimeSlotResponseDTO;
import tn.wtm.school.org.entity.TimeSlot;

import java.util.List;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TimeSlotMapper {

    // ── toResponse ────────────────────────────────────────────────────────────
    @Mapping(target = "id", source = "idTimeSlot")
    TimeSlotResponseDTO toResponse(TimeSlot entity);

    List<TimeSlotResponseDTO> toResponseList(List<TimeSlot> list);
}
