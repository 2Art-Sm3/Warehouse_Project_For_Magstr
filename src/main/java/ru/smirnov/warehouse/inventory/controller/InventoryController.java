package ru.smirnov.warehouse.inventory.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.smirnov.warehouse.inventory.service.WarehouseService;
import ru.smirnov.warehouse.product.repository.ProductRepository;

import java.time.LocalDateTime;

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
    public String createComponent(@RequestParam String componentName, @RequestParam Long productId) {
        warehouseService.createComponent(componentName, productId);
        return "redirect:/inventory";
    }

    @PostMapping("/create-shipment")
    public String createShipment(@RequestParam Long componentId, @RequestParam Double purchasePrice,
                                 @RequestParam Integer quantity, @RequestParam String purchaseDate) {
        LocalDateTime date = LocalDateTime.parse(purchaseDate + "T00:00:00");
        warehouseService.createShipment(componentId, purchasePrice, quantity, date);
        return "redirect:/inventory";
    }

    @PostMapping("/edit-component")
    public String editComponent(@RequestParam Long componentId, @RequestParam String componentName) {
        // Логика редактирования компонента (допишите в WarehouseService)
        return "redirect:/inventory";
    }

    @PostMapping("/edit-shipment")
    public String editShipment(@RequestParam Long shipmentId, @RequestParam Double purchasePrice,
                               @RequestParam Integer quantity, @RequestParam String purchaseDate) {
        LocalDateTime date = LocalDateTime.parse(purchaseDate + "T00:00:00");
        // Логика редактирования поставки (допишите в WarehouseService)
        return "redirect:/inventory";
    }

    @DeleteMapping("/delete-component/{componentId}")
    @ResponseBody
    public String deleteComponent(@PathVariable Long componentId, @RequestParam Long productId) {
        // Логика удаления компонента и всех его поставок (допишите в WarehouseService)
        return "success";
    }

    @DeleteMapping("/delete-shipment/{shipmentId}")
    @ResponseBody
    public String deleteShipment(@PathVariable Long shipmentId, @RequestParam Long componentId) {
        // Логика удаления поставки (допишите в WarehouseService)
        return "success";
    }
}
