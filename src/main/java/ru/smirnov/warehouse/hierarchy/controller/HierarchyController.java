package ru.smirnov.warehouse.hierarchy.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.smirnov.warehouse.hierarchy.entity.HierarchyNode;
import ru.smirnov.warehouse.hierarchy.service.HierarchyService;
import ru.smirnov.warehouse.inventory.service.WarehouseService;
import ru.smirnov.warehouse.product.entity.Product;
import ru.smirnov.warehouse.product.repository.ProductRepository;

import java.security.Principal;
import java.util.Map;

@Controller
@RequestMapping("/hierarchy")
public class HierarchyController {

    private final HierarchyService hierarchyService;
    private final WarehouseService warehouseService;
    private final ProductRepository productRepository;

    public HierarchyController(HierarchyService hierarchyService, WarehouseService warehouseService, ProductRepository productRepository) {
        this.hierarchyService = hierarchyService;
        this.warehouseService = warehouseService;
        this.productRepository = productRepository;
    }

    @ModelAttribute("warehouseService")
    public WarehouseService warehouseService() {
        return warehouseService;
    }

    @GetMapping("/{productId}")
    public String showHierarchy(@PathVariable Long productId, Model model, Principal principal) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        if (!product.getUser().getUsername().equals(principal.getName())) {
            throw new SecurityException("Access denied");
        }
        model.addAttribute("product", product);
        model.addAttribute("components", warehouseService.getComponentsByProduct(product));
        model.addAttribute("hierarchy", hierarchyService.getHierarchy(productId));
        model.addAttribute("rootNode", product.getRootNode());
        model.addAttribute("allNodes", hierarchyService.getAllNodes(productId));
        return "hierarchy";
    }

    @PostMapping("/{productId}")
    public String addNode(@PathVariable Long productId,
                          @RequestParam(required = false) Long parentNodeId,
                          @RequestParam String name,
                          @RequestParam Integer quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        if (parentNodeId == null) {
            hierarchyService.createNode(productId, name, quantity);
        } else {
            hierarchyService.addChildNode(parentNodeId, productId, name, quantity);
        }
        product.setHasAssembly(true);
        productRepository.save(product);
        return "redirect:/hierarchy/" + productId;
    }

    @PostMapping("/create-component-node/{productId}")
    @ResponseBody
    public String createComponentNode(@PathVariable Long productId, @RequestParam Long componentId,
                                      @RequestParam Integer quantity, @RequestParam Double unitCost) {
        hierarchyService.createComponentNode(productId, componentId, quantity, unitCost);
        return "success";
    }

    @PostMapping("/create-node/{productId}")
    @ResponseBody
    public String createNode(@PathVariable Long productId, @RequestParam String nodeName,
                             @RequestParam Integer quantity) {
        hierarchyService.createNode(productId, nodeName, quantity);
        return "success";
    }

    @PostMapping("/add-child-component/{parentNodeId}/{productId}")
    @ResponseBody
    public String addChildComponent(@PathVariable Long parentNodeId, @PathVariable Long productId,
                                    @RequestParam Long componentId, @RequestParam Integer quantity,
                                    @RequestParam Double unitCost) {
        hierarchyService.addChildComponent(parentNodeId, productId, componentId, quantity, unitCost);
        return "success";
    }

    @PostMapping("/add-child-node/{parentNodeId}/{productId}")
    @ResponseBody
    public String addChildNode(@PathVariable Long parentNodeId, @PathVariable Long productId,
                               @RequestParam String nodeName, @RequestParam Integer quantity) {
        hierarchyService.addChildNode(parentNodeId, productId, nodeName, quantity);
        return "success";
    }

    @DeleteMapping("/delete-node/{nodeId}")
    @ResponseBody
    public String deleteNode(@PathVariable Long nodeId) {
        hierarchyService.deleteNode(nodeId);
        return "success";
    }

    @PostMapping("/update-quantity/{nodeId}")
    @ResponseBody
    public Map<String, Boolean> updateQuantity(@PathVariable Long nodeId, @RequestParam Integer quantity) {
        HierarchyNode node = hierarchyService.getNodeRepository().findById(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + nodeId));
        node.setQuantity(quantity);
        node.setTotalCost(node.getUnitCost() != null ? node.getUnitCost() * quantity : 0.0);
        hierarchyService.getNodeRepository().save(node);
        hierarchyService.updateNodeCost(node);
        hierarchyService.updateParentCosts(node.getId());
        hierarchyService.updateProductAssemblyCost(hierarchyService.getProductByNode(nodeId));
        return Map.of("success", true);
    }

    @PostMapping("/update-unit-cost/{nodeId}")
    @ResponseBody
    public Map<String, Boolean> updateUnitCost(@PathVariable Long nodeId, @RequestParam Double unitCost) {
        HierarchyNode node = hierarchyService.getNodeRepository().findById(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + nodeId));
        node.setUnitCost(unitCost);
        node.setTotalCost(unitCost * node.getQuantity());
        hierarchyService.getNodeRepository().save(node);
        hierarchyService.updateNodeCost(node);
        hierarchyService.updateProductAssemblyCost(hierarchyService.getProductByNode(nodeId));
        return Map.of("success", true);
    }

    @PostMapping("/update-assembly-cost/{id}")
    public String updateAssemblyCost(@PathVariable Long id, @RequestParam Double assemblyCost) {
        HierarchyNode node = hierarchyService.getNodeRepository().findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + id));
        node.setAssemblyCost(assemblyCost);
        hierarchyService.getNodeRepository().save(node);
        Product product = hierarchyService.getProductByNode(id);
        hierarchyService.updateProductAssemblyCost(product);
        return "redirect:/hierarchy/" + product.getId();
    }
}