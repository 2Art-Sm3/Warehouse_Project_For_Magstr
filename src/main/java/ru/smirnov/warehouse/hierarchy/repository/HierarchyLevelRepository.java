package ru.smirnov.warehouse.hierarchy.repository;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ru.smirnov.warehouse.hierarchy.entity.HierarchyLevel;

import java.util.List;

public interface HierarchyLevelRepository extends JpaRepository<HierarchyLevel, Long> {
    List<HierarchyLevel> findByProductId(Long productId);
    List<HierarchyLevel> findByParentNodeId(Long parentNodeId);

    @Query("SELECT hl FROM HierarchyLevel hl WHERE hl.childNode.id = :childNodeId")
    List<HierarchyLevel> findByChildNodeId(Long childNodeId);

    @Transactional
    @Modifying
    @Query("DELETE FROM HierarchyLevel hl WHERE hl.childNode.id = :childNodeId")
    void deleteByChildNodeId(Long childNodeId);
}