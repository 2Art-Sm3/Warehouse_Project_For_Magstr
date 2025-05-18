package ru.smirnov.warehouse.component.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.smirnov.warehouse.component.entity.Component;
import ru.smirnov.warehouse.component.entity.Part;
import ru.smirnov.warehouse.component.entity.SubAssembly;

import java.util.List;

public interface ComponentRepository extends JpaRepository<Component, Long> {

    List<Part> findByComponentType(String type); // Для получения только деталей (type = "PART")

    List<SubAssembly> findByComponentTypeIn(List<String> types); // Для получения подсборок (type = "SUBASSEMBLY")
}
