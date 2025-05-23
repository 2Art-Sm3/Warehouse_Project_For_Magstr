package ru.smirnov.warehouse.hierarchy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.smirnov.warehouse.hierarchy.entity.HierarchyLevel;
import ru.smirnov.warehouse.hierarchy.entity.HierarchyNode;

import java.util.List;

public interface HierarchyNodeRepository extends JpaRepository<HierarchyNode, Long> {

}