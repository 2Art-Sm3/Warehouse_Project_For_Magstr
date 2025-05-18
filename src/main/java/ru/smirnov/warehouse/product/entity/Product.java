package ru.smirnov.warehouse.product.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.smirnov.warehouse.common.entity.User;
import ru.smirnov.warehouse.hierarchy.entity.HierarchyNode;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sku;
    @Column(unique = true)
    private String offerId;
    @Column(nullable = false)
    private String name;
    @Column
    private Integer quantityForSale; // Количество к продаже (с Ozon)
    @Column
    private Integer quantityInStock; // Количество на складе (вручную)
    @Column
    private Double price; // Цена
    @Column
    private Double totalAssemblyCost; // Общая себестоимость сборки (сумма себестоимостей 1-го уровня)

    @Column
    private Double ozonCommissions; // Сумма комиссий Ozon
    @Column
    private Double ozonReward; // Вознаграждение Ozon
    @Column
    private Double acquiringFee; // Эквайринг
    @Column
    private Double logisticsFee; // Логистика (средняя)
    @Column
    private Double lastMileFee; // Последняя миля
    @Column
    private Double otherFees; // Прочие комиссии

    @Column
    private Integer soldQuantity; // Продано (с Ozon)
    @Column
    private Integer orderedInTransit; // Заказано в пути (с Ozon)
    @Column
    private String fulfillmentType;

    @Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean hasAssembly; // Флаг наличия сборки

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "root_node_id")
    private HierarchyNode rootNode; // Корневой узел иерархии
}