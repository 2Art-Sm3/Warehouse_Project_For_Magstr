package ru.smirnov.warehouse.hierarchy.service;

import org.springframework.stereotype.Service;
import ru.smirnov.warehouse.component.entity.Component;
import ru.smirnov.warehouse.component.entity.Part;
import ru.smirnov.warehouse.component.entity.SubAssembly;
import ru.smirnov.warehouse.component.repository.ComponentRepository;
import ru.smirnov.warehouse.hierarchy.entity.ProductHierarchy;
import ru.smirnov.warehouse.hierarchy.entity.SubAssemblyDetail;
import ru.smirnov.warehouse.hierarchy.repository.ProductHierarchyRepository;
import ru.smirnov.warehouse.hierarchy.repository.SubAssemblyDetailRepository;
import ru.smirnov.warehouse.inventory.entity.WarehouseStock;
import ru.smirnov.warehouse.inventory.repository.WarehouseStockRepository;
import ru.smirnov.warehouse.product.entity.Product;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProductHierarchyService {

    private final ProductHierarchyRepository hierarchyRepository;
    private final ComponentRepository componentRepository;
    private final SubAssemblyDetailRepository subAssemblyDetailRepository;
    private final WarehouseStockRepository stockRepository;

    public ProductHierarchyService(ProductHierarchyRepository hierarchyRepository,
                                   ComponentRepository componentRepository,
                                   SubAssemblyDetailRepository subAssemblyDetailRepository,
                                   WarehouseStockRepository stockRepository) {
        this.hierarchyRepository = hierarchyRepository;
        this.componentRepository = componentRepository;
        this.subAssemblyDetailRepository = subAssemblyDetailRepository;
        this.stockRepository = stockRepository;
    }

    public void createHierarchy(Product product, List<Long> componentIds, List<Integer> quantities, List<String> filePaths) {
        List<ProductHierarchy> hierarchies = new ArrayList<>();
        for (int i = 0; i < componentIds.size(); i++) {
            Component component = componentRepository.findById(componentIds.get(i))
                    .orElseThrow(() -> new IllegalArgumentException("Component not found: "));

            ProductHierarchy hierarchy = new ProductHierarchy();
            hierarchy.setProduct(product);
            hierarchy.setComponent(component);
            hierarchy.setRequiredQuantity(quantities.get(i));
            hierarchy.setAttachedFilePath(filePaths.get(i));
            hierarchies.add(hierarchy);

            // Если это новая деталь, добавляем её на склад с начальным количеством 0
            if (component instanceof Part && !stockRepository.existsByPart((Part) component)) {
                WarehouseStock stock = new WarehouseStock();
                stock.setPart((Part) component);
                stock.setQuantity(0);
                stock.setPurchasePrice(0.0);
                stock.setPurchaseDate(LocalDateTime.now());
                stockRepository.save(stock);
            }
        }
        hierarchyRepository.saveAll(hierarchies);
    }

    public void createSubAssembly(SubAssembly subAssembly, List<Long> componentIds, List<Integer> quantities) {
        List<SubAssemblyDetail> details = new ArrayList<>();
        for (int i = 0; i < componentIds.size(); i++) {
            Component component = componentRepository.findById(componentIds.get(i))
                    .orElseThrow(() -> new IllegalArgumentException("Component not found: "));

            SubAssemblyDetail detail = new SubAssemblyDetail();
            detail.setSubAssembly(subAssembly);
            detail.setComponent(component);
            detail.setQuantity(quantities.get(i));
            details.add(detail);

            // Если это новая деталь, добавляем её на склад
            if (component instanceof Part && !stockRepository.existsByPart((Part) component)) {
                WarehouseStock stock = new WarehouseStock();
                stock.setPart((Part) component);
                stock.setQuantity(0);
                stock.setPurchasePrice(0.0);
                stock.setPurchaseDate(LocalDateTime.now());
                stockRepository.save(stock);
            }
        }
        subAssemblyDetailRepository.saveAll(details);
    }
}
