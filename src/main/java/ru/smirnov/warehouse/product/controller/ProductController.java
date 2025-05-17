package ru.smirnov.warehouse.product.controller;

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

    @GetMapping("/manage")
    public String showProductManagement(Model model) {
        List<Product> products = productService.getAllProducts();
        model.addAttribute("products", products);
        return "product";
    }

    @GetMapping("/add")
    public String showAddProductForm(Model model) {
        model.addAttribute("product", new Product());
        return "product-add";
    }

    @PostMapping("/add")
    public String addProduct(@ModelAttribute Product product) {
        productService.saveProduct(product);
        return "redirect:/products/manage";
    }

    @GetMapping("/edit/{id}")
    public String showEditProductForm(@PathVariable Long id, Model model) {
        Product product = productService.getProductById(id);
        model.addAttribute("product", product);
        return "product-edit";
    }

    @PostMapping("/edit/{id}")
    public String editProduct(@PathVariable Long id, @ModelAttribute Product product) {
        product.setId(id);
        productService.saveProduct(product);
        return "redirect:/products/manage";
    }

    @GetMapping("/delete/{id}")
    public String deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return "redirect:/products/manage";
    }

    @GetMapping("/refresh-from-ozon")
    public String refreshProductsFromOzon() {
        productService.syncProductsWithOzon();
        return "redirect:/products/manage";
    }
}
