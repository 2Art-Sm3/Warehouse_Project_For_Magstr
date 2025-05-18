package ru.smirnov.warehouse.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.smirnov.warehouse.inventory.entity.Component;
import ru.smirnov.warehouse.product.entity.Product;

import java.util.List;

public interface ComponentRepository extends JpaRepository<Component, Long> {
    List<Component> findByProduct(Product product);
}
