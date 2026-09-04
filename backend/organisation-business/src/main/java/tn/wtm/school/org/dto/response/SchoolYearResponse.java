package tn.wtm.school.org.dto.response;


import lombok.*;


import java.time.LocalDate;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchoolYearResponse {

    Long idAnnee;
    String nom;
    LocalDate dateDebut;
    LocalDate dateFin;
    Boolean estActive;
    Boolean estCourante;
    Integer nombreClasses;
    Integer nombreAffectations;





}
