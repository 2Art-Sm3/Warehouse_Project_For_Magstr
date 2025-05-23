package ru.smirnov.warehouse.hierarchy.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.smirnov.warehouse.hierarchy.entity.HierarchyLevel;
import ru.smirnov.warehouse.hierarchy.entity.HierarchyNode;
import ru.smirnov.warehouse.hierarchy.repository.HierarchyLevelRepository;
import ru.smirnov.warehouse.hierarchy.repository.HierarchyNodeRepository;
import ru.smirnov.warehouse.inventory.entity.Component;
import ru.smirnov.warehouse.inventory.service.WarehouseService;
import ru.smirnov.warehouse.product.entity.Product;
import ru.smirnov.warehouse.product.repository.ProductRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HierarchyService {

    private final HierarchyNodeRepository hierarchyNodeRepository;
    private final HierarchyLevelRepository levelRepository;
    private final ProductRepository productRepository;
    private final WarehouseService warehouseService;

    public HierarchyNodeRepository getNodeRepository() {
        return hierarchyNodeRepository;
    }

    public Product getProductByNode(Long nodeId) {
        return levelRepository.findByChildNodeId(nodeId)
                .stream().findFirst()
                .map(HierarchyLevel::getProduct)
                .orElseThrow(() -> new IllegalArgumentException("Product not found for node: " + nodeId));
    }

    @Transactional
    public HierarchyNode createComponentNode(Long productId, Long componentId, Integer quantity, Double unitCost) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        Component component = warehouseService.getComponentById(componentId);
        HierarchyNode node = new HierarchyNode();
        node.setName(component.getName());
        node.setQuantity(quantity);
        node.setUnitCost(unitCost);
        node.setTotalCost(unitCost * quantity);
        node.setComponent(component);
        node.setIsNode(false);
        node = hierarchyNodeRepository.save(node);

        HierarchyLevel level = new HierarchyLevel();
        level.setProduct(product);
        level.setChildNode(node);
        level.setLevel(1); // Первый уровень
        levelRepository.save(level);

        updateProductAssemblyCost(product);
        product.setHasAssembly(true);
        product.setRootNode(node);
        productRepository.save(product);
        return node;
    }

    @Transactional
    public HierarchyNode createNode(Long productId, String nodeName, Integer quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        HierarchyNode node = new HierarchyNode();
        node.setName(nodeName);
        node.setQuantity(quantity);
        node.setUnitCost(0.0); // Изначально 0, рассчитывается позже
        node.setTotalCost(0.0);
        node.setIsNode(true);
        node = hierarchyNodeRepository.save(node);

        HierarchyLevel level = new HierarchyLevel();
        level.setProduct(product);
        level.setChildNode(node);
        level.setLevel(1); // Первый уровень
        levelRepository.save(level);

        updateProductAssemblyCost(product);
        product.setHasAssembly(true);
        product.setRootNode(node);
        productRepository.save(product);
        return node;
    }

    @Transactional
    public HierarchyNode addChildComponent(Long parentNodeId, Long productId, Long componentId, Integer quantity, Double unitCost) {
        HierarchyNode parent = hierarchyNodeRepository.findById(parentNodeId)
                .orElseThrow(() -> new IllegalArgumentException("Parent node not found: " + parentNodeId));
        if (getMaxLevel(parentNodeId) >= 5) {
            throw new IllegalArgumentException("Maximum hierarchy level (5) reached");
        }

        Component component = warehouseService.getComponentById(componentId);
        HierarchyNode child = new HierarchyNode();
        child.setName(component.getName());
        child.setQuantity(quantity);
        child.setUnitCost(unitCost);
        child.setTotalCost(unitCost * quantity);
        child.setComponent(component);
        child.setIsNode(false);
        child = hierarchyNodeRepository.save(child);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        HierarchyLevel level = new HierarchyLevel();
        level.setProduct(product);
        level.setParentNode(parent);
        level.setChildNode(child);
        level.setLevel(getLevel(parentNodeId) + 1);
        levelRepository.save(level);

        updateNodeCost(parent);
        updateProductAssemblyCost(product);
        return child;
    }

    @Transactional
    public HierarchyNode addChildNode(Long parentNodeId, Long productId, String nodeName, Integer quantity) {
        HierarchyNode parent = hierarchyNodeRepository.findById(parentNodeId)
                .orElseThrow(() -> new IllegalArgumentException("Parent node not found: " + parentNodeId));
        if (getMaxLevel(parentNodeId) >= 5) {
            throw new IllegalArgumentException("Maximum hierarchy level (5) reached");
        }

        HierarchyNode child = new HierarchyNode();
        child.setName(nodeName);
        child.setQuantity(quantity);
        child.setUnitCost(0.0);
        child.setTotalCost(0.0);
        child.setIsNode(true);
        child = hierarchyNodeRepository.save(child);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        HierarchyLevel level = new HierarchyLevel();
        level.setProduct(product);
        level.setParentNode(parent);
        level.setChildNode(child);
        level.setLevel(getLevel(parentNodeId) + 1);
        levelRepository.save(level);

        updateNodeCost(parent);
        updateProductAssemblyCost(product);
        return child;
    }

    @Transactional
    public void deleteNode(Long nodeId) {
        HierarchyNode node = hierarchyNodeRepository.findById(nodeId)
                .orElseThrow(() -> new EntityNotFoundException("Node not found with id " + nodeId));

        // Отвязать все продукты, связанные с этим узлом
        List<Product> products = productRepository.findByRootNode(node);
        for (Product p : products) {
            p.setRootNode(null); // отвязать
        }
        productRepository.saveAll(products);

        // Удалить все уровни, где этот узел — parentNode или childNode
        List<HierarchyLevel> parentLevels = levelRepository.findByParentNode(node);
        levelRepository.deleteAll(parentLevels);

        List<HierarchyLevel> childLevels = levelRepository.findByChildNode(node);
        levelRepository.deleteAll(childLevels);

        // Теперь можно удалить сам узел
        hierarchyNodeRepository.delete(node);
    }

    private int getLevel(Long nodeId) {
        HierarchyLevel level = levelRepository.findByChildNodeId(nodeId)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Level not found for node: " + nodeId));
        return level.getLevel();
    }

    private int getMaxLevel(Long nodeId) {
        HierarchyNode node = hierarchyNodeRepository.findById(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + nodeId));
        if (!node.getIsNode()) return getLevel(nodeId);
        List<HierarchyLevel> children = levelRepository.findByParentNodeId(nodeId);
        if (children.isEmpty()) return getLevel(nodeId);
        return children.stream().mapToInt(child -> getMaxLevel(child.getChildNode().getId())).max().orElse(getLevel(nodeId));
    }

    public void updateNodeCost(HierarchyNode node) {
        if (!node.getIsNode()) return;
        List<HierarchyLevel> children = levelRepository.findByParentNodeId(node.getId());
        double totalCost = children.stream()
                .mapToDouble(child -> child.getChildNode().getTotalCost() != null ? child.getChildNode().getTotalCost() : 0.0)
                .sum();
        node.setTotalCost(totalCost);
        node.setUnitCost(totalCost / (node.getQuantity() != 0 ? node.getQuantity() : 1));
        hierarchyNodeRepository.save(node);
    }

    public void updateParentCosts(Long childNodeId) {
        levelRepository.findByChildNodeId(childNodeId)
                .stream()
                .findFirst()
                .map(HierarchyLevel::getParentNode)
                .ifPresent(parent -> {
                    // parent — ненулевой узел
                    updateNodeCost(parent);
                    updateParentCosts(parent.getId());
                });
    }

    public void updateProductAssemblyCost(Product product) {
        List<HierarchyLevel> rootLevels = levelRepository.findByProductId(product.getId())
                .stream().filter(l -> l.getParentNode() == null).toList();
        double totalCost = rootLevels.stream()
                .mapToDouble(l -> l.getChildNode().getTotalCost() != null ? l.getChildNode().getTotalCost() : 0.0)
                .sum();
        product.setTotalAssemblyCost(totalCost);
        productRepository.save(product);
    }

    public List<HierarchyLevel> getHierarchy(Long productId) {
        return levelRepository.findByProductId(productId);
    }

    public List<HierarchyNode> getAllNodes(Long productId) {
        List<HierarchyLevel> levels = levelRepository.findByProductId(productId);
        return levels.stream()
                .map(HierarchyLevel::getChildNode)
                .distinct()
                .collect(Collectors.toList());
    }
}