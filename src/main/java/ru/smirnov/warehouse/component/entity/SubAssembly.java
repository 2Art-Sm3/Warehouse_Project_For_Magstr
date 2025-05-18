package ru.smirnov.warehouse.component.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;
import ru.smirnov.warehouse.hierarchy.entity.SubAssemblyDetail;

import java.util.List;

@Entity
@DiscriminatorValue("SUBASSEMBLY")
@Getter
@Setter
public class SubAssembly extends Component {
    @OneToMany(mappedBy = "subAssembly", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SubAssemblyDetail> subAssemblyDetails; // Детали и подсборки внутри
}
