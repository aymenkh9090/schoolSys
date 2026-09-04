package tn.wtm.school.common.config;

import jakarta.persistence.PrePersist;
import org.springframework.stereotype.Component;
import tn.wtm.school.common.base.TenantEntity;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.common.exceptions.TenantSecurityException;

@Component
public class TenantEntityListener {

    @PrePersist
    public void prePersist(Object entity) {
        if (entity instanceof TenantEntity tenantEntity) {

            if (!TenantContext.hasTenant()) {
                throw new TenantSecurityException("TenantId obligatoire pour créer une donnée métier.");
            }

            tenantEntity.setTenantId(TenantContext.getTenantId());
        }
    }



}
