package ru.smirnov.warehouse.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.smirnov.warehouse.component.entity.Part;
import ru.smirnov.warehouse.inventory.entity.WarehouseStock;

import java.util.List;

public interface WarehouseStockRepository extends JpaRepository<WarehouseStock, Long> {

    List<WarehouseStock> findByPartOrderByPurchaseDate(Part part);

    @Query("SELECT SUM(ws.quantity) FROM WarehouseStock ws WHERE ws.part = :part")
    Integer sumQuantityByPart(Part part);

    boolean existsByPart(Part part);
}
