package tn.wtm.school.planning.constraints.service;

import tn.wtm.school.planning.constraints.dto.request.ConstraintProfileRequest;
import tn.wtm.school.planning.constraints.dto.request.CreateDefaultProfileRequest;
import tn.wtm.school.planning.constraints.dto.request.ConstraintSettingRequest;
import tn.wtm.school.planning.constraints.dto.response.ConstraintProfileResponse;
import tn.wtm.school.planning.constraints.dto.response.ConstraintSettingResponse;

import java.util.List;

public interface ConstraintProfileService {

    ConstraintProfileResponse create(ConstraintProfileRequest request);

    /**
     * Crée un profil et le peuple automatiquement avec toutes les définitions
     * de contraintes existantes, en utilisant leurs valeurs par défaut.
     */
    ConstraintProfileResponse createDefault(CreateDefaultProfileRequest request);

    List<ConstraintProfileResponse> findAll();

    ConstraintProfileResponse findById(Long id);

    /**
     * Rend ce profil le seul actif de son annee scolaire : les autres profils
     * de la meme annee sont desactives dans la meme transaction. C'est ce
     * profil que le solveur retiendra quand la generation n'en precise aucun.
     */
    ConstraintProfileResponse activate(Long id);

    void deleteProfile(Long id);

    ConstraintSettingResponse addSetting(Long profileId, ConstraintSettingRequest request);

    ConstraintSettingResponse updateSetting(Long settingId, ConstraintSettingRequest request);

    void deleteSetting(Long settingId);

    List<ConstraintSettingResponse> findActiveSettings(Long profileId);
}
