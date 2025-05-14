package ru.smirnov.warehouse.common.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.smirnov.warehouse.common.entity.User;
import ru.smirnov.warehouse.common.repository.UserRepository;

@Service
public class OzonService {

    private UserRepository userRepository;

    public OzonService(UserRepository userRepository) {
        this.userRepository = userRepository;
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
        User user = userRepository.findByUsername(username)
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

    public Mono<String> testOzonApiConnection() {
        return getWebClientForUser()
                .post()
                .uri("/v1/description-category/tree")
                .bodyValue("{\"language\":\"DEFAULT\"}")
                .retrieve()
                .toBodilessEntity()
                .map(response -> "Успешно")
                .onErrorResume(e -> Mono.just("Ошибка подключения к Ozon API, проверьте корректность" +
                        " введеных данных"));
    }

    public Mono<String> syncProductData(String sku) {
        return getWebClientForUser()
                .get()
                .uri("/v2/product/info/stocks?sku=" + sku)
                .retrieve()
                .bodyToMono(String.class);
    }

    public Mono<String> getTransactionList(String fromDate, String toDate, String postingNumber, String transactionType) {
        return getWebClientForUser()
                .post()
                .uri("/v3/finance/transaction/list")
                .bodyValue(new OzonTransactionRequest(fromDate, toDate, postingNumber, transactionType))
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(e -> Mono.just("Ошибка запроса транзакций: " + e.getMessage()));
    }
}

class OzonTransactionRequest {
    private final DateRange date;
    private final String posting_number;
    private final String transaction_type;

    public OzonTransactionRequest(String from, String to, String postingNumber, String transactionType) {
        this.date = new DateRange(from, to);
        this.posting_number = postingNumber;
        this.transaction_type = transactionType;
    }

    static class DateRange {
        private final String from;
        private final String to;

        public DateRange(String from, String to) {
            this.from = from;
            this.to = to;
        }
    }
}
