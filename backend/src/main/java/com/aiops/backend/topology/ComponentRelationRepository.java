package com.aiops.backend.topology;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComponentRelationRepository extends JpaRepository<ComponentRelation, Long> {

    @EntityGraph(attributePaths = {"source", "target"})
    List<ComponentRelation> findAllByOrderByCreatedAtAsc();

    boolean existsBySource_IdAndTarget_IdAndRelationType(
            Long sourceId,
            Long targetId,
            ComponentRelationType relationType
    );

    void deleteBySource_IdOrTarget_Id(Long sourceId, Long targetId);
}
