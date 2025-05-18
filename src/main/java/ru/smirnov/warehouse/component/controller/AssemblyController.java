package ru.smirnov.warehouse.component.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/components")
public class AssemblyController {

    @GetMapping("/manage")
    public String showComponents(Model model) {
        // Логика загрузки компонентов
        return "components"; // Новый шаблон components.html
    }
}
