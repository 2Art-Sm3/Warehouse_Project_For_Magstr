package ru.smirnov.warehouse.product.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.smirnov.warehouse.common.service.OzonService;
import ru.smirnov.warehouse.product.entity.Product;
import ru.smirnov.warehouse.product.repository.ProductRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//import static sun.awt.geom.Curve.round;

@Service
public class ProductService {

    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);

    private ProductRepository productRepository;
    private OzonService ozonService;
    private ObjectMapper objectMapper;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    public ProductService(ProductRepository productRepository, OzonService ozonService, ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.ozonService = ozonService;
        this.objectMapper = objectMapper;
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Товар не найден"));
    }

    public Product saveProduct(Product product) {
        return productRepository.save(product);
    }

    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }

    public void syncProductsWithOzon() {
        // Очистка таблицы перед новой синхронизацией
        productRepository.deleteAll();
        logger.info("Cleared all products from the database");

        // Получаем список товаров
        String lastId = "";
        int limit = 100;
        boolean hasMoreItems = true;

        while (hasMoreItems) {
            logger.info("Fetching product list with lastId: {}", lastId);
            String response = ozonService.getProductList(lastId, limit);
            try {
                JsonNode root = objectMapper.readTree(response);
                JsonNode items = root.path("result").path("items");

                for (JsonNode item : items) {
                    String offerId = item.path("offer_id").asText();
                    String productInfoResponse = ozonService.getProductInfoByOfferId(offerId);
                    if (productInfoResponse == null) {
                        // пропускаем этот артикул, если запрос упал
                        continue;
                    }
                    logger.debug("productInfoResponse for {}: {}", offerId, productInfoResponse);

                    JsonNode productInfoRoot = objectMapper.readTree(productInfoResponse);
                    JsonNode itemsInfo = productInfoRoot.path("items");
                    if (!itemsInfo.isArray() || itemsInfo.size() == 0) {
                        logger.warn("Ozon вернул пустой items для offerId={}!", offerId);
                        continue;
                    }

                    JsonNode productInfoItem = itemsInfo.get(0);
                    String sku = productInfoItem.path("sources").get(0).path("sku").asText();
                    String name = productInfoItem.path("name").asText();
                    Product product = new Product();
                    product.setOfferId(offerId);
                    product.setSku(sku);
                    product.setName(name);
                    saveProduct(product);
                }

                lastId = root.path("result").path("last_id").asText();
                hasMoreItems = !lastId.isEmpty() && items.size() > 0;
                logger.info("Fetched {} items, newLastId: {}, hasMoreItems: {}", items.size(), lastId, hasMoreItems);
            } catch (Exception e) {
                logger.error("Error processing product list response: {}", e.getMessage());
                hasMoreItems = false;
            }
        }

        // Синхронизация остатков и информации о товаре
        List<Product> products = new ArrayList<>(getAllProducts());
        for (Product product : products) {
            String offerId = product.getOfferId();
            String sku = product.getSku();
            logger.info("Syncing details for SKU: {}", sku);

            // Информация о товаре
            String productInfoResponse = ozonService.getProductInfoByOfferId(offerId);
            try {
                JsonNode root = objectMapper.readTree(productInfoResponse);
                JsonNode item = root.path("items").get(0);
                if (item != null) {
                    product.setName(item.path("name").asText(product.getName()));
                    product.setPrice(item.path("price").asDouble());
                    JsonNode stocks = item.path("stocks").path("stocks");
                    int quantityForSale = 0;
                    String fulfillmentType = null;
                    for (JsonNode stock : stocks) {
                        quantityForSale += stock.path("present").asInt();
                        String source = stock.path("source").asText();
                        if ("fbo".equals(source)) fulfillmentType = "FBO";
                        else if ("fbs".equals(source)) fulfillmentType = "FBS";
                    }
                    product.setQuantityForSale(quantityForSale);
                    product.setFulfillmentType(fulfillmentType);
                    logger.info("Updated product info for SKU: {}, quantityForSale: {}, fulfillmentType: {}", sku, quantityForSale, fulfillmentType);
                    saveProduct(product);
                }
            } catch (Exception e) {
                logger.error("Error processing product info for SKU {}: {}", sku, e.getMessage());
            }

            // Аналитика по остаткам
            String stockAnalyticsResponse = ozonService.getStockAnalytics(sku);
            try {
                JsonNode root = objectMapper.readTree(stockAnalyticsResponse);
                JsonNode item = root.path("items").get(0);
                if (item != null) {
                    product.setOrderedInTransit(item.path("transit_stock_count").asInt());
                    logger.info("Updated stock analytics for SKU: {}, orderedInTransit: {}", sku, item.path("transit_stock_count").asInt());
                    saveProduct(product);
                }
            } catch (Exception e) {
                logger.error("Error processing stock analytics for SKU {}: {}", sku, e.getMessage());
            }
        }

        // Синхронизация транзакций и комиссий
        syncTransactionsAndCommissions();
    }

    private void syncTransactionsAndCommissions() {
        try {
            // 1. Получаем список транзакций за последний месяц
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime oneMonthAgo = now.minusDays(30);
            String fromDate = oneMonthAgo.format(DATE_FORMATTER);
            String toDate   = now.format(DATE_FORMATTER);

            String response = ozonService.getTransactionList(fromDate, toDate, "", "all");
            if (response == null) {
                logger.warn("Transaction list response is null, skipping commission sync");
                return;
            }

            JsonNode operations = objectMapper.readTree(response)
                    .path("result")
                    .path("operations");

            // 2. Собираем по каждому SKU список метрик по каждой операции
            Map<String, List<SaleMetrics>> bySku = new HashMap<>();

            for (JsonNode op : operations) {
                String opName = op.path("operation_type_name").asText();
                JsonNode items = op.path("items");
                if (!items.isArray() || items.isEmpty()) continue;

                // 2.1. Продажа — «Доставка покупателю»
                if ("Доставка покупателю".equals(opName)) {
                    double ozonReward = Math.abs(op.path("sale_commission").asDouble(0.0));

                    double lastMile   = 0,
                            logistics  = 0,
                            otherFees  = 0;

                    for (JsonNode svc : op.path("services")) {
                        String name  = svc.path("name").asText();
                        double price = Math.abs(svc.path("price").asDouble(0.0));

                        switch (name) {
                            case "MarketplaceServiceItemDelivToCustomer":
                                lastMile += price;
                                break;
                            case "MarketplaceServiceItemDirectFlowLogistic":
                                logistics += price;
                                break;
                            case "MarketplaceServiceItemDropoffPVZ":
                            case "MarketplaceServiceItemRedistributionDropOffApvz":
                                otherFees += price;
                                break;
                        }
                    }

                    SaleMetrics metrics = new SaleMetrics(ozonReward, 0, logistics, lastMile, otherFees);
                    items.forEach(item -> {
                        String sku = item.path("sku").asText();
                        bySku.computeIfAbsent(sku, k -> new ArrayList<>()).add(metrics);
                    });
                }

                // 2.2. Эквайринг — «Оплата эквайринга»
                if ("Оплата эквайринга".equals(opName)) {
                    double acquiring = 0;
                    for (JsonNode svc : op.path("services")) {
                        if ("MarketplaceRedistributionOfAcquiringOperation"
                                .equals(svc.path("name").asText())) {
                            acquiring = Math.abs(svc.path("price").asDouble(0.0));
                            break;
                        }
                    }
                    SaleMetrics metrics = new SaleMetrics(0, acquiring, 0, 0, 0);
                    items.forEach(item -> {
                        String sku = item.path("sku").asText();
                        bySku.computeIfAbsent(sku, k -> new ArrayList<>()).add(metrics);
                    });
                }
            }

            // 3. Агрегация для каждого товара
            for (Product product : getAllProducts()) {
                String sku = product.getSku();
                List<SaleMetrics> sales = bySku.getOrDefault(sku, List.of());
                if (sales.isEmpty()) continue;

                double sumReward = 0, sumAcq = 0, sumLog = 0, sumOther = 0;
                double maxLastMile = 0;
                int    countOrders = 0, countAcq = 0;

                for (SaleMetrics m : sales) {
                    if (m.ozonReward() > 0) {
                        sumReward += m.ozonReward();
                        countOrders++;
                    }
                    if (m.acquiringFee() > 0) {
                        sumAcq += m.acquiringFee();
                        countAcq++;
                    }
                    sumLog   += m.logisticsFee();
                    sumOther += m.otherFees();
                    maxLastMile = Math.max(maxLastMile, m.lastMileFee());
                }

                product.setOzonReward(round(countOrders > 0 ? sumReward / countOrders : 0.0));
                product.setAcquiringFee(round(countAcq > 0 ? sumAcq / countAcq : 0.0));
                product.setLogisticsFee(round(countOrders > 0 ? sumLog / countOrders : 0.0));
                product.setLastMileFee(round(maxLastMile));
                product.setOzonCommissions(round(
                        product.getOzonReward()
                                + product.getAcquiringFee()
                                + product.getLogisticsFee()
                                + product.getLastMileFee()
                                + round(countOrders > 0 ? sumOther / countOrders : 0.0)
                ));

                saveProduct(product);
            }

        } catch (Exception e) {
            logger.error("Error processing transactions: {}", e.getMessage());
        }
    }

    // Вспомогательный record для одной продажи
    private static record SaleMetrics(
            double ozonReward,
            double acquiringFee,
            double logisticsFee,
            double lastMileFee,
            double otherFees
    ) {}


