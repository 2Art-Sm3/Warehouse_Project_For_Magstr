package ru.smirnov.warehouse.costing.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.smirnov.warehouse.product.entity.Product;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CostingViewDTO {
    // Product information
    private Long selectedProductId;
    private String selectedProductName;
    private Double productPrice; // Цена реализации

    // Ozon expenses
    private Double ozonReward;
    private Double logisticsFee;
    private Double lastMileFee;
    private Double acquiringFee;
    private Double otherFees;
    private Double totalOzonExpenses;

    // Components for basic calculation
    private List<CostingComponentDTO> basicComponents;
    private Double totalBasicComponentsCost;

    // Basic calculation results
    private Double basicTotalVariableExpenses;
    private Double basicMargin;
    private Double basicMarginPercentage;

    // Components for alternative calculation
    private List<CostingComponentDTO> alternativeComponents;
    private Double totalAlternativeComponentsCost;

    // Alternative calculation results (can be null if not calculated yet)
    private Double alternativeTotalVariableExpenses;
    private Double alternativeMargin;
    private Double alternativeMarginPercentage;

    // List of all products for the dropdown
    private List<CostingProductDTO> allUserProducts;

} 