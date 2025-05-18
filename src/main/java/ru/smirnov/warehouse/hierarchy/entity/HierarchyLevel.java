package ru.smirnov.warehouse.hierarchy.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.smirnov.warehouse.product.entity.Product;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "hierarchy_levels")
public class HierarchyLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_node_id")
    private HierarchyNode parentNode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_node_id", nullable = false)
    private HierarchyNode childNode;

    @Column(nullable = false)
    private Integer level; // Уровень иерархии (1-5)
}
