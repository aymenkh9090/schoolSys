package tn.wtm.school.planning.solver.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tn.wtm.school.common.exceptions.ResourceNotFoundException;
import tn.wtm.school.common.handler.GlobalExceptionHandler;
import tn.wtm.school.planning.solver.dto.response.ClassTimetableView;
import tn.wtm.school.planning.solver.dto.response.GeneratedTimetableResponse;
import tn.wtm.school.planning.solver.dto.response.PreflightResponse;
import tn.wtm.school.planning.solver.dto.response.TimetableJobResponse;
import tn.wtm.school.planning.solver.dto.response.TimetableSessionResponse;
import tn.wtm.school.planning.solver.dto.response.TimetableSolutionResponse;
import tn.wtm.school.planning.solver.enums.SolverStatus;
import tn.wtm.school.planning.solver.service.TimetableSolverService;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le contrat HTTP du moteur de génération d'emplois du temps.
 *
 * <p>Montage {@code standaloneSetup} — voir {@link tn.wtm.school.planning.constraints.web.CustomConstraintControllerTest}
 * pour le pourquoi. Les {@code @PreAuthorize} (lecture ouverte à
 * {@code SURVEILLANT}, génération et édition réservées à {@code SCHOOL_ADMIN})
 * sont vérifiés par {@link tn.wtm.school.planning.PlanningControllerSecuriteTest}.
 * On regarde ici : le routage (variables de chemin, paramètres requis /
 * optionnels), la validation des corps, les codes de retour propres au moteur
 * (202 pour une génération asynchrone, 204 pour une suppression) et la
 * délégation au service.
 */
@ExtendWith(MockitoExtension.class)
class TimetableControllerTest {

