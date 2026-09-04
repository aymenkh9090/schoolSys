package tn.wtm.school.common.validations;

import tn.wtm.school.common.exceptions.BadRequestException;

import java.time.LocalTime;


public final class ValidationUtils {

    private ValidationUtils() {
    }

    public static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(fieldName + " est obligatoire");
        }
        return value.trim();
    }

    public static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new BadRequestException(fieldName + " est obligatoire");
        }
        return value;
    }

    public static Integer requirePositiveOrZero(Integer value, String fieldName) {
        if (value == null || value < 0) {
            throw new BadRequestException(fieldName + " doit etre positif ou zero");
        }
        return value;
    }

    public static Double requirePositive(Double value, String fieldName) {
        if (value == null || value <= 0) {
            throw new BadRequestException(fieldName + " doit etre positif");
        }
        return value;
    }

    public static void requireTimeRange(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null) {
            throw new BadRequestException("Heure debut et heure fin sont obligatoires");
        }
        if (!startTime.isBefore(endTime)) {
            throw new BadRequestException("Heure debut doit etre avant heure fin");
        }
    }
}
