package tn.wtm.school.org.dto.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LevelResponse {


    Long idNiveau;
    String nom;
    String code;
    String description;
    Boolean estActif;
    Integer nombreClasses;
    Integer nombreMatieres;
    List<ClassGroupResponse> classes;




}
