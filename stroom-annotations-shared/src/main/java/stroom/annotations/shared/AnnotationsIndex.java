package stroom.annotations.shared;

import stroom.entity.shared.DocumentEntity;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;

@Entity
@Table(name = "ANNOTATIONS_INDEX")
public class AnnotationsIndex extends DocumentEntity {
    public static final String ENTITY_TYPE = "AnnotationsIndex";

    @Transient
    @Override
    public String getType() {
        return ENTITY_TYPE;
    }
}
