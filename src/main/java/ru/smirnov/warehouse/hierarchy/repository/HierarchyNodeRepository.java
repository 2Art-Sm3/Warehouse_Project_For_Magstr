package ru.smirnov.warehouse.hierarchy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.smirnov.warehouse.hierarchy.entity.HierarchyNode;

public interface HierarchyNodeRepository extends JpaRepository<HierarchyNode, Long> {
}