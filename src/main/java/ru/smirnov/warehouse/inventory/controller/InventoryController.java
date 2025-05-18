package ru.smirnov.warehouse.inventory.controller;

import jakarta.transaction.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.smirnov.warehouse.inventory.entity.Component;
import ru.smirnov.warehouse.inventory.entity.Shipment;
import ru.smirnov.warehouse.inventory.service.WarehouseService;
import ru.smirnov.warehouse.product.repository.ProductRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/inventory")
public class InventoryController {

    private final WarehouseService warehouseService;
    private final ProductRepository productRepository;

    public InventoryController(WarehouseService warehouseService, ProductRepository productRepository) {
        this.warehouseService = warehouseService;
        this.productRepository = productRepository;
    }

    @GetMapping
    public String showInventory(Model model, Authentication authentication) {
        String currentUsername = authentication.getName();
        model.addAttribute("products", productRepository.findByUserUsername(currentUsername));
        model.addAttribute("warehouseService", warehouseService);
        return "inventory";
    }

    @PostMapping("/create-component")
    @ResponseBody
    public Map<String, Boolean> createComponent(@RequestParam String componentName, @RequestParam Long productId) {
        warehouseService.createComponent(componentName, productId);
        Map<String, Boolean> response = new HashMap<>();
        response.put("success", true);
        return response;
    }

    @PostMapping("/create-shipment")
    @ResponseBody
    public Map<String, Boolean> createShipment(@RequestParam Long componentId, @RequestParam Double purchasePrice,
                                               @RequestParam Integer quantity, @RequestParam String purchaseDate) {
        LocalDateTime date = LocalDateTime.parse(purchaseDate + "T00:00:00");
        warehouseService.createShipment(componentId, purchasePrice, quantity, date);
        Map<String, Boolean> response = new HashMap<>();
        response.put("success", true);
        return response;
    }

    @PostMapping("/edit-component")
    @ResponseBody
    public Map<String, Boolean> editComponent(@RequestParam Long componentId, @RequestParam String componentName) {
        warehouseService.editComponent(componentId, componentName);
        Map<String, Boolean> response = new HashMap<>();
        response.put("success", true);
        return response;
    }

    @PostMapping("/edit-shipment")
    @ResponseBody
    public Map<String, Boolean> editShipment(@RequestParam Long shipmentId, @RequestParam Double purchasePrice,
                                             @RequestParam Integer quantity, @RequestParam String purchaseDate) {
        LocalDateTime date = LocalDateTime.parse(purchaseDate + "T00:00:00");
        warehouseService.editShipment(shipmentId, purchasePrice, quantity, date);
        Map<String, Boolean> response = new HashMap<>();
        response.put("success", true);
        return response;
    }

    @DeleteMapping("/delete-component/{componentId}")
    @ResponseBody
    @Transactional
    public ResponseEntity<Map<String, Boolean>> deleteComponent(@PathVariable Long componentId, @RequestParam Long productId) {
        warehouseService.deleteComponent(componentId);
        Map<String, Boolean> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/delete-shipment/{shipmentId}")
    @ResponseBody
    @Transactional
    public ResponseEntity<Map<String, Boolean>> deleteShipment(@PathVariable Long shipmentId, @RequestParam Long componentId) {
        warehouseService.deleteShipment(shipmentId);
        Map<String, Boolean> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/component-quantity/{componentId}")
    @ResponseBody
    public Map<String, Integer> getComponentQuantity(@PathVariable Long componentId) {
        Component component = warehouseService.getComponentById(componentId);
        int quantity = warehouseService.calculateComponentQuantity(component);
        Map<String, Integer> response = new HashMap<>();
        response.put("quantity", quantity);
        return response;
    }

    @GetMapping("/shipments/{componentId}")
    public String getShipmentsFragment(@PathVariable Long componentId, @RequestParam Long productId, Model model) {
        Component component = warehouseService.getComponentById(componentId);
        List<Shipment> shipments = warehouseService.getShipmentsByComponent(component);
        model.addAttribute("shipments", shipments);
        model.addAttribute("component", component);
        model.addAttribute("product", productRepository.findById(productId).orElseThrow());
        return "fragments/shipments :: shipmentTable";
    }
}
