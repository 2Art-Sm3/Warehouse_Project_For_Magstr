package ru.smirnov.warehouse.hierarchy.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.smirnov.warehouse.component.entity.Component;
import ru.smirnov.warehouse.component.repository.ComponentRepository;
import ru.smirnov.warehouse.hierarchy.entity.ProductHierarchy;
import ru.smirnov.warehouse.hierarchy.service.ProductHierarchyService;
import ru.smirnov.warehouse.product.entity.Product;
import ru.smirnov.warehouse.product.repository.ProductRepository;

import java.util.List;

@Controller
@RequestMapping("/hierarchy")
public class HierarchyController {

    private final ProductHierarchyService hierarchyService;
    private final ProductRepository productRepository;
    private final ComponentRepository componentRepository;

    public HierarchyController(ProductHierarchyService hierarchyService,
                               ProductRepository productRepository,
                               ComponentRepository componentRepository) {
        this.hierarchyService = hierarchyService;
        this.productRepository = productRepository;
        this.componentRepository = componentRepository;
    }

    @GetMapping("/{productId}")
    public String showHierarchy(@PathVariable Long productId, Model model) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        List<Component> components = componentRepository.findAll();
        List<ProductHierarchy> existingHierarchy = product.getHierarchies();
        model.addAttribute("product", product);
        model.addAttribute("components", components);
        model.addAttribute("existingHierarchy", existingHierarchy);
        return "hierarchy";
    }

    @GetMapping("/view/{productId}")
    public String viewHierarchy(@PathVariable Long productId, Model model) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        List<ProductHierarchy> existingHierarchy = product.getHierarchies();
        model.addAttribute("product", product);
        model.addAttribute("existingHierarchy", existingHierarchy);
        return "view-hierarchy";
    }

    @PostMapping("/create")
    public String createHierarchy(@RequestParam Long productId,
                                  @RequestParam List<Long> componentIds,
                                  @RequestParam List<Integer> quantities,
                                  @RequestParam List<String> filePaths) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        hierarchyService.createHierarchy(product, componentIds, quantities, filePaths);
        product.setHasAssembly(true);
        productRepository.save(product);
        return "redirect:/products/manage";
    }
}
