package ru.smirnov.warehouse.costing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.smirnov.warehouse.common.entity.User;
import ru.smirnov.warehouse.common.service.UserService;
import ru.smirnov.warehouse.costing.dto.CostingComponentInstanceDTO;
import ru.smirnov.warehouse.costing.dto.CostingProductDTO;
import ru.smirnov.warehouse.costing.dto.CostingViewDTO;
import ru.smirnov.warehouse.hierarchy.entity.HierarchyNode;
import ru.smirnov.warehouse.hierarchy.service.HierarchyService;
import ru.smirnov.warehouse.hierarchy.dto.HierarchyViewEntry;
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
    private final HierarchyService hierarchyService;
    private final ComponentRepository componentRepository;
    private final ShipmentRepository shipmentRepository;
    private final UserRepository userRepository;

    private static final DateTimeFormatter SHIPMENT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.00");

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

    public CostingViewDTO getCostingView(Long productId, Map<Long, Long> alternativeShipmentSelectionsByNodeId) {
        User currentUser = getCurrentUser();
        Product product = productRepository.findById(productId)
                .filter(p -> p.getUser().getId().equals(currentUser.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Product not found or access denied: " + productId));

        CostingViewDTO.CostingViewDTOBuilder viewDTOBuilder = CostingViewDTO.builder()
                .selectedProductId(product.getId())
                .selectedProductName(product.getName())
                .productPrice(product.getPrice() != null ? product.getPrice() : 0.0);

        // Ozon Expenses
        double ozonReward = product.getOzonReward() != null ? product.getOzonReward() : 0.0;
        double logisticsFee = product.getLogisticsFee() != null ? product.getLogisticsFee() : 0.0;
        double lastMileFee = product.getLastMileFee() != null ? product.getLastMileFee() : 0.0;
        double acquiringFee = product.getAcquiringFee() != null ? product.getAcquiringFee() : 0.0;
        double otherFees = product.getOtherFees() != null ? product.getOtherFees() : 0.0;
        double totalOzonExpenses = ozonReward + logisticsFee + lastMileFee + acquiringFee + otherFees;

        viewDTOBuilder
            .ozonReward(ozonReward)
            .logisticsFee(logisticsFee)
            .lastMileFee(lastMileFee)
            .acquiringFee(acquiringFee)
            .otherFees(otherFees)
            .totalOzonExpenses(round(totalOzonExpenses));

        List<HierarchyViewEntry> hierarchyEntries = hierarchyService.getHierarchyView(product.getId());
        List<CostingComponentInstanceDTO> componentInstances = new ArrayList<>();
        if (alternativeShipmentSelectionsByNodeId == null) alternativeShipmentSelectionsByNodeId = new HashMap<>();

        for (HierarchyViewEntry viewEntry : hierarchyEntries) {
            HierarchyNode node = viewEntry.getChildNode();
            if (node.getIsNode() || node.getComponent() == null) { // Skip nodes/sub-assemblies and nodes without components
                continue;
            }

            Component originalComponent = node.getComponent(); // Already fetched by HierarchyService's DTO building

            double unitCostFromHierarchy = node.getUnitCost() != null ? node.getUnitCost() : 0.0;
            int quantityInProduct = node.getQuantity(); // This is the quantity of this specific node
            double totalCostFromHierarchy = unitCostFromHierarchy * quantityInProduct;

            List<Shipment> shipments = shipmentRepository.findByComponentId(originalComponent.getId());
            List<CostingComponentInstanceDTO.ShipmentPriceDTO> shipmentPrices = shipments.stream()
                .map(s -> CostingComponentInstanceDTO.ShipmentPriceDTO.builder()
                        .shipmentId(s.getId())
                        .price(s.getPurchasePrice())
                        .displayText(String.format("%s руб. (от %s)",
                                     DECIMAL_FORMAT.format(s.getPurchasePrice()),
                                     s.getPurchaseDate().format(SHIPMENT_DATE_FORMATTER)))
                        .build())
                .collect(Collectors.toList());

            Long initialSelectedShipmentId = alternativeShipmentSelectionsByNodeId.get(node.getId());
            Long finalSelectedShipmentId = null;
            double determinedAltUnitCost = unitCostFromHierarchy; // Default to basic cost

            Shipment chosenShipment = null;

            if (initialSelectedShipmentId != null) {
                // Try to find the shipment that was explicitly selected
                chosenShipment = shipments.stream()
                    .filter(s -> s.getId().equals(initialSelectedShipmentId))
                    .findFirst()
                    .orElse(null);
            }

            if (chosenShipment != null) {
                // Valid explicit selection found
                finalSelectedShipmentId = chosenShipment.getId();
                determinedAltUnitCost = chosenShipment.getPurchasePrice();
            } else {
                // No valid explicit selection (either not provided or invalid ID)
                // Default to the first available shipment, if any
                if (!shipments.isEmpty()) {
                    chosenShipment = shipments.get(0);
                    finalSelectedShipmentId = chosenShipment.getId();
                    determinedAltUnitCost = chosenShipment.getPurchasePrice();
                    // If there was no initial selection, update the map with the default we picked.
                    // If there *was* an initial (but invalid) selection, the controller will get the new finalSelectedShipmentId
                    // and can update its altSelectionsString for the next request if needed.
                    if (initialSelectedShipmentId == null) {
                         alternativeShipmentSelectionsByNodeId.put(node.getId(), finalSelectedShipmentId);
                    }
                } else {
                    // No shipments available, so finalSelectedShipmentId remains null
                    // and determinedAltUnitCost remains unitCostFromHierarchy
                }
            }
            
            // If, after all logic, an invalid initialSelectedShipmentId caused chosenShipment to be null 
            // but shipments were available, we might have defaulted. Ensure the map is updated if an invalid ID was initially passed.
            if (initialSelectedShipmentId != null && finalSelectedShipmentId != null && !initialSelectedShipmentId.equals(finalSelectedShipmentId)) {
                alternativeShipmentSelectionsByNodeId.put(node.getId(), finalSelectedShipmentId);
            }

            double totalCostAlternative = determinedAltUnitCost * quantityInProduct;

            componentInstances.add(CostingComponentInstanceDTO.builder()
                    .hierarchyNodeId(node.getId())
                    .componentId(originalComponent.getId())
                    .componentName(originalComponent.getName())
                    .quantity(quantityInProduct)
                    .unitCostFromHierarchy(round(unitCostFromHierarchy))
                    .totalCostFromHierarchy(round(totalCostFromHierarchy))
                    .availableShipmentPrices(shipmentPrices)
                    .selectedShipmentId(finalSelectedShipmentId) // Use the final determined ID
                    .unitCostAlternative(round(determinedAltUnitCost))
                    .totalCostAlternative(round(totalCostAlternative))
                    .build());
        }

        viewDTOBuilder.componentInstances(componentInstances);

        // Calculate totals for Basic Scenario
        double totalBasicComponentsCost = componentInstances.stream()
                .mapToDouble(CostingComponentInstanceDTO::getTotalCostFromHierarchy)
                .sum();
        viewDTOBuilder.totalBasicComponentsCost(round(totalBasicComponentsCost));
        double basicTotalVariableExpenses = totalBasicComponentsCost + totalOzonExpenses;
        viewDTOBuilder.basicTotalVariableExpenses(round(basicTotalVariableExpenses));
        double basicMargin = viewDTOBuilder.build().getProductPrice() - basicTotalVariableExpenses;
        viewDTOBuilder.basicMargin(round(basicMargin));
        viewDTOBuilder.basicMarginPercentage(viewDTOBuilder.build().getProductPrice() > 0 ? round((basicMargin / viewDTOBuilder.build().getProductPrice()) * 100) : 0.0);

        // Calculate totals for Alternative Scenario
        double totalAlternativeComponentsCost = componentInstances.stream()
                .mapToDouble(CostingComponentInstanceDTO::getTotalCostAlternative)
                .sum();
        viewDTOBuilder.totalAlternativeComponentsCost(round(totalAlternativeComponentsCost));
        double altTotalVariableExpenses = totalAlternativeComponentsCost + totalOzonExpenses;
        viewDTOBuilder.alternativeTotalVariableExpenses(round(altTotalVariableExpenses));
        double altMargin = viewDTOBuilder.build().getProductPrice() - altTotalVariableExpenses;
        viewDTOBuilder.alternativeMargin(round(altMargin));
        viewDTOBuilder.alternativeMarginPercentage(viewDTOBuilder.build().getProductPrice() > 0 ? round((altMargin / viewDTOBuilder.build().getProductPrice()) * 100) : 0.0);

        viewDTOBuilder.allUserProducts(getUserProductsForCosting());
        return viewDTOBuilder.build();
    }

    private double round(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0; // Handle cases like division by zero leading to NaN/Infinity
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
} 
