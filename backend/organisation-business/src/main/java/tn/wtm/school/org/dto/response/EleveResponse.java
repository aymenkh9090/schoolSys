package tn.wtm.school.org.dto.response;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EleveResponse {

    private Long idEleve;
    private String codeEleve;
    private String nom;
    private String prenom;
    private String numIdentite;
    private String email;
    private String telephone;
    private Long classeId;
    private String classeCode;
    private Boolean estActif;
}
