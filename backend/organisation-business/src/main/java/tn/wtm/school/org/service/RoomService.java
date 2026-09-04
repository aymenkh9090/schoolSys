package tn.wtm.school.org.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tn.wtm.school.org.dto.request.RoomRequest;
import tn.wtm.school.org.dto.response.RoomImportResult;
import tn.wtm.school.org.dto.response.RoomResponse;

import java.io.InputStream;
import java.util.List;

public interface RoomService {

    RoomResponse createRoom(RoomRequest request);

    RoomResponse getRoomById(Long id);

    RoomResponse getRoomByCode(String code);

    Page<RoomResponse> getRooms(Pageable pageable);

    List<RoomResponse> getRoomsByTenant();

    RoomResponse updateRoom(Long id, RoomRequest request);

    void deleteRoom(Long id);

    /** Importer des salles depuis un flux CSV. Colonnes requises : codeSalle, typeSalle */
    RoomImportResult importFromCsv(InputStream csvStream);

    /** Importer des salles depuis un flux Excel (.xls / .xlsx). Même colonnes que le CSV. */
    RoomImportResult importFromExcel(InputStream excelStream);
}
