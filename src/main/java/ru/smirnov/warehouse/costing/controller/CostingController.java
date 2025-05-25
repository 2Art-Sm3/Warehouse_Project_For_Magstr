package ru.smirnov.warehouse.costing.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.smirnov.warehouse.costing.dto.CostingViewDTO;
import ru.smirnov.warehouse.costing.service.CostingService;

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
            // Если товар не выбран, загружаем список товаров и пустой DTO или DTO для первого товара
            List<ru.smirnov.warehouse.costing.dto.CostingProductDTO> products = costingService.getUserProductsForCosting();
            if (!products.isEmpty()) {
                costingViewDTO = costingService.getCostingView(products.get(0).getId(), alternativeShipmentSelections);
            } else {
                costingViewDTO = new CostingViewDTO(); // Пустой DTO, если нет товаров
                costingViewDTO.setAllUserProducts(new ArrayList<>());
            }
        } else {
            costingViewDTO = costingService.getCostingView(productId, alternativeShipmentSelections);
        }

        model.addAttribute("costingView", costingViewDTO);
        model.addAttribute("selectedProductId", productId);
        model.addAttribute("altSelectionsString", altSelectionsString); // Передаем обратно для сохранения в URL

        return "costing"; // Имя HTML файла
    }

    // Parses a string like "componentId1:shipmentId1,componentId2:shipmentId2"
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
                        // Log error or handle malformed pair
                    }
                }
            }
        }
        return selections;
    }

    // Helper to build the altSelectionsString for URL redirects or AJAX updates
    // Not strictly needed in controller if JS handles it, but can be useful
    private String buildAlternativeSelectionsString(Map<Long, Long> selections) {
        if (selections == null || selections.isEmpty()) return "";
        return selections.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(","));
    }

    @PostMapping("/calculate-alternative")
    @ResponseBody // Чтобы вернуть JSON для AJAX
    public CostingViewDTO calculateAlternative(@RequestParam Long productId,
                                               @RequestBody Map<String, String> selectionsPayload) {
        // Преобразование Map<String, String> в Map<Long, Long> 
        Map<Long, Long> alternativeShipmentSelections = selectionsPayload.entrySet().stream()
            .collect(Collectors.toMap(
                entry -> Long.parseLong(entry.getKey()),
                entry -> Long.parseLong(entry.getValue()) 
            ));
        // Возвращаем только часть DTO, относящуюся к альтернативным расчетам, или весь DTO
        // Для простоты пока вернем весь, но можно оптимизировать
        return costingService.getCostingView(productId, alternativeShipmentSelections);
    }

    @PostMapping("/compare")
    @ResponseBody
    public Map<String, Object> compareScenarios(@RequestBody CostingComparisonRequestDTO comparisonRequest) {
        Map<String, Object> result = new HashMap<>();
        // Данные из запроса
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

        if (alternativeMarginPercentage < basicMarginPercentage) {
            // (ЦенаРеализации - СумПерПеременныхРасх) / ЦенаРеализации = ЦелеваяМаржинальностьПроцент / 100
            // ЦенаРеализации - СумПерПеременныхРасх = (ЦелеваяМаржинальностьПроцент / 100) * ЦенаРеализации
            // ЦенаРеализации * (1 - ЦелеваяМаржинальностьПроцент / 100) = СумПерПеременныхРасх
            // ЦенаРеализации = СумПерПеременныхРасх / (1 - ЦелеваяМаржинальностьПроцент / 100)
            if (basicMarginPercentage >= 100) { // Предотвращение деления на ноль или отрицательное число
                 result.put("recommendation", "Невозможно достичь базовой маржинальности (" + String.format("%.2f", basicMarginPercentage) + "%) с текущими переменными расходами в альтернативном сценарии.");
            } else {
                double requiredPrice = alternativeTotalVariableExpenses / (1 - (basicMarginPercentage / 100.0));
                result.put("recommendation", String.format(
                    "Для сохранения маржинальности в %.2f%%, цена реализации должна составлять %.2f руб.",
                    basicMarginPercentage,
                    round(requiredPrice)
                ));
            }
        } else if (alternativeMarginPercentage > basicMarginPercentage) {
            result.put("recommendation", String.format(
                "Ваша маржинальность выросла на %.2f%%.",
                round(alternativeMarginPercentage - basicMarginPercentage)
            ));
        } else {
            result.put("recommendation", "Маржинальность не изменилась.");
        }
        return result;
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    // DTO для запроса сравнения
    @lombok.Getter @lombok.Setter
    static class CostingComparisonRequestDTO {
        private double basicMargin;
        private double basicMarginPercentage;
        private double basicProductPrice;
        private double alternativeMargin;
        private double alternativeMarginPercentage;
        private double alternativeTotalVariableExpenses;
    }
} 