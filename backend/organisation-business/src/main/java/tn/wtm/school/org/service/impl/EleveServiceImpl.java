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
import tn.wtm.school.org.dto.request.EleveRequest;
import tn.wtm.school.org.dto.response.EleveImportResult;
import tn.wtm.school.org.dto.response.EleveResponse;
import tn.wtm.school.org.entity.ClassGroup;
import tn.wtm.school.org.entity.Eleve;
import tn.wtm.school.org.mapper.EleveMapper;
import tn.wtm.school.org.repository.ClassGroupRepository;
import tn.wtm.school.org.repository.EleveRepository;
import tn.wtm.school.org.service.EleveService;
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
public class EleveServiceImpl extends TenantService implements EleveService {

    private final EleveRepository eleveRepository;
    private final ClassGroupRepository classGroupRepository;
    private final EleveMapper eleveMapper;

    @Override
    @Transactional
    public EleveResponse creer(EleveRequest requete) {
        String tenantId = currentTenant();

        if (eleveRepository.existsByTenantIdAndCodeEleve(tenantId, requete.getCodeEleve())) {
            throw new ConflictException("Un élève avec le code " + requete.getCodeEleve() + " existe déjà");
        }

        ClassGroup classe = trouverClasse(tenantId, requete.getClasseId());

        Eleve eleve = eleveMapper.toEntity(requete);
        eleve.setClasseGroup(classe);
        return eleveMapper.toResponse(eleveRepository.save(eleve));
    }

    @Override
    public EleveResponse recupererParId(Long id) {
        return eleveMapper.toResponse(trouverEleve(id));
    }

    @Override
    public Page<EleveResponse> listerTous(Pageable pageable) {
        return eleveRepository.findByTenantId(currentTenant(), pageable)
                .map(eleveMapper::toResponse);
    }

    @Override
    public Page<EleveResponse> rechercher(String q, Pageable pageable) {
        if (q == null || q.isBlank()) {
            return listerTous(pageable);
        }
        return eleveRepository.searchEleves(currentTenant(), q.trim(), pageable)
                .map(eleveMapper::toResponse);
    }

    @Override
    public Page<EleveResponse> listerParClasse(Long classeId, Pageable pageable) {
        if (classeId == null) {
            throw new BadRequestException("L'identifiant de la classe est obligatoire");
        }
        return eleveRepository.findByTenantIdAndClasseGroup_IdClasse(currentTenant(), classeId, pageable)
                .map(eleveMapper::toResponse);
    }

    @Override
    public List<EleveResponse> listerActifsParClasse(Long classeId) {
        if (classeId == null) {
            throw new BadRequestException("L'identifiant de la classe est obligatoire");
        }
        return eleveMapper.toResponseList(
                eleveRepository.findByTenantIdAndClasseGroup_IdClasseAndEstActifTrue(currentTenant(), classeId));
    }

    @Override
    public List<Long> listerIdsParClasse(String tenantId, Long classeId) {
        if (classeId == null) {
            throw new BadRequestException("L'identifiant de la classe est obligatoire");
        }
        String tenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : currentTenant();
        return eleveRepository.findIdsByTenantIdAndClasseId(tenant, classeId);
    }

    @Override
    @Transactional
    public EleveResponse modifier(Long id, EleveRequest requete) {
        String tenantId = currentTenant();
        Eleve eleve = trouverEleve(id);

        if (eleveRepository.existsByTenantIdAndCodeEleveAndIdEleveNot(tenantId, requete.getCodeEleve(), id)) {
            throw new ConflictException("Un élève avec le code " + requete.getCodeEleve() + " existe déjà");
        }

        ClassGroup classe = trouverClasse(tenantId, requete.getClasseId());
        eleveMapper.updateFromRequest(requete, eleve);
        eleve.setClasseGroup(classe);
        return eleveMapper.toResponse(eleveRepository.save(eleve));
    }

    @Override
    @Transactional
    public EleveResponse toggleStatut(Long id, Boolean estActif) {
        if (estActif == null) {
            throw new BadRequestException("Le statut est obligatoire");
        }
        Eleve eleve = trouverEleve(id);
        eleve.setEstActif(estActif);
        return eleveMapper.toResponse(eleveRepository.save(eleve));
    }

    @Override
    @Transactional
    public void supprimer(Long id) {
        eleveRepository.delete(trouverEleve(id));
    }

