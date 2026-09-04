package tn.wtm.school.org.service;


import org.springframework.boot.autoconfigure.data.web.SpringDataWebProperties;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import tn.wtm.school.org.dto.request.BulkCreateClassGroupRequest;
import tn.wtm.school.org.dto.request.ClassGroupRequest;
import tn.wtm.school.org.dto.request.LevelRequest;
import tn.wtm.school.org.dto.response.BulkCreateClassGroupResult;
import tn.wtm.school.org.dto.response.ClassGroupResponse;
import tn.wtm.school.org.dto.response.LevelResponse;

import java.util.List;

public interface LevelService {


    // ----- Level //-----


    LevelResponse createLevel(LevelRequest levelRequest);
    LevelResponse getLevelById(Long id);
    Page<LevelResponse> getAllLevels(Pageable pageable);
    List<LevelResponse> getActivesLevels();
    LevelResponse updateLevel(LevelRequest  levelRequest , Long id);
    /** Activer / désactiver un niveau */
    LevelResponse toggleLevelStatus(Long id , Boolean estActif);
    void deleteLevel(Long id);
    Boolean existsByCode(String code);
    /** Récupérer un niveau avec ses classes chargées */
    LevelResponse getLevelWithClasses(Long id);
    /** Récupérer un niveau avec ses matières chargées */
    LevelResponse getLevelWithSubjects(Long id);


    // ----------Class Group -----------

    ClassGroupResponse createClass(ClassGroupRequest classGroupRequest);
    ClassGroupResponse getClassGroupById(Long id);
    /** Lister toutes les classes du tenant courant */
    List<ClassGroupResponse> getAllClassGroups();
    /** Lister les classes d'un niveau */
    List<ClassGroupResponse> getClassGroupsByLevel(Long levelId);
    /** Lister les Classes d'une annéé Scolaire */
    List<ClassGroupResponse> getClassGroupsBySchoolYear(Long schoolYearId);
    /** Lister les classes d'un niveau pour une annéé scolaire */
    List<ClassGroupResponse> getClassGroupsByLevelAndYear(Long levelId, Long schoolYearId);
    /** Lister les classes Paginée par annéé */
    Page<ClassGroupResponse> getClassGroupsByYearPaged(Long schoolId, Pageable pageable);
    /** Modifier une classe */
    ClassGroupResponse updateClassGroup(Long id,ClassGroupRequest request);
    /** Activer Desactiver un classe */
    ClassGroupResponse toggleClassGroupStatus(Long id, Boolean estActif);
    void deleteClassGroup(Long id);
    /** Recuperer une classe avec ses affectations chargées */
    ClassGroupResponse getClassGroupWithAssignments(Long id);
    /** Verifier uncité du code dans niveau + annéé */
    Boolean existsByCodeInLevelAndYear(String code, Long levelId, Long schoolYearId);

    /** Créer des classes en masse depuis un descriptif par niveau */
    BulkCreateClassGroupResult bulkCreateClassGroups(BulkCreateClassGroupRequest request);




    















}
