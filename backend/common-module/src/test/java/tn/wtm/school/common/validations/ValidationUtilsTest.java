package tn.wtm.school.common.validations;

import org.junit.jupiter.api.Test;
import tn.wtm.school.common.exceptions.BadRequestException;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

class ValidationUtilsTest {

    @Test
    void texteObligatoire() {
        assertThat(ValidationUtils.requireText("  7B1 ", "code")).isEqualTo("7B1");
        assertThatThrownBy(() -> ValidationUtils.requireText(null, "code"))
                .isInstanceOf(BadRequestException.class).hasMessage("code est obligatoire");
        assertThatThrownBy(() -> ValidationUtils.requireText("  ", "code"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void valeurNonNulle() {
        assertThat(ValidationUtils.requireNonNull(3L, "id")).isEqualTo(3L);
        assertThatThrownBy(() -> ValidationUtils.requireNonNull(null, "id"))
                .isInstanceOf(BadRequestException.class).hasMessage("id est obligatoire");
    }

    @Test
    void entierPositifOuNul() {
        assertThat(ValidationUtils.requirePositiveOrZero(0, "capacite")).isZero();
        assertThatThrownBy(() -> ValidationUtils.requirePositiveOrZero(-1, "capacite"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("positif ou zero");
        assertThatThrownBy(() -> ValidationUtils.requirePositiveOrZero(null, "capacite"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void decimalStrictementPositif() {
        assertThat(ValidationUtils.requirePositive(1.5, "coef")).isEqualTo(1.5);
        assertThatThrownBy(() -> ValidationUtils.requirePositive(0.0, "coef"))
                .isInstanceOf(BadRequestException.class).hasMessage("coef doit etre positif");
        assertThatThrownBy(() -> ValidationUtils.requirePositive(null, "coef"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void plageHoraire() {
        LocalTime huit = LocalTime.of(8, 0);
        LocalTime neuf = LocalTime.of(9, 0);

        assertThatNoException().isThrownBy(() -> ValidationUtils.requireTimeRange(huit, neuf));
        assertThatThrownBy(() -> ValidationUtils.requireTimeRange(null, neuf))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("obligatoires");
        assertThatThrownBy(() -> ValidationUtils.requireTimeRange(huit, null))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> ValidationUtils.requireTimeRange(neuf, huit))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("avant");
        assertThatThrownBy(() -> ValidationUtils.requireTimeRange(huit, huit))
                .isInstanceOf(BadRequestException.class);
    }
}