    // ── Import CSV ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public EleveImportResult importFromCsv(InputStream csvStream) {
        String tenantId = currentTenant();
        List<EleveImportResult.RowError> rowErrors = new ArrayList<>();
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
                String code = col(row, "codeEleve");
                try {
                    int[] counts = processEleveRow(row, line, code, tenantId, rowErrors);
                    imported += counts[0];
                    skipped  += counts[1];
                } catch (Exception ex) {
                    rowErrors.add(error(line, code, ex.getMessage()));
                }
            }

        } catch (IOException ex) {
            throw new BadRequestException("Impossible de lire le fichier CSV : " + ex.getMessage());
        }

        log.info("[import-eleves] tenant={} imported={} skipped={} errors={}",
                tenantId, imported, skipped, rowErrors.size() - skipped);

        return buildResult(imported, skipped, rowErrors);
    }

    // ── Import Excel ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public EleveImportResult importFromExcel(InputStream excelStream) {
        String tenantId = currentTenant();
        List<EleveImportResult.RowError> rowErrors = new ArrayList<>();
        int imported = 0;
        int skipped  = 0;

        ExcelImportHelper.ExcelData data = ExcelImportHelper.parse(excelStream);
        validateHeaders(data.headers());

        int lineOffset = 2;
        for (int i = 0; i < data.rows().size(); i++) {
            int line = i + lineOffset;
            Map<String, String> row = data.rows().get(i);
            String code = col(row, "codeEleve");
            try {
                int[] counts = processEleveRow(row, line, code, tenantId, rowErrors);
                imported += counts[0];
                skipped  += counts[1];
            } catch (Exception ex) {
                rowErrors.add(error(line, code, ex.getMessage()));
            }
        }

        log.info("[import-eleves-excel] tenant={} imported={} skipped={} errors={}",
                tenantId, imported, skipped, rowErrors.size() - skipped);

        return buildResult(imported, skipped, rowErrors);
    }

    // ── Shared row processing ─────────────────────────────────────────────────

    /** Returns [importedDelta, skippedDelta] */
    private int[] processEleveRow(Map<String, String> row, int line, String code,
                                   String tenantId,
                                   List<EleveImportResult.RowError> rowErrors) {
        String codeClasse = col(row, "codeClasse");
        if (codeClasse == null) {
            rowErrors.add(error(line, code, "codeClasse est obligatoire"));
            return new int[]{0, 0};
        }

        ClassGroup classe = classGroupRepository.findByTenantIdAndCode(tenantId, codeClasse).orElse(null);
        if (classe == null) {
            rowErrors.add(error(line, code, "classe introuvable : " + codeClasse));
            return new int[]{0, 0};
        }

        if (code == null || code.isBlank()) {
            rowErrors.add(error(line, code, "codeEleve est obligatoire"));
            return new int[]{0, 0};
        }
        if (eleveRepository.existsByTenantIdAndCodeEleve(tenantId, code)) {
            rowErrors.add(error(line, code, "code déjà existant : " + code));
            return new int[]{0, 1};
        }

        String nom    = col(row, "nom");
        String prenom = col(row, "prenom");
        if (nom == null) {
            rowErrors.add(error(line, code, "nom est obligatoire"));
            return new int[]{0, 0};
        }
        if (prenom == null) {
            rowErrors.add(error(line, code, "prenom est obligatoire"));
            return new int[]{0, 0};
        }

        Eleve eleve = Eleve.builder()
                .codeEleve(code)
                .nom(nom)
                .prenom(prenom)
                .numIdentite(col(row, "numIdentite"))
                .email(col(row, "email"))
                .telephone(col(row, "telephone"))
                .classeGroup(classe)
                .estActif(true)
                .build();
        eleve.setTenantId(tenantId);
        eleveRepository.save(eleve);
        return new int[]{1, 0};
    }

    // ── Parsing helpers ───────────────────────────────────────────────────────

    private void validateHeaders(List<String> headers) {
        List<String> required = List.of("codeEleve", "nom", "prenom", "codeClasse");
        List<String> missing = required.stream()
                .filter(h -> !headers.contains(h))
                .toList();
        if (!missing.isEmpty()) {
            throw new BadRequestException("Colonnes manquantes : " + missing);
        }
    }

    private String col(Map<String, String> r, String name) {
        String v = r.get(name);
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    private EleveImportResult.RowError error(int line, String code, String reason) {
        return EleveImportResult.RowError.builder()
                .line(line).codeEleve(code).reason(reason).build();
    }

    private EleveImportResult buildResult(int imported, int skipped,
                                          List<EleveImportResult.RowError> rowErrors) {
        return EleveImportResult.builder()
                .imported(imported)
                .skipped(skipped)
                .errors(rowErrors.size() - skipped)
                .rowErrors(rowErrors)
                .build();
    }

    // ── Privé ─────────────────────────────────────────────────────────────────

    private Eleve trouverEleve(Long id) {
        if (id == null) {
            throw new BadRequestException("L'identifiant de l'élève est obligatoire");
        }
        return eleveRepository.findByTenantIdAndIdEleve(currentTenant(), id)
                .orElseThrow(() -> new ResourceNotFoundException("Élève introuvable : " + id));
    }

    private ClassGroup trouverClasse(String tenantId, Long classeId) {
        if (classeId == null) {
            throw new BadRequestException("L'identifiant de la classe est obligatoire");
        }
        return classGroupRepository.findByTenantIdAndIdClasse(tenantId, classeId)
                .orElseThrow(() -> new ResourceNotFoundException("Classe introuvable : " + classeId));
    }
}
