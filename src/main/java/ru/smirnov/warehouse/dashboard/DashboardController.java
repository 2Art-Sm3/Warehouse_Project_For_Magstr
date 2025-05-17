package ru.smirnov.warehouse.dashboard;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.smirnov.warehouse.common.entity.User;
import ru.smirnov.warehouse.common.repository.UserRepository;
import ru.smirnov.warehouse.common.service.OzonService;
import ru.smirnov.warehouse.common.service.UserService;
import ru.smirnov.warehouse.product.service.ProductService;

@Controller
public class DashboardController {

    @Autowired
    private UserService userService;

    @Autowired
    private OzonService ozonService;
    @Autowired
    private ProductService productService;

    @GetMapping("/dashboard")
    public String showDashboard(Model model) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден"));

        model.addAttribute("ozonClientId", user.getOzonClientId());
        model.addAttribute("ozonApiKey", user.getOzonApiKey());

        String ozonApiStatus = user.getOzonClientId() != null && user.getOzonApiKey() != null
                ? ozonService.testOzonApiConnection()
                : "Ключи не настроены";
        model.addAttribute("ozonApiStatus", ozonApiStatus);

        model.addAttribute("products", productService.getAllProducts());
        model.addAttribute("lowStockItems", 0);
        model.addAttribute("totalRevenue", 0);
        model.addAttribute("averageCommission", 0);

        return "dashboard";
    }

    @PostMapping("/dashboard/update-ozon-keys")
    public String updateOzonKeys(
            @RequestParam("clientId") String clientId,
            @RequestParam("apiKey") String apiKey) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден"));

        user.setOzonClientId(clientId);
        user.setOzonApiKey(apiKey);
        userService.save(user);

        ozonService.resetWebClient();

        return "redirect:/dashboard";
    }

    /*
    // Пример метода для подсчёта товаров с низким запасом (требуется реализация логики)
    private int calculateLowStockItems() {
        return (int) productService.getAllProducts().stream()
                .filter(p -> p.getQuantityForSale() != null && p.getQuantityForSale() < 10)
                .count();
    }

    // Пример метода для расчёта общей выручки (требуется интеграция с транзакциями)
    private double calculateTotalRevenue() {
        return productService.getAllProducts().stream()
                .filter(p -> p.getPrice() != null && p.getSoldQuantity() != null)
                .mapToDouble(p -> p.getPrice() * p.getSoldQuantity())
                .sum();
    }

    // Пример метода для расчёта средней комиссии (требуется интеграция с Ozon)
    private double calculateAverageCommission() {
        return productService.getAllProducts().stream()
                .filter(p -> p.getOzonCommissions() != null && p.getPrice() != null && p.getPrice() > 0)
                .mapToDouble(p -> (p.getOzonCommissions() / p.getPrice()) * 100)
                .average()
                .orElse(0.0);
    }
    */
}
