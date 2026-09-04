package tn.wtm.school.common.base;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.Filters;
import org.hibernate.annotations.ParamDef;
import tn.wtm.school.common.config.TenantEntityListener;

import static org.hibernate.binder.internal.TenantIdBinder.FILTER_NAME;
import static tn.wtm.school.common.config.TenantFilterConstants.PARAM_TENANT_ID;

@Getter @Setter @MappedSuperclass
@EntityListeners(TenantEntityListener.class)
@FilterDef(
        name = "tenantFilter",
        parameters = @ParamDef(name = "tenantId", type = String.class)
)
@Filter(
        name = "tenantFilter",
        condition = "tenant_id = :tenantId"
)
public abstract class TenantEntity extends BaseEntity{

    @Column(name = "tenant_id",nullable = false, updatable = false)
    private String tenantId;
}
