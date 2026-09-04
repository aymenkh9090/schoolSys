package tn.wtm.school.org.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchoolYearRequest {

    @NotBlank(message = "Le nom de l'année scolaire est obligatoire")
    @Size(max = 50, message = "Le nom ne doit pas dépasser 50 caractères")
    String nom;

    @NotNull(message = "La date de début est obligatoire")
    LocalDate dateDebut;

    @NotNull(message = "La date de fin est obligatoire")
    LocalDate dateFin;

    Boolean estActive;
    Boolean estCourante;


        @AssertTrue(message = "La date de fin doit être après la date de début")
        public boolean isDateRangeValid() {
            if (dateDebut == null || dateFin == null) return true;
            return dateFin.isAfter(dateDebut);
        }






    }
