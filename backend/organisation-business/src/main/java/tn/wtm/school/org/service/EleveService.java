package tn.wtm.school.org.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tn.wtm.school.org.dto.request.EleveRequest;
import tn.wtm.school.org.dto.response.EleveImportResult;
import tn.wtm.school.org.dto.response.EleveResponse;

import java.io.InputStream;
import java.util.List;

public interface EleveService {

    EleveResponse creer(EleveRequest requete);

    EleveResponse recupererParId(Long id);

    Page<EleveResponse> listerTous(Pageable pageable);

    Page<EleveResponse> listerParClasse(Long classeId, Pageable pageable);

    List<EleveResponse> listerActifsParClasse(Long classeId);

    List<Long> listerIdsParClasse(String tenantId, Long classeId);

    Page<EleveResponse> rechercher(String q, Pageable pageable);

    EleveResponse modifier(Long id, EleveRequest requete);

    EleveResponse toggleStatut(Long id, Boolean estActif);

    void supprimer(Long id);

    /** Importer des élèves depuis un flux CSV. Colonnes requises : codeEleve, nom, prenom, codeClasse */
    EleveImportResult importFromCsv(InputStream csvStream);

    /** Importer des élèves depuis un flux Excel (.xls / .xlsx). Même colonnes que le CSV. */
    EleveImportResult importFromExcel(InputStream excelStream);
}
