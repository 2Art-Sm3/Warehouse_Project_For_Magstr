package ru.smirnov.warehouse.inventory.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.smirnov.warehouse.component.entity.Part;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "warehouse_stock")
public class WarehouseStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Double purchasePrice;

    @Column(nullable = false)
    private LocalDateTime purchaseDate; // Дата закупки для определения старой партии

    @ManyToOne
    @JoinColumn(name = "part_id", nullable = false)
    private Part part;
}
