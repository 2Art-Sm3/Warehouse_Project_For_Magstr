package ru.smirnov.warehouse.inventory.service;

import org.springframework.stereotype.Service;
import ru.smirnov.warehouse.inventory.entity.Component;
import ru.smirnov.warehouse.inventory.entity.Shipment;
import ru.smirnov.warehouse.inventory.repository.ComponentRepository;
import ru.smirnov.warehouse.inventory.repository.ShipmentRepository;
import ru.smirnov.warehouse.product.entity.Product;
import ru.smirnov.warehouse.product.repository.ProductRepository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class WarehouseService {

    private final ComponentRepository componentRepository;
    private final ShipmentRepository shipmentRepository;
    private final ProductRepository productRepository;

    public WarehouseService(ComponentRepository componentRepository, ShipmentRepository shipmentRepository, ProductRepository productRepository) {
        this.componentRepository = componentRepository;
        this.shipmentRepository = shipmentRepository;
        this.productRepository = productRepository;
    }

    public List<Shipment> getAllShipments() {
        List<Shipment> list = shipmentRepository.findAll();
        return list;
    }

    public int calculateTotalQuantity(Product product) {
        List<Component> components = componentRepository.findByProductOrderByIdAsc(product);
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
        return componentRepository.findByProductOrderByIdAsc(product);
    }

    public List<Shipment> getShipmentsByComponentId(Long componentId) {
        return shipmentRepository.findByComponentId(componentId);
    }


    public List<Shipment> getShipmentsByComponent(Component component) {
        List<Shipment> shipments = shipmentRepository.findByComponentId(component.getId());
        shipments.sort(Comparator.comparing(Shipment::getPurchaseDate));
        return shipments;
    }

    public Component getComponentById(Long componentId) {
        return componentRepository.findById(componentId)
                .orElseThrow(() -> new IllegalArgumentException("Component not found: " + componentId));
    }

    public Shipment getShipmentById(Long shipmentId) {
        return shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found: " + shipmentId));
    }

    public void createComponent(String name, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
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

    public void updateComponentQuantity(Component component) {
        int totalQuantity = calculateComponentQuantity(component);
        component.setQuantity(totalQuantity);
        componentRepository.save(component);
    }

    public void editComponent(Long componentId, String newName) {
        Component component = componentRepository.findById(componentId)
                .orElseThrow(() -> new IllegalArgumentException("Component not found: " + componentId));
        component.setName(newName);
        componentRepository.save(component);
    }

    public void editShipment(Long shipmentId, Double purchasePrice, Integer quantity, LocalDateTime purchaseDate) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found: " + shipmentId));
        shipment.setPurchasePrice(purchasePrice);
        shipment.setQuantity(quantity);
        shipment.setPurchaseDate(purchaseDate);
        shipmentRepository.save(shipment);
        updateComponentQuantity(shipment.getComponent());
    }

    public void deleteComponent(Long componentId) {
        Component component = componentRepository.findById(componentId)
                .orElseThrow(() -> new IllegalArgumentException("Component not found: " + componentId));
        shipmentRepository.deleteByComponentId(componentId);
        componentRepository.delete(component);
    }

    public void deleteShipment(Long shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found: " + shipmentId));
        Component component = shipment.getComponent();
        shipmentRepository.delete(shipment);
        updateComponentQuantity(component);
    }
}