package tn.wtm.school.org.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.common.exceptions.ConflictException;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.common.service.TenantService;
import tn.wtm.school.common.validations.ObjectsValidator;
import tn.wtm.school.org.dto.request.RoomRequest;
import tn.wtm.school.org.dto.response.RoomImportResult;
import tn.wtm.school.org.dto.response.RoomResponse;
import tn.wtm.school.org.entity.Room;
import tn.wtm.school.org.enums.RoomType;
import tn.wtm.school.org.mapper.RoomMapper;
import tn.wtm.school.org.repository.RoomRepository;
import tn.wtm.school.org.service.RoomService;
import tn.wtm.school.org.util.ExcelImportHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class RoomServiceImpl extends TenantService implements RoomService {

    private final RoomRepository roomRepository;
    private final RoomMapper roomMapper;
    private final ObjectsValidator<RoomRequest> validator;

    @Override
    @Transactional
    public RoomResponse createRoom(RoomRequest request) {
        String tenantId = currentTenant();
        validator.validate(request);
        if (roomRepository.existsByTenantIdAndCodeSalle(tenantId, request.getCodeSalle())) {
            throw new ConflictException("Une salle avec le code " + request.getCodeSalle() + " existe deja");
        }
        return roomMapper.toResponse(roomRepository.save(roomMapper.toEntity(request)));
    }

    @Override
    public RoomResponse getRoomById(Long id) {
        return roomMapper.toResponse(findById(id));
    }

    @Override
    public RoomResponse getRoomByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BadRequestException("Le code de la salle est obligatoire");
        }
        Room room = roomRepository.findByTenantIdAndCodeSalle(currentTenant(), code.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Salle avec code " + code + " introuvable"));
        return roomMapper.toResponse(room);
    }

    @Override
    public Page<RoomResponse> getRooms(Pageable pageable) {
        return roomRepository.findByTenantId(currentTenant(), pageable).map(roomMapper::toResponse);
    }

    @Override
    public List<RoomResponse> getRoomsByTenant() {
        return roomRepository.findByTenantId(currentTenant()).stream().map(roomMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public RoomResponse updateRoom(Long id, RoomRequest request) {
        String tenantId = currentTenant();
        validator.validate(request);
        Room room = findById(id);
        if (roomRepository.existsByTenantIdAndCodeSalleAndIdSalleNot(tenantId, request.getCodeSalle(), id)) {
            throw new ConflictException("Une salle avec le code " + request.getCodeSalle() + " existe deja");
        }
        roomMapper.updateFromRequest(request, room);
        return roomMapper.toResponse(roomRepository.save(room));
    }

    @Override
    @Transactional
    public void deleteRoom(Long id) {
        roomRepository.delete(findById(id));
    }

    // ----- Import CSV --------------------------------------------------------

    @Override
    @Transactional
    public RoomImportResult importFromCsv(InputStream csvStream) {
        String tenantId = currentTenant();
        List<RoomImportResult.RowError> rowErrors = new ArrayList<>();
        int imported = 0;
        int skipped  = 0;

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .setCommentMarker('#')
                .build();

        try (CSVParser parser = CSVParser.parse(
                new InputStreamReader(csvStream, StandardCharsets.UTF_8), format)) {

            validateHeaders(parser.getHeaderNames());

            for (CSVRecord record : parser) {
                int line = (int) record.getRecordNumber() + 1;
                Map<String, String> row = record.toMap();
                String code = col(row, "codeSalle");
                try {
                    RoomRequest req = parseRecord(row);
                    validator.validate(req);
                    if (roomRepository.existsByTenantIdAndCodeSalle(tenantId, req.getCodeSalle())) {
                        rowErrors.add(error(line, code, "code déjà existant : " + req.getCodeSalle()));
                        skipped++;
                        continue;
                    }
                    roomRepository.save(roomMapper.toEntity(req));
                    imported++;
                } catch (Exception ex) {
                    rowErrors.add(error(line, code, ex.getMessage()));
                }
            }

        } catch (IOException ex) {
            throw new BadRequestException("Impossible de lire le fichier CSV : " + ex.getMessage());
        }

        log.info("[import-rooms] tenant={} imported={} skipped={} errors={}",
                tenantId, imported, skipped, rowErrors.size() - skipped);

        return buildResult(imported, skipped, rowErrors);
    }

    // ----- Import Excel ------------------------------------------------------

    @Override
    @Transactional
    public RoomImportResult importFromExcel(InputStream excelStream) {
        String tenantId = currentTenant();
        List<RoomImportResult.RowError> rowErrors = new ArrayList<>();
        int imported = 0;
        int skipped  = 0;

        ExcelImportHelper.ExcelData data = ExcelImportHelper.parse(excelStream);
        validateHeaders(data.headers());

        int lineOffset = 2; // header is row 1, data starts row 2
        for (int i = 0; i < data.rows().size(); i++) {
            int line = i + lineOffset;
            Map<String, String> row = data.rows().get(i);
            String code = col(row, "codeSalle");
            try {
                RoomRequest req = parseRecord(row);
                validator.validate(req);
                if (roomRepository.existsByTenantIdAndCodeSalle(tenantId, req.getCodeSalle())) {
                    rowErrors.add(error(line, code, "code déjà existant : " + req.getCodeSalle()));
                    skipped++;
                    continue;
                }
                roomRepository.save(roomMapper.toEntity(req));
                imported++;
            } catch (Exception ex) {
                rowErrors.add(error(line, code, ex.getMessage()));
            }
        }

        log.info("[import-rooms-excel] tenant={} imported={} skipped={} errors={}",
                tenantId, imported, skipped, rowErrors.size() - skipped);

        return buildResult(imported, skipped, rowErrors);
    }

    // ----- Parsing helpers ---------------------------------------------------

    private void validateHeaders(List<String> headers) {
        List<String> required = List.of("codeSalle", "typeSalle");
        List<String> missing = required.stream()
                .filter(h -> !headers.contains(h))
                .toList();
        if (!missing.isEmpty()) {
            throw new BadRequestException("Colonnes manquantes : " + missing);
        }
    }

    private RoomRequest parseRecord(Map<String, String> r) {
        return RoomRequest.builder()
                .codeSalle(col(r, "codeSalle"))
                .typeSalle(parseRoomType(r, "typeSalle"))
                .capacite(parseInt(r, "capacite"))
                .codeBloc(col(r, "codeBloc"))
                .numEtage(col(r, "numEtage"))
                .build();
    }

    private RoomType parseRoomType(Map<String, String> r, String name) {
        String v = col(r, name);
        if (v == null) return null;
        try {
            return RoomType.valueOf(v.toUpperCase().trim());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Type de salle invalide : " + v +
                    ". Valeurs acceptées : NORMALE, LABSCIENCE, LABPHYSIQUE, LABINFORMATIQUE, SALLESPORT");
        }
    }

    private String col(Map<String, String> r, String name) {
        String v = r.get(name);
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    private Integer parseInt(Map<String, String> r, String name) {
        String v = col(r, name);
        if (v == null) return null;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Valeur numérique invalide pour " + name + " : " + v);
        }
    }

    private RoomImportResult.RowError error(int line, String code, String reason) {
        return RoomImportResult.RowError.builder()
                .line(line).codeSalle(code).reason(reason).build();
    }

    private RoomImportResult buildResult(int imported, int skipped,
                                         List<RoomImportResult.RowError> rowErrors) {
        return RoomImportResult.builder()
                .imported(imported)
                .skipped(skipped)
                .errors(rowErrors.size() - skipped)
                .rowErrors(rowErrors)
                .build();
    }

    private Room findById(Long id) {
        if (id == null) {
            throw new BadRequestException("L'identifiant de la salle est obligatoire");
        }
        return roomRepository.findByTenantIdAndIdSalle(currentTenant(), id)
                .orElseThrow(() -> new ResourceNotFoundException("Salle avec ID " + id + " introuvable"));
    }
}

