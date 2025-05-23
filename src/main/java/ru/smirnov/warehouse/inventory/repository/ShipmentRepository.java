package ru.smirnov.warehouse.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.smirnov.warehouse.inventory.entity.Shipment;

import java.util.List;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    List<Shipment> findAll();

    List<Shipment> findByComponentId(Long componentId);

    void deleteByComponentId(Long componentId);
}
