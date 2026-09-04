package tn.wtm.school.planning.constraints.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.wtm.school.planning.constraints.entity.ConstraintDefinition;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConstraintDefinitionRepository extends JpaRepository<ConstraintDefinition, Long> {

    List<ConstraintDefinition> findAllByOrderByCategoryAscCodeAsc();

    Optional<ConstraintDefinition> findByCode(String code);

    boolean existsByCode(String code);
}
