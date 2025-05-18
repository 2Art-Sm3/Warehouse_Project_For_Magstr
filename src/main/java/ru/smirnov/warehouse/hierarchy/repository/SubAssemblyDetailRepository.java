package ru.smirnov.warehouse.hierarchy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.smirnov.warehouse.component.entity.SubAssembly;
import ru.smirnov.warehouse.hierarchy.entity.SubAssemblyDetail;

import java.util.List;

public interface SubAssemblyDetailRepository extends JpaRepository<SubAssemblyDetail, Long> {

    List<SubAssemblyDetail> findBySubAssembly(SubAssembly subAssembly);
}
