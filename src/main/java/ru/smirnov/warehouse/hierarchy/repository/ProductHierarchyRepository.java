package ru.smirnov.warehouse.hierarchy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.smirnov.warehouse.hierarchy.entity.ProductHierarchy;
import ru.smirnov.warehouse.product.entity.Product;

import java.util.List;

public interface ProductHierarchyRepository extends JpaRepository<ProductHierarchy, Long> {

    List<ProductHierarchy> findByProduct(Product product);
}