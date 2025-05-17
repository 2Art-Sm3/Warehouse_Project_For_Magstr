package ru.smirnov.warehouse.product.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    @Column
    private String offerId;
    @Column(nullable = false)
    private String name;
//    @Column
//    private String color;
    @Column
    private Integer quantityForSale; // Количество к продаже (с Ozon)
    @Column
    private Integer quantityInStock; // Количество на складе (вручную)
    @Column
    private Double price; // Цена

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

    @Column(columnDefinition = "BIT DEFAULT FALSE")
    private Boolean hasAssembly; // Флаг наличия сборки

//      @Column
//    private Double cost; // Себестоимость (рассчитывается)
//    @Column
//    private Double margin; // Маржа (рассчитывается)
//    @Column
//    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
//    private List<ProductComponent> components;
}
