package ru.smirnov.warehouse.inventory.service;

import org.springframework.stereotype.Service;
import ru.smirnov.warehouse.component.entity.Component;
import ru.smirnov.warehouse.component.entity.Part;
import ru.smirnov.warehouse.component.entity.SubAssembly;
import ru.smirnov.warehouse.hierarchy.entity.ProductHierarchy;
import ru.smirnov.warehouse.hierarchy.entity.SubAssemblyDetail;
import ru.smirnov.warehouse.hierarchy.repository.ProductHierarchyRepository;
import ru.smirnov.warehouse.hierarchy.repository.SubAssemblyDetailRepository;
import ru.smirnov.warehouse.inventory.entity.WarehouseStock;
import ru.smirnov.warehouse.inventory.repository.WarehouseStockRepository;
import ru.smirnov.warehouse.product.entity.Product;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WarehouseService {

    private final WarehouseStockRepository stockRepository;
    private final ProductHierarchyRepository hierarchyRepository;
    private final SubAssemblyDetailRepository subAssemblyDetailRepository;

    public WarehouseService(WarehouseStockRepository stockRepository,
                            ProductHierarchyRepository hierarchyRepository,
                            SubAssemblyDetailRepository subAssemblyDetailRepository) {
        this.stockRepository = stockRepository;
        this.hierarchyRepository = hierarchyRepository;
        this.subAssemblyDetailRepository = subAssemblyDetailRepository;
    }

    // Добавление партии деталей на склад
    public void addStock(Part part, int quantity, double purchasePrice) {
        WarehouseStock stock = new WarehouseStock();
        stock.setPart(part);
        stock.setQuantity(quantity);
        stock.setPurchasePrice(purchasePrice);
        stock.setPurchaseDate(LocalDateTime.now());
        stockRepository.save(stock);
    }

    // Расчёт, сколько товаров можно произвести
    public int calculateProducibleQuantity(Product product) {
        List<ProductHierarchy> hierarchies = hierarchyRepository.findByProduct(product);
        Map<Part, Integer> requiredParts = new HashMap<>();

        // Собираем все детали, включая вложенные из подсборок
        collectRequiredParts(hierarchies, requiredParts, 1);

        int minQuantity = Integer.MAX_VALUE;
        for (Map.Entry<Part, Integer> entry : requiredParts.entrySet()) {
            Part part = entry.getKey();
            int required = entry.getValue();
            int available = stockRepository.sumQuantityByPart(part);
            minQuantity = Math.min(minQuantity, available / required);
        }
        return minQuantity == Integer.MAX_VALUE ? 0 : minQuantity;
    }

    // Рекурсивный метод для сбора всех деталей из иерархии
    private void collectRequiredParts(List<ProductHierarchy> hierarchies, Map<Part, Integer> requiredParts, int multiplier) {
        for (ProductHierarchy hierarchy : hierarchies) {
            Component component = hierarchy.getComponent();
            int quantity = hierarchy.getRequiredQuantity() * multiplier;

            if (component instanceof Part) {
                requiredParts.merge((Part) component, quantity, Integer::sum);
            } else if (component instanceof SubAssembly) {
                List<SubAssemblyDetail> details = subAssemblyDetailRepository.findBySubAssembly((SubAssembly) component);
                for (SubAssemblyDetail detail : details) {
                    if (detail.getComponent() instanceof Part) {
                        requiredParts.merge((Part) detail.getComponent(), detail.getQuantity() * quantity, Integer::sum);
                    } else {
                        // Рекурсия для вложенных подсборок
                        List<SubAssemblyDetail> subDetails = subAssemblyDetailRepository.findBySubAssembly((SubAssembly) detail.getComponent());
                        collectSubAssemblyParts(subDetails, requiredParts, detail.getQuantity() * quantity);
                    }
                }
            }
        }
    }

    private void collectSubAssemblyParts(List<SubAssemblyDetail> details, Map<Part, Integer> requiredParts, int multiplier) {
        for (SubAssemblyDetail detail : details) {
            Component component = detail.getComponent();
            int quantity = detail.getQuantity() * multiplier;

            if (component instanceof Part) {
                requiredParts.merge((Part) component, quantity, Integer::sum);
            } else if (component instanceof SubAssembly) {
                List<SubAssemblyDetail> subDetails = subAssemblyDetailRepository.findBySubAssembly((SubAssembly) component);
                collectSubAssemblyParts(subDetails, requiredParts, quantity);
            }
        }
    }

    // Производство товара
    public void produceProduct(Product product, int quantity) {
        List<ProductHierarchy> hierarchies = hierarchyRepository.findByProduct(product);
        Map<Part, Integer> requiredParts = new HashMap<>();

        // Собираем все детали для производства
        collectRequiredParts(hierarchies, requiredParts, quantity);

        // Вычитаем детали из склада
        for (Map.Entry<Part, Integer> entry : requiredParts.entrySet()) {
            deductFromStock(entry.getKey(), entry.getValue());
        }

        // Увеличиваем количество готовых товаров
        product.setQuantityInStock(product.getQuantityInStock() + quantity);
    }

    private void deductFromStock(Part part, int requiredQuantity) {
        List<WarehouseStock> stocks = stockRepository.findByPartOrderByPurchaseDate(part);
        int remaining = requiredQuantity;

        for (WarehouseStock stock : stocks) {
            if (remaining <= 0) break;
            int toDeduct = Math.min(remaining, stock.getQuantity());
            stock.setQuantity(stock.getQuantity() - toDeduct);
            remaining -= toDeduct;
            if (stock.getQuantity() == 0) stockRepository.delete(stock);
            else stockRepository.save(stock);
        }

        if (remaining > 0) {
            throw new IllegalStateException("Not enough parts in stock for production: " + part.getName());
        }
    }
}
