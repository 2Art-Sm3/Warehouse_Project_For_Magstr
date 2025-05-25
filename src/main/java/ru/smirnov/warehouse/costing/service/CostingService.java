package ru.smirnov.warehouse.costing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.smirnov.warehouse.common.entity.User;
import ru.smirnov.warehouse.common.service.UserService;
import ru.smirnov.warehouse.costing.dto.CostingComponentDTO;
import ru.smirnov.warehouse.costing.dto.CostingProductDTO;
import ru.smirnov.warehouse.costing.dto.CostingViewDTO;
import ru.smirnov.warehouse.hierarchy.entity.HierarchyLevel;
import ru.smirnov.warehouse.hierarchy.entity.HierarchyNode;
import ru.smirnov.warehouse.hierarchy.repository.HierarchyLevelRepository;
import ru.smirnov.warehouse.inventory.entity.Component;
import ru.smirnov.warehouse.inventory.entity.Shipment;
import ru.smirnov.warehouse.inventory.repository.ComponentRepository;
import ru.smirnov.warehouse.inventory.repository.ShipmentRepository;
import ru.smirnov.warehouse.product.entity.Product;
import ru.smirnov.warehouse.product.repository.ProductRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.smirnov.warehouse.common.repository.UserRepository;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CostingService {

    private final ProductRepository productRepository;
    private final HierarchyLevelRepository hierarchyLevelRepository;
    private final ComponentRepository componentRepository; // Предполагая, что он есть
    private final ShipmentRepository shipmentRepository;   // Предполагая, что он есть
    private final UserRepository userRepository;

    private static final DateTimeFormatter SHIPMENT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##");

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Current user not found: " + username));
    }

    public List<CostingProductDTO> getUserProductsForCosting() {
        User currentUser = getCurrentUser();
        return productRepository.findByUser(currentUser).stream()
                .map(product -> new CostingProductDTO(product.getId(), product.getName()))
                .collect(Collectors.toList());
    }

    public CostingViewDTO getCostingView(Long productId, Map<Long, Long> alternativeShipmentSelections) {
        User currentUser = getCurrentUser();
        Product product = productRepository.findById(productId)
                .filter(p -> p.getUser().getId().equals(currentUser.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Product not found or access denied: " + productId));

        CostingViewDTO viewDTO = new CostingViewDTO();
        viewDTO.setSelectedProductId(product.getId());
        viewDTO.setSelectedProductName(product.getName());
        viewDTO.setProductPrice(product.getPrice() != null ? product.getPrice() : 0.0);

        // Ozon Expenses
        viewDTO.setOzonReward(product.getOzonReward() != null ? product.getOzonReward() : 0.0);
        viewDTO.setLogisticsFee(product.getLogisticsFee() != null ? product.getLogisticsFee() : 0.0);
        viewDTO.setLastMileFee(product.getLastMileFee() != null ? product.getLastMileFee() : 0.0);
        viewDTO.setAcquiringFee(product.getAcquiringFee() != null ? product.getAcquiringFee() : 0.0);
        viewDTO.setOtherFees(product.getOtherFees() != null ? product.getOtherFees() : 0.0);
        double totalOzonExpenses = viewDTO.getOzonReward() + viewDTO.getLogisticsFee() +
                                   viewDTO.getLastMileFee() + viewDTO.getAcquiringFee() + viewDTO.getOtherFees();
        viewDTO.setTotalOzonExpenses(round(totalOzonExpenses));

        // Flattened components list (Map<Component Original ID, Total Quantity>)
        Map<Long, Integer> componentQuantitiesInProduct = new HashMap<>();
        List<HierarchyLevel> productHierarchy = hierarchyLevelRepository.findByProductId(product.getId());
        collectComponentQuantitiesRecursive(productHierarchy, null, 1, componentQuantitiesInProduct);

        // Basic Components Calculation
        List<CostingComponentDTO> basicComponents = new ArrayList<>();
        double totalBasicComponentsCost = 0;
        for (Map.Entry<Long, Integer> entry : componentQuantitiesInProduct.entrySet()) {
            Long componentId = entry.getKey();
            Integer totalQuantity = entry.getValue();
            HierarchyNode nodeDetails = findHierarchyNodeForComponent(productHierarchy, componentId);
            Component originalComponent = componentRepository.findById(componentId).orElse(null);

            if (nodeDetails != null && originalComponent != null) {
                double unitCost = nodeDetails.getUnitCost() != null ? nodeDetails.getUnitCost() : 0.0;
                double totalCostInProduct = unitCost * totalQuantity;
                basicComponents.add(new CostingComponentDTO(
                        componentId,
                        originalComponent.getName(),
                        round(unitCost),
                        totalQuantity,
                        round(totalCostInProduct),
                        null, // No shipment prices for basic
                        null  // No selected shipment for basic
                ));
                totalBasicComponentsCost += totalCostInProduct;
            }
        }
        viewDTO.setBasicComponents(basicComponents);
        viewDTO.setTotalBasicComponentsCost(round(totalBasicComponentsCost));

        // Basic Calculation Results
        double basicTotalVariableExpenses = totalBasicComponentsCost + totalOzonExpenses;
        viewDTO.setBasicTotalVariableExpenses(round(basicTotalVariableExpenses));
        double basicMargin = viewDTO.getProductPrice() - basicTotalVariableExpenses;
        viewDTO.setBasicMargin(round(basicMargin));
        viewDTO.setBasicMarginPercentage(viewDTO.getProductPrice() > 0 ? round((basicMargin / viewDTO.getProductPrice()) * 100) : 0.0);

        // Alternative Components Calculation
        List<CostingComponentDTO> alternativeComponents = new ArrayList<>();
        double totalAlternativeComponentsCost = 0;
        if (alternativeShipmentSelections == null) alternativeShipmentSelections = new HashMap<>();

        for (Map.Entry<Long, Integer> entry : componentQuantitiesInProduct.entrySet()) {
            Long componentId = entry.getKey();
            Integer totalQuantity = entry.getValue();
            Component originalComponent = componentRepository.findById(componentId).orElse(null);
            if (originalComponent == null) continue;

            List<Shipment> shipments = shipmentRepository.findByComponentId(componentId);
            List<CostingComponentDTO.ShipmentPriceDTO> shipmentPrices = shipments.stream()
                .map(s -> new CostingComponentDTO.ShipmentPriceDTO(
                        s.getId(),
                        s.getPurchasePrice(),
                        String.format("%s руб. (от %s)", 
                                      DECIMAL_FORMAT.format(s.getPurchasePrice()), 
                                      s.getPurchaseDate().format(SHIPMENT_DATE_FORMATTER))))
                .collect(Collectors.toList());

            Long selectedShipmentId = alternativeShipmentSelections.getOrDefault(componentId, 
                shipments.isEmpty() ? null : shipments.get(0).getId() // Default to first shipment if any
            );
            
            double altUnitCost = 0.0;
            final Long currentSelectedShipmentId = selectedShipmentId; // Create a final variable

            if (currentSelectedShipmentId != null) {
                altUnitCost = shipments.stream()
                                .filter(s -> s.getId().equals(currentSelectedShipmentId)) // Use the final variable
                                .findFirst()
                                .map(Shipment::getPurchasePrice)
                                .orElse(0.0);
            } else if (!shipments.isEmpty()) {
                 // If no selection, and shipments exist, but somehow selectedShipmentId became null
                 // (e.g. previous selection deleted), default to first shipment's price
                 altUnitCost = shipments.get(0).getPurchasePrice();
                 selectedShipmentId = shipments.get(0).getId(); // also update selectedShipmentId to reflect this default
            } else {
                 // If no shipments at all, get basic unit cost from HierarchyNode for this component
                 HierarchyNode nodeDetails = findHierarchyNodeForComponent(productHierarchy, componentId);
                 if(nodeDetails != null) {
                    altUnitCost = nodeDetails.getUnitCost() != null ? nodeDetails.getUnitCost() : 0.0;
                 } // else altUnitCost remains 0.0
            }

            double totalAltCostInProduct = altUnitCost * totalQuantity;
            alternativeComponents.add(new CostingComponentDTO(
                    componentId,
                    originalComponent.getName(),
                    round(altUnitCost),
                    totalQuantity,
                    round(totalAltCostInProduct),
                    shipmentPrices,
                    selectedShipmentId
            ));
            totalAlternativeComponentsCost += totalAltCostInProduct;
        }
        viewDTO.setAlternativeComponents(alternativeComponents);
        viewDTO.setTotalAlternativeComponentsCost(round(totalAlternativeComponentsCost));

        // Alternative Calculation Results
        double altTotalVariableExpenses = totalAlternativeComponentsCost + totalOzonExpenses;
        viewDTO.setAlternativeTotalVariableExpenses(round(altTotalVariableExpenses));
        double altMargin = viewDTO.getProductPrice() - altTotalVariableExpenses;
        viewDTO.setAlternativeMargin(round(altMargin));
        viewDTO.setAlternativeMarginPercentage(viewDTO.getProductPrice() > 0 ? round((altMargin / viewDTO.getProductPrice()) * 100) : 0.0);
        
        viewDTO.setAllUserProducts(getUserProductsForCosting());
        return viewDTO;
    }

    private void collectComponentQuantitiesRecursive(
        List<HierarchyLevel> allLevelsForProduct,
        Long parentNodeId, // null for root nodes of the product
        int parentMultiplier, // How many of the parent are in the product (starts at 1 for root)
        Map<Long, Integer> componentQuantities // Map<Component Original ID, Total Quantity>
    ) {
        List<HierarchyLevel> currentChildrenLevels = allLevelsForProduct.stream()
            .filter(hl -> {
                if (parentNodeId == null) return hl.getParentNode() == null;
                return hl.getParentNode() != null && hl.getParentNode().getId().equals(parentNodeId);
            })
            .collect(Collectors.toList());

        for (HierarchyLevel level : currentChildrenLevels) {
            HierarchyNode childNode = level.getChildNode();
            int currentTotalQuantity = parentMultiplier * childNode.getQuantity();

            if (childNode.getIsNode()) { // If it's a sub-assembly (node)
                collectComponentQuantitiesRecursive(allLevelsForProduct, childNode.getId(), currentTotalQuantity, componentQuantities);
            } else { // If it's an actual component
                if (childNode.getComponent() != null) {
                    Long originalComponentId = childNode.getComponent().getId();
                    componentQuantities.merge(originalComponentId, currentTotalQuantity, Integer::sum);
                }
            }
        }
    }
    
    private HierarchyNode findHierarchyNodeForComponent(List<HierarchyLevel> productHierarchy, Long originalComponentId) {
        return productHierarchy.stream()
            .map(HierarchyLevel::getChildNode)
            .filter(node -> !node.getIsNode() && node.getComponent() != null && node.getComponent().getId().equals(originalComponentId))
            .findFirst() // This might not be entirely correct if a component appears multiple times with different node settings
            .orElse(null); // Or, ideally, fetch the one that's part of *this* product's direct hierarchy if possible
                           // For now, this assumes unitCost in HierarchyNode is consistent for a component within a product's hierarchy
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
} 