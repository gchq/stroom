package stroom.elastic.server;

/**
 * As loaded in from remote service
 */
public class ElasticIndexConfig {
    private String uuid;

    private String stroomName;

    private String indexName;

    private String indexedType;

    private String mappingsJson;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getStroomName() {
        return stroomName;
    }

    public void setStroomName(String stroomName) {
        this.stroomName = stroomName;
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

    public String getMappingsJson() {
        return mappingsJson;
    }

    public void setMappingsJson(String mappingsJson) {
        this.mappingsJson = mappingsJson;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ElasticIndexConfig{");
        sb.append("uuid='").append(uuid).append('\'');
        sb.append(", stroomName='").append(stroomName).append('\'');
        sb.append(", indexName='").append(indexName).append('\'');
        sb.append(", indexedType='").append(indexedType).append('\'');
        sb.append(", mappingsJson='").append(mappingsJson).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
