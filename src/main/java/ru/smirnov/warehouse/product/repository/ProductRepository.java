package ru.smirnov.warehouse.product.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.smirnov.warehouse.common.entity.User;
import ru.smirnov.warehouse.product.entity.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    default List<Product> findAll() {
        return findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    Optional<Product> findByOfferId(String offerId);

    Optional<Product> findByOfferIdAndUser(String offerId, User user);
}
