package ru.smirnov.warehouse.hierarchy.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.smirnov.warehouse.inventory.entity.Component;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "hierarchy_nodes")
public class HierarchyNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // Для узла — имя, для компонента — имя из Component

    @Column(nullable = false)
    private Integer quantity; // Количество компонентов или узлов

    @Column
    private Double unitCost; // Себестоимость за единицу (выбирается из партий для компонентов)

    @Column
    private Double totalCost; // Суммарная себестоимость (unitCost * quantity)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_id")
    private Component component; // Ссылка на компонент, если это компонентный узел

    @Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean isNode; // Флаг, указывающий, является ли это узлом (иначе — компонент)

    @Column
    private Double assemblyCost; // Поле для стоимости сборки
}