package stroom.elastic.server;

import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.SQLNameConstants;
import stroom.explorer.shared.ExplorerConstants;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;

@Entity
@Table(name = "ELASTIC_INDEX")
public class ElasticIndex extends DocumentEntity {
    public static final String ENTITY_TYPE = ExplorerConstants.ELASTIC_SEARCH;

    private String indexName;

    private String indexedType;

    @Column(name = SQLNameConstants.INDEX_NAME)
    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    @Column(name = SQLNameConstants.INDEXED_TYPE)
    public String getIndexedType() {
        return indexedType;
    }

    public void setIndexedType(String indexedType) {
        this.indexedType = indexedType;
    }

    @Transient
    @Override
    public String getType() {
        return ENTITY_TYPE;
    }
}
