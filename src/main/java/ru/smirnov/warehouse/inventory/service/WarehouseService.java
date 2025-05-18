package ru.smirnov.warehouse.inventory.service;

import org.springframework.stereotype.Service;
import ru.smirnov.warehouse.inventory.entity.Component;
import ru.smirnov.warehouse.inventory.entity.Shipment;
import ru.smirnov.warehouse.inventory.repository.ComponentRepository;
import ru.smirnov.warehouse.inventory.repository.ShipmentRepository;
import ru.smirnov.warehouse.product.entity.Product;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class WarehouseService {

    private final ComponentRepository componentRepository;
    private final ShipmentRepository shipmentRepository;

    public WarehouseService(ComponentRepository componentRepository, ShipmentRepository shipmentRepository) {
        this.componentRepository = componentRepository;
        this.shipmentRepository = shipmentRepository;
    }

    public int calculateTotalQuantity(Product product) {
        List<Component> components = componentRepository.findByProduct(product);
        return components.stream()
                .mapToInt(this::calculateComponentQuantity)
                .sum();
    }

    public int calculateComponentQuantity(Component component) {
        List<Shipment> shipments = shipmentRepository.findByComponentId(component.getId());
        return shipments.stream()
                .mapToInt(Shipment::getQuantity)
                .sum();
    }

    public List<Component> getComponentsByProduct(Product product) {
        return componentRepository.findByProduct(product);
    }

    public List<Shipment> getShipmentsByComponent(Component component) {
        return shipmentRepository.findByComponentId(component.getId());
    }

    public void createComponent(String name, Long productId) {
        Product product = new Product(); // Замените на реальную загрузку из репозитория
        product.setId(productId);
        Component component = new Component();
        component.setName(name);
        component.setQuantity(0);
        component.setProduct(product);
        componentRepository.save(component);
    }

    public void createShipment(Long componentId, Double purchasePrice, Integer quantity, LocalDateTime purchaseDate) {
        Component component = componentRepository.findById(componentId)
                .orElseThrow(() -> new IllegalArgumentException("Component not found: " + componentId));
        Shipment shipment = new Shipment();
        shipment.setPurchasePrice(purchasePrice);
        shipment.setQuantity(quantity);
        shipment.setPurchaseDate(purchaseDate);
        shipment.setComponent(component);
        shipmentRepository.save(shipment);
        updateComponentQuantity(component);
    }

    private void updateComponentQuantity(Component component) {
        int totalQuantity = calculateComponentQuantity(component);
        component.setQuantity(totalQuantity);
        componentRepository.save(component);
    }
}