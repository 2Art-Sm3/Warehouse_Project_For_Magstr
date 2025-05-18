package ru.smirnov.warehouse.inventory.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public String showInventory(Model model) {
        model.addAttribute("products", productRepository.findAll());
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
        LocalDateTime date = LocalDateTime.parse(purchaseDate + "T00:00:00"); // Пример парсинга, настройте под нужный формат
        warehouseService.createShipment(componentId, purchasePrice, quantity, date);
        return "redirect:/inventory";
    }
}
