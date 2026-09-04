package tn.wtm.school.tenant.mapper;

import org.mapstruct.*;
import tn.wtm.school.tenant.dto.CreateTenantRequest;
import tn.wtm.school.tenant.dto.TenantResponse;
import tn.wtm.school.tenant.dto.UpdateTenantRequest;
import tn.wtm.school.tenant.entity.Tenant;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface TenantMapper {

    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "etablismentType", source = "type")
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "keycloakGroupId", ignore = true)
    @Mapping(target = "adminKeycloakId", ignore = true)
    @Mapping(target = "adminEmail", ignore = true)
    Tenant toEntity(CreateTenantRequest dto);

    @Mapping(target = "id", source = "tenantId")
    @Mapping(target = "type", source = "etablismentType")
    @Mapping(target = "adminUsername", ignore = true)
    @Mapping(target = "adminTempPassword", ignore = true)
    TenantResponse toResponse(Tenant tenant);

    List<TenantResponse> toResponseList(List<Tenant> tenants);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "etablismentType", source = "type")
    @Mapping(target = "active", ignore = true) // dérivé automatiquement par Tenant.setStatus()
    @Mapping(target = "keycloakGroupId", ignore = true)
    @Mapping(target = "adminKeycloakId", ignore = true)
    @Mapping(target = "adminEmail", ignore = true)
    void updateEntityFromRequest(UpdateTenantRequest request, @MappingTarget Tenant tenant);
}
