package tn.wtm.school.org.service;

import tn.wtm.school.org.dto.request.CreateSchoolUserRequest;
import tn.wtm.school.org.dto.request.UpdateSchoolUserRequest;
import tn.wtm.school.org.dto.response.SchoolUserResponse;
import tn.wtm.school.org.enums.UserRole;

import java.util.List;

public interface SchoolUserService {

    SchoolUserResponse createUser(CreateSchoolUserRequest request);

    List<SchoolUserResponse> getAllUsers();

    List<SchoolUserResponse> getActiveUsers();

    List<SchoolUserResponse> getUsersByRole(UserRole role);

    SchoolUserResponse getUserById(Long userId);

    /**
     * Identifiant interne du compte lié au JWT courant, ou {@code null} si aucun
     * compte du tenant ne correspond. Sert à tracer l'auteur d'une action sans
     * demander son identifiant à l'utilisateur.
     */
    Long getCurrentUserId();

    SchoolUserResponse updateUser(Long userId, UpdateSchoolUserRequest request);

    SchoolUserResponse deactivateUser(Long userId);

    SchoolUserResponse reactivateUser(Long userId);
}
