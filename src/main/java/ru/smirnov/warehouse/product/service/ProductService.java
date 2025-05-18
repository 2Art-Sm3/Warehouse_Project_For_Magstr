package ru.smirnov.warehouse.product.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import ru.smirnov.warehouse.common.entity.User;
import ru.smirnov.warehouse.common.repository.UserRepository;
import ru.smirnov.warehouse.common.service.OzonService;
import ru.smirnov.warehouse.product.entity.Product;
import ru.smirnov.warehouse.product.repository.ProductRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


@Service
public class ProductService {

    private ProductRepository productRepository;
    private OzonService ozonService;
    private ObjectMapper objectMapper;
    private UserRepository userRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);

    public ProductService(ProductRepository productRepository, OzonService ozonService,
                          ObjectMapper objectMapper, UserRepository userRepository) {
        this.productRepository = productRepository;
        this.ozonService = ozonService;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof org.springframework.security.core.userdetails.User) {
            String username = ((org.springframework.security.core.userdetails.User) principal).getUsername(); // email, так как usernameParameter("email")
            return userRepository.findByUsername(username)
                    .orElseThrow(() -> new IllegalStateException("User not found for email: " + username));
        }
        return (User) principal; // Предполагаем, что principal — это ваш объект User
    }

    public List<Product> getAllProducts() {
        User currentUser = getCurrentUser();

        Sort sort = Sort.by(Sort.Direction.ASC, "id");
        return productRepository.findByUser(currentUser, sort);
    }

    public Product getProductById(Long id) {
        User currentUser = getCurrentUser();
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Товар не найден"));
        if (!product.getUser().getId().equals(currentUser.getId())) {
            throw new IllegalStateException("Товар не принадлежит текущему пользователю");
        }
        return product;
    }

    public Product saveProduct(Product product) {
        if (product.getUser() == null) {
            product.setUser(getCurrentUser());
        }
        return productRepository.save(product);
    }

    public void deleteProduct(Long id) {
        Product product = getProductById(id); // Уже включает проверку пользователя
        productRepository.deleteById(id);
    }

    public void syncProductsWithOzon() {

        User currentUser = getCurrentUser();

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

                    Optional<Product> existingProduct = productRepository.findByOfferIdAndUser(offerId, currentUser);
                    Product product;
                    if (existingProduct.isPresent()) {
                        product = existingProduct.get();
                        logger.info("Updating existing product with offerId: {}", offerId);
                    } else {
                        product = new Product();
                        product.setOfferId(offerId);
                        product.setUser(currentUser); // Добавлено: привязываем продукт к текущему пользователю
                        logger.info("Creating new product with offerId: {}", offerId);
                    }
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
                product.setOtherFees(round(countOrders > 0 ? sumOther / countOrders : 0.0));
                product.setOzonCommissions(round(
                        product.getOzonReward()
                                + product.getAcquiringFee()
                                + product.getLogisticsFee()
                                + product.getLastMileFee()
                                + product.getOtherFees()
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

}