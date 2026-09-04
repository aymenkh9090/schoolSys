package tn.wtm.school.org.mapper;

import org.mapstruct.*;
import tn.wtm.school.org.dto.request.RoomRequest;
import tn.wtm.school.org.dto.response.RoomResponse;
import tn.wtm.school.org.entity.Room;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface RoomMapper {


    // DTO==>ToEntity
    @IgnoreAuditFields
    @Mapping(target = "idSalle",ignore = true)
    @Mapping(target = "tenantId",ignore = true)
    @Mapping(target = "estDisponible", defaultValue = "true")
    Room toEntity(RoomRequest request);

    // Entity==>ToResponse
    RoomResponse toResponse(Room room);

    // UpdateFromDto
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @IgnoreAuditFields
    @Mapping(target = "idSalle",ignore = true)
    @Mapping(target = "tenantId",ignore = true)
    void updateFromRequest(RoomRequest request, @MappingTarget Room room);





}
