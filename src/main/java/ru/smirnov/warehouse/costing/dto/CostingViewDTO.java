package ru.smirnov.warehouse.costing.dto;

import lombok.Builder;
import lombok.Data;
import ru.smirnov.warehouse.product.entity.Product;

import java.util.List;

@Data
@Builder
public class CostingViewDTO {
    // Fields for product selection dropdown
    private List<CostingProductDTO> allUserProducts;
    private Long selectedProductId;
    private String selectedProductName;

    // Product specific data (common for both scenarios)
    private Double productPrice;

    // Ozon expenses (common for both scenarios)
    private Double ozonReward;
    private Double logisticsFee;
    private Double lastMileFee;
    private Double acquiringFee;
    private Double otherFees;
    private Double totalOzonExpenses;

    // List of component instances from the hierarchy
    private List<CostingComponentInstanceDTO> componentInstances;

    // Calculated values for Basic Scenario (derived from componentInstances)
    private Double totalBasicComponentsCost; // Sum of (instance.unitCostFromHierarchy * instance.quantity)
    private Double basicTotalVariableExpenses; // totalBasicComponentsCost + totalOzonExpenses
    private Double basicMargin;                // productPrice - basicTotalVariableExpenses
    private Double basicMarginPercentage;      // (basicMargin / productPrice) * 100

    // Calculated values for Alternative Scenario (derived from componentInstances)
    private Double totalAlternativeComponentsCost; // Sum of (instance.unitCostAlternative * instance.quantity)
    private Double alternativeTotalVariableExpenses; // totalAlternativeComponentsCost + totalOzonExpenses
    private Double alternativeMargin;                // productPrice - alternativeTotalVariableExpenses
    private Double alternativeMarginPercentage;      // (alternativeMargin / productPrice) * 100

    // To carry selections between requests
    private String altSelectionsString; // e.g., "nodeId1:shipId1,nodeId2:shipId2"
} 