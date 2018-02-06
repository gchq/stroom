package stroom.elastic.server;

import stroom.query.audit.service.DocRefEntity;

/**
 * As loaded in from remote service
 */
public class ElasticIndexDocRefEntity extends DocRefEntity {

    private String indexName;

    private String indexedType;

    private String mappingsJson;

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(final String value) {
        this.indexName = value;
    }

    public String getIndexedType() {
        return indexedType;
    }

    public void setIndexedType(final String value) {
        this.indexedType = value;
    }

    /**
     +     * This will operate as a read only field, if the user wants to modify the index itself, it
     +     * is best done through Kibana
     +     * @return The current state of the mappings in JSON
     +     */
    public String getMappingsJson() {
        return mappingsJson;
    }

    public void setMappingsJson(String mappingsJson) {
        this.mappingsJson = mappingsJson;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ElasticIndexDocRefEntity{");
        sb.append("super='").append(super.toString()).append('\'');
        sb.append(", indexName='").append(indexName).append('\'');
        sb.append(", indexedType='").append(indexedType).append('\'');
        sb.append(", mappingsJson='").append(mappingsJson).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