    @Mock private TimetableSolverService solverService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TimetableController(solverService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── preflight & génération ───────────────────────────────────────────────

    @Test
    void preflightForwardsSchoolYearAndReturns200() throws Exception {
        when(solverService.preflight(2026L, null)).thenReturn(
                PreflightResponse.builder().ready(true).blockingCount(0).warningCount(2).build());

        mockMvc.perform(get("/api/planning/timetable/preflight").param("schoolYearId", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(true))
                .andExpect(jsonPath("$.warningCount").value(2));

        verify(solverService).preflight(2026L, null);
    }

    @Test
    void preflightWithoutSchoolYearReturns400() throws Exception {
        mockMvc.perform(get("/api/planning/timetable/preflight"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(solverService);
    }

    @Test
    void generateValidReturns202Accepted() throws Exception {
        when(solverService.startGeneration(any())).thenReturn(jobResponse(SolverStatus.PENDING));

        mockMvc.perform(post("/api/planning/timetable/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schoolYearId\":2026,\"constraintProfileId\":4}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(88))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void generateWithoutSchoolYearReturns400() throws Exception {
        mockMvc.perform(post("/api/planning/timetable/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"constraintProfileId\":4}"))
                .andExpect(status().isBadRequest());
        verify(solverService, never()).startGeneration(any());
    }

    // ── cycle de vie d'un job ────────────────────────────────────────────────

    @Test
    void listJobsWithoutYearPassesNull() throws Exception {
        when(solverService.listJobs(null)).thenReturn(List.of(jobResponse(SolverStatus.SOLVED)));

        mockMvc.perform(get("/api/planning/timetable/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        verify(solverService).listJobs(isNull());
    }

    @Test
    void listJobsForwardsAcademicYearWhenPresent() throws Exception {
        when(solverService.listJobs(2026L)).thenReturn(List.of());

        mockMvc.perform(get("/api/planning/timetable/jobs").param("academicYearId", "2026"))
                .andExpect(status().isOk());

        verify(solverService).listJobs(2026L);
    }

    @Test
    void getJobStatusReturns200() throws Exception {
        when(solverService.getJobStatus(88L)).thenReturn(jobResponse(SolverStatus.RUNNING));

        mockMvc.perform(get("/api/planning/timetable/jobs/88"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void getJobStatusUnknownReturns404() throws Exception {
        when(solverService.getJobStatus(404L))
                .thenThrow(new ResourceNotFoundException("Job 404 introuvable"));

        mockMvc.perform(get("/api/planning/timetable/jobs/404"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelReturns200AndDelegates() throws Exception {
        when(solverService.cancelJob(88L)).thenReturn(jobResponse(SolverStatus.CANCELLED));

        mockMvc.perform(post("/api/planning/timetable/jobs/88/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(solverService).cancelJob(88L);
    }

    @Test
    void deleteJobReturns204AndDelegates() throws Exception {
        mockMvc.perform(delete("/api/planning/timetable/jobs/88"))
                .andExpect(status().isNoContent());

        verify(solverService).deleteJob(88L);
    }

    // ── consultation du résultat ─────────────────────────────────────────────

    @Test
    void getSolutionReturns200() throws Exception {
        when(solverService.getSolution(88L))
                .thenReturn(TimetableSolutionResponse.builder().build());

        mockMvc.perform(get("/api/planning/timetable/jobs/88/solution"))
                .andExpect(status().isOk());
    }

    @Test
    void getResultUnknownReturns404() throws Exception {
        when(solverService.getResult(88L))
                .thenThrow(new ResourceNotFoundException("Pas de résultat persisté pour le job 88"));

        mockMvc.perform(get("/api/planning/timetable/jobs/88/result"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getClassTimetableForwardsBothPathVariables() throws Exception {
        when(solverService.getClassTimetable(88L, "3A"))
                .thenReturn(classView("3A"));

        mockMvc.perform(get("/api/planning/timetable/jobs/88/view/class/3A"))
                .andExpect(status().isOk());

        verify(solverService).getClassTimetable(88L, "3A");
    }

    @Test
    void getAllClassTimetablesReturns200() throws Exception {
        when(solverService.getAllClassTimetables(88L)).thenReturn(List.of(classView("3A"), classView("3B")));

        mockMvc.perform(get("/api/planning/timetable/jobs/88/view/classes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    // ── édition post-génération ──────────────────────────────────────────────

    @Test
    void moveSessionValidReturns200() throws Exception {
        when(solverService.moveSession(eq(88L), eq(500L), any()))
                .thenReturn(TimetableSessionResponse.builder()
                        .idTimetableSession(500L).day(DayOfWeek.TUESDAY).startTime(LocalTime.of(10, 0)).build());

        mockMvc.perform(patch("/api/planning/timetable/jobs/88/sessions/500")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"TUESDAY\",\"startTime\":\"10:00\",\"roomCode\":\"B12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idTimetableSession").value(500));

        verify(solverService).moveSession(eq(88L), eq(500L), any());
    }

    @Test
    void moveSessionWithoutDayReturns400() throws Exception {
        mockMvc.perform(patch("/api/planning/timetable/jobs/88/sessions/500")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"10:00\"}"))
                .andExpect(status().isBadRequest());
        verify(solverService, never()).moveSession(any(), any(), any());
    }

    // ── publication ──────────────────────────────────────────────────────────

    @Test
    void listGeneratedReturns200() throws Exception {
        when(solverService.listGeneratedTimetables(null))
                .thenReturn(List.of(generatedResponse()));

        mockMvc.perform(get("/api/planning/timetable/generated"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("DRAFT"));
    }

    @Test
    void publishReturns200AndDelegates() throws Exception {
        GeneratedTimetableResponse published = generatedResponse();
        published.setStatus("PUBLISHED");
        when(solverService.publishTimetable(12L)).thenReturn(published);

        mockMvc.perform(post("/api/planning/timetable/generated/12/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        verify(solverService).publishTimetable(12L);
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private static ClassTimetableView classView(String classCode) {
        return new ClassTimetableView(88L, classCode, SolverStatus.SOLVED, "0hard/0medium/-5soft", List.of());
    }

    private static TimetableJobResponse jobResponse(SolverStatus status) {
        return TimetableJobResponse.builder()
                .jobId(88L)
                .schoolYearId(2026L)
                .constraintProfileId(4L)
                .status(status)
                .build();
    }

    private static GeneratedTimetableResponse generatedResponse() {
        return GeneratedTimetableResponse.builder()
                .id(12L)
                .jobId(88L)
                .academicYearId(2026L)
                .scoreAchieved("0hard/0medium/-5soft")
                .feasible(true)
                .totalSessions(400)
                .status("DRAFT")
                .build();
    }
}
