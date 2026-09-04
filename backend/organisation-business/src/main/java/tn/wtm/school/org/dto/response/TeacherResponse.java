package tn.wtm.school.org.dto.response;


import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherResponse {


    Long idEnseignant;
    String codeEnseignant;
    String numIdentite;
    String nom;
    String prenom;
    String nomComplet;
    String email;
    String telephone;
    Integer maxHeuresSemaine;
    Integer maxHeuresJour;
    Integer minHeuresJour;
    Boolean estEnPoste;
    String photo;
    String specialite;
    Integer nombreAffectations;
    Double totalHeures;
    List<TeachingAssignmentResponse> affectations;





}
