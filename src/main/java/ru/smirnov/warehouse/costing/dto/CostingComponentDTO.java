package ru.smirnov.warehouse.costing.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CostingComponentDTO {
    private Long componentId; // ID изначального компонента (из Warehouse)
    private String name;
    private double unitCost; // Себестоимость за единицу (выбранная)
    private int quantityInProduct; // Итоговое количество этого компонента в конечном изделии
    private double totalCostInProduct; // unitCost * quantityInProduct
    private List<ShipmentPriceDTO> availableShipmentPrices; // Для альтернативного сценария
    private Long selectedShipmentId; // ID выбранной поставки для альтернативного сценария

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ShipmentPriceDTO {
        private Long shipmentId;
        private double price;
        private String displayText; // Например, "100.50 руб. (от 2023-01-01)"
    }
} 