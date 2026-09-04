package tn.wtm.school.tenant.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tn.wtm.school.tenant.dto.SubscriptionResponse;
import tn.wtm.school.tenant.entity.Subscription;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {

    @Mapping(target = "tenantId", source = "tenant.tenantId")
    SubscriptionResponse toResponse(Subscription subscription);

    List<SubscriptionResponse> toResponseList(List<Subscription> subscriptions);
}
