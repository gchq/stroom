package stroom.elastic.shared;

import stroom.entity.shared.DocumentEntity;
import stroom.explorer.shared.ExplorerConstants;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;

@Entity
@Table(name = "ELASTIC_INDEX")
public class ElasticIndex extends DocumentEntity {
    public static final String ENTITY_TYPE = ExplorerConstants.ELASTIC_SEARCH;

    @Transient
    @Override
    public String getType() {
        return ENTITY_TYPE;
    }
}
