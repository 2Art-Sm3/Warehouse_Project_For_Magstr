package ru.smirnov.warehouse.product.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.smirnov.warehouse.product.entity.Product;
import ru.smirnov.warehouse.product.service.ProductService;

import java.util.List;

@Controller
@RequestMapping("/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    @GetMapping("/manage")
    public String showProductManagement(Model model) {

        List<Product> products = productService.getAllProducts();
        model.addAttribute("products", products);
        return "product";
    }

    @PostMapping("/update-stock/{id}")
    public String updateStock(@PathVariable Long id, @RequestParam(value = "quantityInStock", required = false) Integer quantityInStock) {
        Product product = productService.getProductById(id);
        if (product != null) {
            logger.info("Received update-stock request for product ID: {}, quantityInStock: {}", id, quantityInStock);
            if (quantityInStock != null) {
                product.setQuantityInStock(quantityInStock);
                logger.info("Saving new quantityInStock: {} for product ID: {}", quantityInStock, id);
            } else {
                logger.warn("quantityInStock is null for product ID: {}, setting to 0", id);
                product.setQuantityInStock(0);
            }
            Product savedProduct = productService.saveProduct(product);
            logger.info("Saved product ID: {}, new quantityInStock: {}", id, savedProduct.getQuantityInStock());
        } else {
            logger.error("Product with ID {} not found", id);
        }
        return "redirect:/products/manage";
    }

    @PostMapping("/create-assembly/{id}")
    public String createAssembly(@PathVariable Long id, @RequestParam("componentName") String componentName,
                                 @RequestParam("quantity") Integer quantity) {
        // Логика создания сборки (нужна сущность ProductAssembly и сервис)
        Product product = productService.getProductById(id);
        if (product != null) {
            // Здесь должна быть реализация добавления компонента (например, через ProductAssemblyService)
            product.setHasAssembly(true); // Флаг для отображения кнопки "Смотреть сборку"
            productService.saveProduct(product);
        }
        return "redirect:/products/manage";
    }

    @GetMapping("/view-assembly/{id}")
    public String viewAssembly(@PathVariable Long id, Model model) {
        Product product = productService.getProductById(id);
        if (product != null && product.getHasAssembly()) {
            // Логика загрузки и отображения сборки (нужна страница assembly.html)
            model.addAttribute("product", product);
            return "assembly"; // Предполагаемая страница для отображения иерархии
        }
        return "redirect:/products/manage";
    }

    @GetMapping("/refresh-from-ozon")
    public String refreshProductsFromOzon() {
        productService.syncProductsWithOzon();
        return "redirect:/products/manage";
    }
}
