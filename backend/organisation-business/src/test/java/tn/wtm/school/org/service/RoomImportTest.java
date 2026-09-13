package tn.wtm.school.org.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import tn.wtm.school.common.context.TenantContext;
import tn.wtm.school.common.exceptions.BadRequestException;
import tn.wtm.school.common.validations.ObjectsValidator;
import tn.wtm.school.org.dto.request.RoomRequest;
import tn.wtm.school.org.dto.response.RoomImportResult;
import tn.wtm.school.org.dto.response.RoomResponse;
import tn.wtm.school.org.entity.Room;
import tn.wtm.school.org.enums.RoomType;
import tn.wtm.school.org.mapper.RoomMapper;
import tn.wtm.school.org.repository.RoomRepository;
import tn.wtm.school.org.service.impl.RoomServiceImpl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Import des salles depuis un fichier : une ligne fautive est rapportée avec
 * son numéro et sa raison, sans empêcher l'import des autres.
 */
@ExtendWith(MockitoExtension.class)
class RoomImportTest {

    @Mock RoomRepository roomRepository;
    @Mock RoomMapper     roomMapper;
    @Mock ObjectsValidator<RoomRequest> validator;

    RoomServiceImpl service;

    static final String TENANT = "tenant-1";

    @BeforeEach
    void setUp() {
        service = new RoomServiceImpl(roomRepository, roomMapper, validator);
        TenantContext.setTenantId(TENANT);
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    static InputStream csv(String contenu) {
        return new ByteArrayInputStream(contenu.getBytes(StandardCharsets.UTF_8));
    }

    static InputStream classeur(String[]... lignes) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet feuille = wb.createSheet("salles");
            for (int i = 0; i < lignes.length; i++) {
                Row ligne = feuille.createRow(i);
                for (int j = 0; j < lignes[i].length; j++) {
                    if (lignes[i][j] != null) ligne.createCell(j).setCellValue(lignes[i][j]);
                }
            }
            wb.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    @Test
    void pagination_filtreeParTenant() {
        Pageable page = PageRequest.of(0, 10);
        Room salle = Room.builder().idSalle(1L).codeSalle("S1").build();
        when(roomRepository.findByTenantId(TENANT, page)).thenReturn(new PageImpl<>(List.of(salle)));
        when(roomMapper.toResponse(salle)).thenReturn(RoomResponse.builder().codeSalle("S1").build());

        assertThat(service.getRooms(page).getContent()).extracting(RoomResponse::getCodeSalle).containsExactly("S1");
    }

    @Test
    void csv_colonneManquante_refuseLeFichier() {
        assertThatThrownBy(() -> service.importFromCsv(csv("codeSalle,capacite\nS1,30\n")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("typeSalle");
    }

    @Test
    void csv_fluxIllisible_badRequest() {
        InputStream casse = new InputStream() {
            @Override public int read() throws IOException { throw new IOException("lecture"); }
        };

        assertThatThrownBy(() -> service.importFromCsv(casse))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("lecture");
    }

    @Test
    void csv_lignesValidesImportees_fautivesRapportees() {
        when(roomRepository.existsByTenantIdAndCodeSalle(TENANT, "S1")).thenReturn(false);
        when(roomRepository.existsByTenantIdAndCodeSalle(TENANT, "S2")).thenReturn(true);
        // lenient : validate est aussi appelé, sans lever, pour les autres lignes.
        lenient().doThrow(new BadRequestException("capacité négative"))
                .when(validator).validate(argThat(r -> "S6".equals(r.getCodeSalle())));

        String contenu = """
                codeSalle,typeSalle,capacite,codeBloc,numEtage
                S1,labscience,30,B,1
                S2,NORMALE,25,,
                S3,PISCINE,20,,
                S4,NORMALE,trente,,
                S6,NORMALE,-5,,
                """;

        RoomImportResult resultat = service.importFromCsv(csv(contenu));

        assertThat(resultat.getImported()).isEqualTo(1);
        assertThat(resultat.getSkipped()).isEqualTo(1);
        assertThat(resultat.getErrors()).isEqualTo(3);
        assertThat(resultat.getRowErrors()).extracting(RoomImportResult.RowError::getLine).containsExactly(3, 4, 5, 6);
        assertThat(resultat.getRowErrors()).extracting(RoomImportResult.RowError::getReason)
                .satisfiesExactly(
                        r -> assertThat(r).isEqualTo("code déjà existant : S2"),
                        r -> assertThat(r).startsWith("Type de salle invalide : PISCINE"),
                        r -> assertThat(r).isEqualTo("Valeur numérique invalide pour capacite : trente"),
                        r -> assertThat(r).isEqualTo("capacité négative"));

        ArgumentCaptor<RoomRequest> lue = ArgumentCaptor.forClass(RoomRequest.class);
        verify(roomMapper).toEntity(lue.capture());
        assertThat(lue.getValue().getTypeSalle()).isEqualTo(RoomType.LABSCIENCE);
        assertThat(lue.getValue().getCapacite()).isEqualTo(30);
        assertThat(lue.getValue().getCodeBloc()).isEqualTo("B");
        assertThat(lue.getValue().getNumEtage()).isEqualTo("1");
    }

    @Test
    void csv_champsFacultatifsVides_restentNuls() {
        when(roomRepository.existsByTenantIdAndCodeSalle(eq(TENANT), any())).thenReturn(false);

        service.importFromCsv(csv("codeSalle,typeSalle,capacite\nS1,,\n"));

        ArgumentCaptor<RoomRequest> lue = ArgumentCaptor.forClass(RoomRequest.class);
        verify(roomMapper).toEntity(lue.capture());
        assertThat(lue.getValue().getTypeSalle()).isNull();
        assertThat(lue.getValue().getCapacite()).isNull();
    }

    @Test
    void excel_colonneManquante_refuseLeFichier() throws IOException {
        InputStream fichier = classeur(new String[]{"codeSalle"});

        assertThatThrownBy(() -> service.importFromExcel(fichier))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("typeSalle");
        verify(roomRepository, never()).save(any());
    }

    @Test
    void excel_lignesValidesImportees_fautivesRapporteesAvecLeurNumero() throws IOException {
        when(roomRepository.existsByTenantIdAndCodeSalle(TENANT, "L1")).thenReturn(false);
        when(roomRepository.existsByTenantIdAndCodeSalle(TENANT, "L2")).thenReturn(true);

        RoomImportResult resultat = service.importFromExcel(classeur(
                new String[]{"codeSalle", "typeSalle", "capacite"},
                new String[]{"L1", "LABINFORMATIQUE", "18"},
                new String[]{"L2", "NORMALE", "30"},
                new String[]{"L3", "NORMALE", "beaucoup"}));

        assertThat(resultat.getImported()).isEqualTo(1);
        assertThat(resultat.getSkipped()).isEqualTo(1);
        assertThat(resultat.getErrors()).isEqualTo(1);
        assertThat(resultat.getRowErrors()).extracting(RoomImportResult.RowError::getLine).containsExactly(3, 4);
        assertThat(resultat.getRowErrors()).extracting(RoomImportResult.RowError::getCodeSalle)
                .containsExactly("L2", "L3");
    }
}
