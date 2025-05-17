package ru.smirnov.warehouse.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.smirnov.warehouse.product.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