//    private void syncTransactionsAndCommissions() {
//        LocalDateTime now = LocalDateTime.now();
//        LocalDateTime oneMonthAgo = now.minusDays(30);
//        String fromDate = oneMonthAgo.format(DATE_FORMATTER);
//        String toDate = now.format(DATE_FORMATTER);
//
//        String response = ozonService.getTransactionList(fromDate, toDate, "", "all");
//        try {
//            logger.debug("Transaction list response: {}", response);
//            JsonNode root = objectMapper.readTree(response);
//            JsonNode operations = root.path("result").path("operations");
//            if (!operations.isArray() || operations.size() == 0) {
//                logger.warn("No transaction operations found in response");
//                return;
//            }
//
//            // Карта для агрегации данных по SKU
//            Map<String, Map<String, Double>> feeMap = new HashMap<>();
//            Map<String, Integer> soldCountMap = new HashMap<>();
//
//            for (JsonNode operation : operations) {
//                JsonNode items = operation.path("items");
//                if (!items.isArray() || items.size() == 0) continue;
//
//                String opType = operation.path("type").asText();
//                String opName = operation.path("operation_type").asText();
//
//                for (JsonNode item : items) {
//                    String sku = item.path("sku").asText();
//                    // получаем или создаём метрики
//                    Map<String, Double> fees = feeMap.computeIfAbsent(sku, k -> new HashMap<>());
//                    fees.putIfAbsent("ozonReward", 0.0);
//                    fees.putIfAbsent("acquiringFee", 0.0);
//                    fees.putIfAbsent("logisticsFee", 0.0);
//                    fees.putIfAbsent("lastMileFee", 0.0);
//
//                    // 1) Продажа — это orders
//                    if ("orders".equals(opType)) {
//                        // accruals_for_sale → ozonReward
//                        double reward = operation.path("accruals_for_sale").asDouble(0.0);
//                        fees.put("ozonReward", fees.get("ozonReward") + reward);
//
//                        // services в заказах содержат логистику и последнюю милю
//                        for (JsonNode service : operation.path("services")) {
//                            String name = service.path("name").asText();
//                            double price = Math.abs(service.path("price").asDouble(0.0));
//                            if (name.equals("MarketplaceServiceItemDelivToCustomer")) {
//                                // берем максимум
//                                fees.put("lastMileFee", Math.max(fees.get("lastMileFee"), price));
//                            } else if (name.startsWith("MarketplaceServiceItemDirectFlowLogistic")
//                                    || name.equals("MarketplaceServiceItemDeliveryKGT")) {
//                                fees.put("logisticsFee", fees.get("logisticsFee") + price);
//                            }
//                        }
//                    }
//
//                    // 2) Эквайринг — отдельный тип операции
//                    if ("other".equals(opType) && "MarketplaceRedistributionOfAcquiringOperation".equals(opName)) {
//                        for (JsonNode service : operation.path("services")) {
//                            if ("MarketplaceRedistributionOfAcquiringOperation"
//                                    .equals(service.path("name").asText())) {
//                                double price = Math.abs(service.path("price").asDouble(0.0));
//                                fees.put("acquiringFee", fees.get("acquiringFee") + price);
//                            }
//                        }
//                    }
//
//                    soldCountMap.compute(sku, (k, v) -> v == null ? 1 : v + 1);
//                }
//            }
//
//
////            for (JsonNode operation : operations) {
////                JsonNode items = operation.path("items");
////                if (!items.isArray() || items.size() == 0) {
////                    continue; // Пропускаем операции без товаров
////                }
////
////                String deliverySchema = operation.path("posting").path("delivery_schema").asText("unknown");
////                double saleCommission = operation.path("sale_commission").asDouble(0.0);
////
////                // Обработка каждого товара в операции
////                for (JsonNode item : items) {
////                    String sku = item.path("sku").asText();
////                    feeMap.computeIfAbsent(sku, k -> new HashMap<>()).putIfAbsent("ozonReward", 0.0);
////                    feeMap.computeIfAbsent(sku, k -> new HashMap<>()).putIfAbsent("acquiringFee", 0.0);
////                    feeMap.computeIfAbsent(sku, k -> new HashMap<>()).putIfAbsent("logisticsFee", 0.0);
////                    feeMap.computeIfAbsent(sku, k -> new HashMap<>()).putIfAbsent("lastMileFee", Double.MIN_VALUE); // Для максимума
////
////                    // Увеличиваем количество продаж
////                    soldCountMap.compute(sku, (k, v) -> v == null ? 1 : v + 1);
////
////                    // Добавляем sale_commission как ozonReward
////                    feeMap.get(sku).put("ozonReward", feeMap.get(sku).get("ozonReward") + Math.abs(saleCommission));
////
////                    // Обработка services
////                    JsonNode services = operation.path("services");
////                    for (JsonNode service : services) {
////                        double price = service.path("price").asDouble(0.0);
////                        String serviceName = service.path("name").asText().toLowerCase();
////                        if (serviceName.contains("marketplaceredistributionofacquiringoperation")) {
////                            feeMap.get(sku).put("acquiringFee", feeMap.get(sku).get("acquiringFee") + Math.abs(price));
////                        } else if (serviceName.contains("marketplaceserviceitemdirectflowlogistic")) {
////                            feeMap.get(sku).put("logisticsFee", feeMap.get(sku).get("logisticsFee") + Math.abs(price));
////                        } else if (serviceName.contains("marketplaceserviceitemdelivtocustomer")) {
////                            double currentMax = feeMap.get(sku).get("lastMileFee");
////                            feeMap.get(sku).put("lastMileFee", Math.max(currentMax, Math.abs(price)));
////                        }
////                    }
////                }
////            }
//
//            List<Product> products = new ArrayList<>(getAllProducts());
//            for (Product product : products) {
//                String sku = product.getSku();
//                String fulfillmentType = product.getFulfillmentType();
//                if (fulfillmentType != null) {
//                    Map<String, Double> fees = feeMap.getOrDefault(sku, new HashMap<>());
//                    product.setOzonReward(fees.getOrDefault("ozonReward", 0.0));
//                    product.setAcquiringFee(fees.getOrDefault("acquiringFee", 0.0));
//                    product.setLogisticsFee(fees.getOrDefault("logisticsFee", 0.0));
//                    product.setLastMileFee(fees.getOrDefault("lastMileFee", 0.0) == Double.MIN_VALUE ? 0.0 : fees.getOrDefault("lastMileFee", 0.0));
//                    product.setOzonCommissions(
//                            product.getOzonReward() +
//                                    product.getAcquiringFee() +
//                                    product.getLogisticsFee() +
//                                    product.getLastMileFee()
//                    );
//                    product.setSoldQuantity(soldCountMap.getOrDefault(sku, 0));
//                    logger.info("Updated fees for SKU: {}, ozonReward: {}, acquiringFee: {}, logisticsFee: {}, lastMileFee: {}, ozonCommissions: {}, soldQuantity: {}",
//                            sku, product.getOzonReward(), product.getAcquiringFee(), product.getLogisticsFee(), product.getLastMileFee(),
//                            product.getOzonCommissions(), product.getSoldQuantity());
//                    saveProduct(product);
//                } else {
//                    logger.warn("No fulfillmentType for SKU: {}, skipping fee update", sku);
//                }
//            }
//        } catch (Exception e) {
//            logger.error("Error processing transactions: {}", e.getMessage());
//        }
//    }

