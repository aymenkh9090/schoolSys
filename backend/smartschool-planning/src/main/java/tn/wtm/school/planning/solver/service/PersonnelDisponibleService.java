package tn.wtm.school.planning.solver.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.wtm.school.common.service.TenantService;
import tn.wtm.school.org.dto.response.TeacherResponse;
import tn.wtm.school.org.entity.SchoolYear;
import tn.wtm.school.org.entity.Teacher;
import tn.wtm.school.org.entity.TimeSlot;
import tn.wtm.school.org.enums.DayPeriod;
import tn.wtm.school.org.mapper.TeacherMapper;
import tn.wtm.school.org.repository.SchoolYearRepository;
import tn.wtm.school.org.repository.TeacherRepository;
import tn.wtm.school.org.repository.TimeSlotRepository;
import tn.wtm.school.planning.solver.entity.GeneratedTimetable;
import tn.wtm.school.planning.solver.repository.GeneratedTimetableRepository;
import tn.wtm.school.planning.solver.repository.TimetableSessionRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Résout la liste des enseignants disponibles pour le pointage d'une date donnée,
 * en croisant l'emploi du temps publié (si présent) avec le jour de la semaine
 * (et, optionnellement, la période matin/après-midi). Sans emploi du temps
 * publié pour l'année scolaire courante — ou sans créneaux configurés pour la
 * période demandée — on retombe sur tous les enseignants en poste (comportement
 * dégradé mais utilisable).
 */
@Service
@RequiredArgsConstructor
public class PersonnelDisponibleService extends TenantService {

    private final SchoolYearRepository schoolYearRepository;
    private final GeneratedTimetableRepository generatedTimetableRepository;
    private final TimetableSessionRepository timetableSessionRepository;
    private final TeacherRepository teacherRepository;
    private final TeacherMapper teacherMapper;
    private final TimeSlotRepository timeSlotRepository;

    public List<TeacherResponse> findTeachersWorkingOn(LocalDate date) {
        return findTeachersWorkingOn(date, null);
    }

    public List<TeacherResponse> findTeachersWorkingOn(LocalDate date, DayPeriod periode) {
        String tenantId = currentTenant();
        DayOfWeek jour = date.getDayOfWeek();

        Optional<SchoolYear> anneeCourante = schoolYearRepository.findByTenantIdAndEstCouranteTrue(tenantId);
        if (anneeCourante.isEmpty()) {
            return tousLesEnseignantsEnPoste(tenantId);
        }

        Optional<GeneratedTimetable> emploiPublie = generatedTimetableRepository
                .findLatestPublishedByTenantIdAndAcademicYearId(tenantId, anneeCourante.get().getIdAnnee());
        if (emploiPublie.isEmpty()) {
            return tousLesEnseignantsEnPoste(tenantId);
        }

        List<String> codes;
        if (periode == null) {
            codes = timetableSessionRepository.findDistinctTeacherCodesByJobIdAndTenantIdAndDay(
                    emploiPublie.get().getJobId(), tenantId, jour);
        } else {
            Optional<LocalTime[]> plageHoraire = plageHorairePeriode(tenantId, jour, periode);
            if (plageHoraire.isEmpty()) {
                // Pas de créneaux configurés pour cette période ce jour-là : on ne bloque
                // pas le pointage, on retombe sur la disponibilité de la journée entière.
                return findTeachersWorkingOn(date);
            }
            LocalTime[] bornes = plageHoraire.get();
            codes = timetableSessionRepository.findDistinctTeacherCodesByJobIdAndTenantIdAndDayAndStartTimeBetween(
                    emploiPublie.get().getJobId(), tenantId, jour, bornes[0], bornes[1]);
        }

        if (codes.isEmpty()) {
            return List.of();
        }

        List<Teacher> teachers = teacherRepository
                .findByTenantIdAndCodeEnseignantInAndEstEnPosteTrueOrderByNomAscPrenomAsc(tenantId, codes);
        return teacherMapper.toResponseLightList(teachers);
    }

    private List<TeacherResponse> tousLesEnseignantsEnPoste(String tenantId) {
        return teacherMapper.toResponseLightList(
                teacherRepository.findByTenantIdAndEstEnPosteTrueOrderByNomAscPrenomAsc(tenantId));
    }

    /** Plage [début, fin) couvrant tous les créneaux configurés pour ce jour/période — vide si aucun créneau. */
    private Optional<LocalTime[]> plageHorairePeriode(String tenantId, DayOfWeek jour, DayPeriod periode) {
        List<TimeSlot> creneaux = timeSlotRepository
                .findByTenantIdAndDayOfWeekOrderByOrderIndexAsc(tenantId, jour).stream()
                .filter(t -> t.getDayPeriod() == periode)
                .toList();
        if (creneaux.isEmpty()) {
            return Optional.empty();
        }
        LocalTime debut = creneaux.stream().map(TimeSlot::getStartTime).min(Comparator.naturalOrder()).orElseThrow();
        LocalTime fin = creneaux.stream().map(TimeSlot::getEndTime).max(Comparator.naturalOrder()).orElseThrow();
        return Optional.of(new LocalTime[]{debut, fin});
    }
}
