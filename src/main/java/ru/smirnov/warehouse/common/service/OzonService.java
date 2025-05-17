package ru.smirnov.warehouse.common.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import ru.smirnov.warehouse.common.entity.User;

import java.time.Duration;

@Service
public class OzonService {

    private static final Logger logger = LoggerFactory.getLogger(OzonService.class);

    private UserService userService;
    private WebClient webClient;

    public OzonService(UserService userService) {
        this.userService = userService;
    }

    @Bean
    public WebClient webClient() {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1048576)) // 1 MB
                .build();
        return WebClient.builder()
                .exchangeStrategies(strategies)
                .baseUrl("https://api-seller.ozon.ru")
                .build();
    }

    private WebClient getWebClientForUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден"));
        if (user.getOzonClientId() == null || user.getOzonApiKey() == null) {
            throw new IllegalStateException("Ключи Ozon API не настроены");
        }
        return webClient().mutate()
                .defaultHeader("Client-Id", user.getOzonClientId())
                .defaultHeader("Api-Key", user.getOzonApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public void resetWebClient() {
        this.webClient = null;
    }

    public String testOzonApiConnection() {
        try {
            return getWebClientForUser()
                    .post()
                    .uri("/v1/description-category/tree")
                    .bodyValue("{\"language\":\"DEFAULT\"}")
                    .retrieve()
                    .toBodilessEntity()
                    .map(response -> "Коннект установлен (HTTP " + response.getStatusCode().value() + ")")
                    .block(Duration.ofSeconds(10));
        } catch (Exception e) {
            logger.error("Error in testOzonApiConnection: {}", e.getMessage());
            return "Ошибка подключения к Ozon API: " + e.getMessage();
        }
    }

    public String getProductList(String lastId, int limit) {
        String requestBody = String.format("{\"filter\":{\"visibility\":\"ALL\"},\"last_id\":\"%s\",\"limit\":%d}", lastId, limit);
        try {
            String response = getWebClientForUser()
                    .post()
                    .uri("/v3/product/list")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(10));
            Thread.sleep(500); // Задержка 500 мс
            return response;
        } catch (Exception e) {
            logger.error("Error in getProductList: {}", e.getMessage());
            return "Ошибка получения списка товаров: " + e.getMessage();
        }
    }

    public String getProductInfoByOfferId(String offerId) {
        String requestBody = String.format("{\"offer_id\":[\"%s\"]}", offerId);
        try {
            return getWebClientForUser()
                    .post()
                    .uri("/v3/product/info/list")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(10));
        } catch (Exception e) {
            logger.error("Error in getProductInfoByOfferId for offerId {}: ", offerId, e);
            return null;
        }
    }

    public String getStockAnalytics(String sku) {
        String requestBody = String.format("{\"skus\":[\"%s\"]}", sku);
        try {
            String response = getWebClientForUser()
                    .post()
                    .uri("/v1/analytics/stocks")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(10));
            Thread.sleep(500);
            return response;
        } catch (Exception e) {
            logger.error("Error in getStockAnalytics for SKU {}: {}", sku, e.getMessage());
            return "Ошибка получения аналитики по остаткам: " + e.getMessage();
        }
    }

    public String getTransactionList(String fromDate, String toDate, String postingNumber, String transactionType) {
        String requestBody = String.format(
                "{\"filter\":{\"date\":{\"from\":\"%s\",\"to\":\"%s\"},\"operation_type\":[],\"posting_number\":\"%s\",\"transaction_type\":\"%s\"},\"page\":1,\"page_size\":1000}",
                fromDate, toDate, postingNumber, transactionType
        );
        logger.debug("⚡ Отправка запроса в Ozon...");
        logger.debug("Запрос транзакций: {}", requestBody);
        try {
            String response = getWebClientForUser()
                    .post()
                    .uri("/v3/finance/transaction/list")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(10));
            logger.debug("Ответ транзакций: {}", response);
            Thread.sleep(500);
            return response;
        } catch (Exception e) {
            logger.error("Error in getTransactionList: {}", e.getMessage());
            return "Ошибка запроса транзакций: " + e.getMessage();
        }
    }

    public String getFboPostings(String since, String to, String status) {
        String requestBody = String.format(
                "{\"dir\":\"ASC\",\"filter\":{\"since\":\"%s\",\"to\":\"%s\",\"status\":\"%s\"},\"limit\":100,\"offset\":0,\"translit\":true,\"with\":{\"analytics_data\":true,\"financial_data\":true}}",
                since, to, status
        );
        try {
            String response = getWebClientForUser()
                    .post()
                    .uri("/v2/posting/fbo/list")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(10));
            Thread.sleep(500);
            return response;
        } catch (Exception e) {
            logger.error("Error in getFboPostings: {}", e.getMessage());
            return "Ошибка получения списка отправлений FBO: " + e.getMessage();
        }
    }

    public String getFbsPostings(String since, String to, String status) {
        String requestBody = String.format(
                "{\"dir\":\"ASC\",\"filter\":{\"since\":\"%s\",\"to\":\"%s\",\"status\":\"%s\"},\"limit\":100,\"offset\":0,\"translit\":true,\"with\":{\"analytics_data\":true,\"financial_data\":true}}",
                since, to, status
        );
        try {
            String response = getWebClientForUser()
                    .post()
                    .uri("/v2/posting/fbs/list")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(10));
            Thread.sleep(500);
            return response;
        } catch (Exception e) {
            logger.error("Error in getFbsPostings: {}", e.getMessage());
            return "Ошибка получения списка отправлений FBS: " + e.getMessage();
        }
    }
}

//class OzonTransactionRequest {
//    private final DateRange date;
//    private final String posting_number;
//    private final String transaction_type;
//
//    public OzonTransactionRequest(String from, String to, String postingNumber, String transactionType) {
//        this.date = new DateRange(from, to);
//        this.posting_number = postingNumber;
//        this.transaction_type = transactionType;
//    }
//
//    static class DateRange {
//        private final String from;
//        private final String to;
//
//        public DateRange(String from, String to) {
//            this.from = from;
//            this.to = to;
//        }
//    }
//}
