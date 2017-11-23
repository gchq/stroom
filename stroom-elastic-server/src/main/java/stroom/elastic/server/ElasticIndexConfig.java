package stroom.elastic.server;

/**
 * As loaded in from remote service
 */
public class ElasticIndexConfig {
    private String uuid;

    private String indexName;

    private String indexedType;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

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

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ElasticIndexConfig{");
        sb.append("uuid='").append(uuid).append('\'');
        sb.append(", indexName='").append(indexName).append('\'');
        sb.append(", indexedType='").append(indexedType).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
