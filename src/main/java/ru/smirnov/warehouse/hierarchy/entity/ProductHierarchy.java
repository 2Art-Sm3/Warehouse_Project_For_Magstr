package ru.smirnov.warehouse.hierarchy.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.smirnov.warehouse.component.entity.Component;
import ru.smirnov.warehouse.product.entity.Product;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "product_hierarchy")
public class ProductHierarchy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne
    @JoinColumn(name = "component_id", nullable = false)
    private Component component; // Может быть Part или SubAssembly

    @Column(nullable = false)
    private Integer requiredQuantity; // Количество компонентов на 1 изделие

    @Column
    private String attachedFilePath; // Путь к файлу (например, чертеж)
}
