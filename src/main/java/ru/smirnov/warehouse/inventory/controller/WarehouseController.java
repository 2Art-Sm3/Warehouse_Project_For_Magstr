package ru.smirnov.warehouse.inventory.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.smirnov.warehouse.component.entity.Component;
import ru.smirnov.warehouse.component.entity.Part;
import ru.smirnov.warehouse.component.repository.ComponentRepository;
import ru.smirnov.warehouse.inventory.service.WarehouseService;
import ru.smirnov.warehouse.product.entity.Product;
import ru.smirnov.warehouse.product.repository.ProductRepository;

import java.util.List;

@Controller
@RequestMapping("/warehouse")
public class WarehouseController {

    private final WarehouseService warehouseService;
    private final ProductRepository productRepository;
    private final ComponentRepository componentRepository;

    public WarehouseController(WarehouseService warehouseService,
                               ProductRepository productRepository,
                               ComponentRepository componentRepository) {
        this.warehouseService = warehouseService;
        this.productRepository = productRepository;
        this.componentRepository = componentRepository;
    }

    @GetMapping
    public String showWarehouse(Model model) {
        List<Product> products = productRepository.findAll();
        List<Component> components = componentRepository.findAll();
        model.addAttribute("products", products);
        model.addAttribute("components", components);
        model.addAttribute("warehouseService", warehouseService);
        return "warehouse";
    }

    @PostMapping("/addStock")
    public String addStock(@RequestParam Long partId, @RequestParam int quantity, @RequestParam double purchasePrice) {
        Part part = (Part) componentRepository.findById(partId)
                .orElseThrow(() -> new IllegalArgumentException("Part not found: " + partId));
        warehouseService.addStock(part, quantity, purchasePrice);
        return "redirect:/warehouse";
    }

    @PostMapping("/produce")
    public String produceProduct(@RequestParam Long productId, @RequestParam int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        warehouseService.produceProduct(product, quantity);
        productRepository.save(product);
        return "redirect:/warehouse";
    }
}
