package ru.smirnov.warehouse.costing.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class CostingComponentInstanceDTO {
    private Long hierarchyNodeId; // The ID of the HierarchyNode this instance comes from
    private Long componentId; // ID of the underlying Component
    private String componentName;
    private Integer quantity; // Quantity from this specific HierarchyNode
    private Double unitCostFromHierarchy; // Unit cost from this specific HierarchyNode
    private Double totalCostFromHierarchy;

    // For alternative scenario
    private List<ShipmentPriceDTO> availableShipmentPrices;
    private Long selectedShipmentId; // Shipment ID selected for this instance in alternative scenario
    private Double unitCostAlternative; // Derived from selectedShipmentId or unitCostFromHierarchy
    private Double totalCostAlternative;

    @Data
    @Builder
    public static class ShipmentPriceDTO {
        private Long shipmentId;
        private Double price;
        private String displayText; // e.g., "10.50 руб. (от 2023-01-15)"
    }
} 