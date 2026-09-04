package tn.wtm.school.org.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.common.exceptions.ConflictException;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.common.exceptions.TenantSecurityException;
import tn.wtm.school.common.validations.ObjectsValidator;
import tn.wtm.school.org.dto.request.RoomRequest;
import tn.wtm.school.org.dto.response.RoomResponse;
import tn.wtm.school.org.entity.Room;
import tn.wtm.school.org.mapper.RoomMapper;
import tn.wtm.school.org.repository.RoomRepository;
import tn.wtm.school.org.service.impl.RoomServiceImpl;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock RoomRepository roomRepository;
    @Mock RoomMapper     roomMapper;
    @Mock ObjectsValidator<RoomRequest> validator;

    RoomServiceImpl service;

    static final String TENANT = "tenant-1";
    static final Long   R_ID   = 10L;

    @BeforeEach
    void setUp() {
        service = new RoomServiceImpl(roomRepository, roomMapper, validator);
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    // ── helpers ───────────────────────────────────────────────────────────────

    RoomRequest req(String code) {
        return RoomRequest.builder().codeSalle(code).typeSalle(tn.wtm.school.org.enums.RoomType.NORMALE).capacite(30).build();
    }

    Room room(Long id, String code) {
        return Room.builder().idSalle(id).codeSalle(code).typeSalle(tn.wtm.school.org.enums.RoomType.NORMALE).build();
    }

    RoomResponse resp(Long id, String code) {
        return RoomResponse.builder().idSalle(id).codeSalle(code).build();
    }

    void withTenant() { TenantContext.setTenantId(TENANT); }

    // ── CREATE ────────────────────────────────────────────────────────────────

    @Nested
    class Create {

        @Test
        void noTenant_throwsTenantSecurityException() {
            assertThatThrownBy(() -> service.createRoom(req("S1")))
                    .isInstanceOf(TenantSecurityException.class);
            verify(roomRepository, never()).save(any());
        }

        @Test
        void duplicateCode_throwsConflict() {
            withTenant();
            when(roomRepository.existsByTenantIdAndCodeSalle(TENANT, "S1")).thenReturn(true);

            assertThatThrownBy(() -> service.createRoom(req("S1")))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("S1");
        }

        @Test
        void happyPath_savesAndReturnsResponse() {
            withTenant();
            Room entity = room(R_ID, "S1");
            when(roomMapper.toEntity(any())).thenReturn(entity);
            when(roomRepository.save(entity)).thenReturn(entity);
            when(roomMapper.toResponse(entity)).thenReturn(resp(R_ID, "S1"));

            RoomResponse result = service.createRoom(req("S1"));

            assertThat(result.getCodeSalle()).isEqualTo("S1");
            verify(roomRepository).save(entity);
        }
    }

    // ── READ ──────────────────────────────────────────────────────────────────

    @Nested
    class Read {

        @Test
        void getById_nullId_throwsBadRequest() {
            withTenant();
            assertThatThrownBy(() -> service.getRoomById(null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void getById_notFound_throwsResourceNotFound() {
            withTenant();
            when(roomRepository.findByTenantIdAndIdSalle(TENANT, R_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getRoomById(R_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(R_ID.toString());
        }

        @Test
        void getById_tenantIsolation_usesTenantscopedQuery() {
            withTenant();
            Room entity = room(R_ID, "S1");
            when(roomRepository.findByTenantIdAndIdSalle(TENANT, R_ID)).thenReturn(Optional.of(entity));
            when(roomMapper.toResponse(entity)).thenReturn(resp(R_ID, "S1"));

            service.getRoomById(R_ID);

            verify(roomRepository).findByTenantIdAndIdSalle(TENANT, R_ID);
        }

        @Test
        void getByCode_nullCode_throwsBadRequest() {
            withTenant();
            assertThatThrownBy(() -> service.getRoomByCode(null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void getByCode_blankCode_throwsBadRequest() {
            withTenant();
            assertThatThrownBy(() -> service.getRoomByCode("  "))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void getByCode_notFound_throwsResourceNotFound() {
            withTenant();
            when(roomRepository.findByTenantIdAndCodeSalle(TENANT, "S9")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getRoomByCode("S9"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void getByCode_trimsThenSearches() {
            withTenant();
            Room entity = room(R_ID, "S1");
            when(roomRepository.findByTenantIdAndCodeSalle(TENANT, "S1")).thenReturn(Optional.of(entity));
            when(roomMapper.toResponse(entity)).thenReturn(resp(R_ID, "S1"));

            service.getRoomByCode("  S1  ");

            verify(roomRepository).findByTenantIdAndCodeSalle(TENANT, "S1");
        }

        @Test
        void getRoomsByTenant_tenantIsolation_usesTenantscopedQuery() {
            withTenant();
            Room entity = room(R_ID, "S1");
            when(roomRepository.findByTenantId(TENANT)).thenReturn(List.of(entity));
            when(roomMapper.toResponse(entity)).thenReturn(resp(R_ID, "S1"));

            List<RoomResponse> result = service.getRoomsByTenant();

            assertThat(result).hasSize(1);
            verify(roomRepository).findByTenantId(TENANT);
        }
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    @Nested
    class Update {

        @Test
        void notFound_throwsResourceNotFound() {
            withTenant();
            when(roomRepository.findByTenantIdAndIdSalle(TENANT, R_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateRoom(R_ID, req("S1")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void duplicateCode_throwsConflict() {
            withTenant();
            Room entity = room(R_ID, "S1");
            when(roomRepository.findByTenantIdAndIdSalle(TENANT, R_ID)).thenReturn(Optional.of(entity));
            when(roomRepository.existsByTenantIdAndCodeSalleAndIdSalleNot(TENANT, "S2", R_ID)).thenReturn(true);

            assertThatThrownBy(() -> service.updateRoom(R_ID, req("S2")))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void happyPath_updatesAndReturnsResponse() {
            withTenant();
            Room entity = room(R_ID, "S1");
            when(roomRepository.findByTenantIdAndIdSalle(TENANT, R_ID)).thenReturn(Optional.of(entity));
            when(roomRepository.save(entity)).thenReturn(entity);
            when(roomMapper.toResponse(entity)).thenReturn(resp(R_ID, "S1-new"));

            RoomResponse result = service.updateRoom(R_ID, req("S1-new"));

            assertThat(result.getCodeSalle()).isEqualTo("S1-new");
            verify(roomRepository).save(entity);
        }
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    @Nested
    class Delete {

        @Test
        void notFound_throwsResourceNotFound() {
            withTenant();
            when(roomRepository.findByTenantIdAndIdSalle(TENANT, R_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteRoom(R_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(roomRepository, never()).delete(any());
        }

        @Test
        void happyPath_deletesRoom() {
            withTenant();
            Room entity = room(R_ID, "S1");
            when(roomRepository.findByTenantIdAndIdSalle(TENANT, R_ID)).thenReturn(Optional.of(entity));

            service.deleteRoom(R_ID);

            verify(roomRepository).delete(entity);
        }
    }
}
