package ru.smirnov.warehouse.costing.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.smirnov.warehouse.costing.dto.CostingViewDTO;
import ru.smirnov.warehouse.costing.service.CostingService;
import ru.smirnov.warehouse.costing.dto.CostingProductDTO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Controller
@RequestMapping("/costing")
@RequiredArgsConstructor
public class CostingController {

    private final CostingService costingService;

    @GetMapping
    public String showCostingPage(@RequestParam(name = "productId", required = false) Long productId,
                                  @RequestParam(name = "altSelections", required = false) String altSelectionsString,
                                  Model model) {
        CostingViewDTO costingViewDTO;
        Map<Long, Long> alternativeShipmentSelections = parseAlternativeSelections(altSelectionsString);

        if (productId == null) {
            List<CostingProductDTO> products = costingService.getUserProductsForCosting();
            if (!products.isEmpty()) {
                productId = products.get(0).getId();
                costingViewDTO = costingService.getCostingView(productId, alternativeShipmentSelections);
            } else {
                costingViewDTO = CostingViewDTO.builder()
                                   .allUserProducts(new ArrayList<>())
                                   .componentInstances(new ArrayList<>())
                                   .build();
            }
        } else {
            costingViewDTO = costingService.getCostingView(productId, alternativeShipmentSelections);
        }
        
        String updatedAltSelectionsString = buildAlternativeSelectionsString(costingViewDTO.getComponentInstances());

        model.addAttribute("costingView", costingViewDTO);
        model.addAttribute("selectedProductId", productId);
        model.addAttribute("altSelectionsString", updatedAltSelectionsString);

        return "costing";
    }

    private Map<Long, Long> parseAlternativeSelections(String altSelectionsString) {
        Map<Long, Long> selections = new HashMap<>();
        if (altSelectionsString != null && !altSelectionsString.isEmpty()) {
            String[] pairs = altSelectionsString.split(",");
            for (String pair : pairs) {
                String[] keyValue = pair.split(":");
                if (keyValue.length == 2) {
                    try {
                        selections.put(Long.parseLong(keyValue[0]), Long.parseLong(keyValue[1]));
                    } catch (NumberFormatException e) {
                        System.err.println("Malformed selection pair: " + pair + ". Error: " + e.getMessage());
                    }
                }
            }
        }
        return selections;
    }

    private String buildAlternativeSelectionsString(List<ru.smirnov.warehouse.costing.dto.CostingComponentInstanceDTO> instances) {
        if (instances == null || instances.isEmpty()) return "";
        return instances.stream()
                .filter(instance -> instance.getSelectedShipmentId() != null)
                .map(instance -> instance.getHierarchyNodeId() + ":" + instance.getSelectedShipmentId())
                .collect(Collectors.joining(","));
    }

    @PostMapping("/calculate-alternative")
    @ResponseBody
    public CostingViewDTO calculateAlternative(@RequestParam Long productId,
                                               @RequestBody Map<String, String> selectionsPayload) {
        Map<Long, Long> alternativeShipmentSelections = selectionsPayload.entrySet().stream()
            .collect(Collectors.toMap(
                entry -> Long.parseLong(entry.getKey()),
                entry -> Long.parseLong(entry.getValue())
            ));
        return costingService.getCostingView(productId, alternativeShipmentSelections);
    }

    @PostMapping("/compare")
    @ResponseBody
    public Map<String, Object> compareScenarios(@RequestBody CostingComparisonRequestDTO comparisonRequest) {
        Map<String, Object> result = new HashMap<>();
        double basicMargin = comparisonRequest.getBasicMargin();
        double basicMarginPercentage = comparisonRequest.getBasicMarginPercentage();
        double basicProductPrice = comparisonRequest.getBasicProductPrice();
        double alternativeMargin = comparisonRequest.getAlternativeMargin();
        double alternativeMarginPercentage = comparisonRequest.getAlternativeMarginPercentage();
        double alternativeTotalVariableExpenses = comparisonRequest.getAlternativeTotalVariableExpenses();

        result.put("basicMargin", basicMargin);
        result.put("basicMarginPercentage", basicMarginPercentage);
        result.put("alternativeMargin", alternativeMargin);
        result.put("alternativeMarginPercentage", alternativeMarginPercentage);

        String recommendation;
        if (alternativeMarginPercentage < basicMarginPercentage) {
            if (basicMarginPercentage >= 100 || (1 - (basicMarginPercentage / 100.0)) <= 0) {
                 recommendation = "Невозможно достичь базовой маржинальности (" + String.format("%.2f", basicMarginPercentage) + "%) с текущими переменными расходами в альтернативном сценарии.";
            } else {
                double requiredPrice = alternativeTotalVariableExpenses / (1 - (basicMarginPercentage / 100.0));
                recommendation = String.format(
                    "Для сохранения маржинальности в %.2f%%, цена реализации должна составлять %.2f руб.",
                    basicMarginPercentage,
                    round(requiredPrice)
                );
            }
        } else if (alternativeMarginPercentage > basicMarginPercentage) {
            recommendation = String.format(
                "Ваша маржинальность выросла на %.2f%%.",
                round(alternativeMarginPercentage - basicMarginPercentage)
            );
        } else {
            recommendation = "Маржинальность не изменилась.";
        }
        result.put("recommendation", recommendation);
        return result;
    }

    private double round(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    @lombok.Data
    static class CostingComparisonRequestDTO {
        private double basicMargin;
        private double basicMarginPercentage;
        private double basicProductPrice;
        private double alternativeMargin;
        private double alternativeMarginPercentage;
        private double alternativeTotalVariableExpenses;
    }
} 