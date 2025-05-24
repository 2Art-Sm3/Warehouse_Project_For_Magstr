package ru.smirnov.warehouse.hierarchy.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import ru.smirnov.warehouse.hierarchy.entity.HierarchyNode;

@Getter
@Setter
@AllArgsConstructor
public class HierarchyViewEntry {
    private HierarchyNode childNode;
    private int depth; // 0-indexed depth
    private boolean isLastChildOfParent;
    private Long parentNodeId; // Can be null if it's a root-level child
    private Integer originalLevel; // The 1-indexed level from HierarchyLevel
} 