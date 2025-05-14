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

@Controller
public class DashboardController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OzonService ozonService;

    @GetMapping("/dashboard")
    public String showDashboard(Model model) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден"));

        model.addAttribute("ozonClientId", user.getOzonClientId());
        model.addAttribute("ozonApiKey", user.getOzonApiKey());

        String ozonApiStatus = user.getOzonClientId() != null && user.getOzonApiKey() != null
                ? ozonService.testOzonApiConnection().block()
                : "Ключи не настроены";
        model.addAttribute("ozonApiStatus", ozonApiStatus);

        model.addAttribute("totalItems", 150);
        model.addAttribute("lowStockItems", 5);
        model.addAttribute("totalRevenue", 120000);
        model.addAttribute("averageMargin", 25);

        return "dashboard";
    }

    @PostMapping("/dashboard/update-ozon-keys")
    public String updateOzonKeys(
            @RequestParam("clientId") String clientId,
            @RequestParam("apiKey") String apiKey) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден"));

        user.setOzonClientId(clientId);
        user.setOzonApiKey(apiKey);
        userRepository.save(user);

        return "redirect:/dashboard";
    }
}
