package ru.smirnov.warehouse.component.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Entity
@DiscriminatorValue("PART")
@Getter
@Setter
public class Part extends Component {
    // Деталь, которая хранится на складе
}