//    private void syncTransactionsAndCommissions() {
//        LocalDateTime now = LocalDateTime.now();
//        LocalDateTime oneMonthAgo = now.minusDays(30);
//        String fromDate = oneMonthAgo.format(DATE_FORMATTER);
//        String toDate = now.format(DATE_FORMATTER);
//
//        String response = ozonService.getTransactionList(fromDate, toDate, "", "all");
//        try {
//            JsonNode root = objectMapper.readTree(response);
//            JsonNode operations = root.path("result").path("operations");
//            Map<String, Map<String, List<Double>>> commissionMap = new HashMap<>();
//            Map<String, Integer> soldCountMap = new HashMap<>();
//
//            for (JsonNode operation : operations) {
//                JsonNode items = operation.path("items");
//                String deliverySchema = operation.path("posting").path("delivery_schema").asText();
//                for (JsonNode item : items) {
//                    String sku = item.path("sku").asText();
//                    commissionMap.computeIfAbsent(sku, k -> new HashMap<>())
//                            .computeIfAbsent(deliverySchema, k -> new ArrayList<>());
//                    soldCountMap.compute(sku, (k, v) -> v == null ? 1 : v + 1);
//
//                    JsonNode services = operation.path("services");
//                    for (JsonNode service : services) {
//                        String serviceName = service.path("name").asText();
//                        double price = service.path("price").asDouble();
//                        commissionMap.get(sku).get(deliverySchema).add(price);
//                    }
//                    commissionMap.get(sku).get(deliverySchema).add(operation.path("sale_commission").asDouble());
//                }
//            }
//
//            List<Product> products = new ArrayList<>(getAllProducts());
//            for (Product product : products) {
//                String sku = product.getSku();
//                String fulfillmentType = product.getFulfillmentType();
//                if (fulfillmentType != null) {
//                    Map<String, List<Double>> skuCommissions = commissionMap.getOrDefault(sku, new HashMap<>());
//                    List<Double> commissions = skuCommissions.getOrDefault(fulfillmentType, List.of());
//                    double totalCommissions = commissions.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
//                    product.setOzonCommissions(totalCommissions);
//                    product.setSoldQuantity(soldCountMap.getOrDefault(sku, 0));
//                    logger.info("Updated commissions for SKU: {}, totalCommissions: {}, soldQuantity: {}", sku, totalCommissions, soldCountMap.getOrDefault(sku, 0));
//                    saveProduct(product);
//                }
//            }
//        } catch (Exception e) {
//            logger.error("Error processing transactions: {}", e.getMessage());
//        }
//    }
}