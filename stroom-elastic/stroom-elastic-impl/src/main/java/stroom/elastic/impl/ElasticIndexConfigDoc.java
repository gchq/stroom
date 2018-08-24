package stroom.elastic.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docstore.shared.Doc;
import stroom.query.audit.model.DocRefEntity;

/**
 * As loaded in from remote service
 */
@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "description", "elasticHttpUrl", "indexName", "indexedType", "mappingsJson"})
@JsonInclude(Include.NON_EMPTY)
public class ElasticIndexConfigDoc extends DocRefEntity {
    public static final String DOCUMENT_TYPE = "ElasticIndex";

    private String description;
    private String elasticHttpUrl;
//    private String clusterName;
//    private String transportHosts;
    private String indexName;
    private String indexedType;
    private String mappingsJson;

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public String getElasticHttpUrl() {
        return elasticHttpUrl;
    }

    public void setElasticHttpUrl(final String elasticHttpUrl) {
        this.elasticHttpUrl = elasticHttpUrl;
    }
//
//    public String getClusterName() {
//        return clusterName;
//    }
//
//    public void setClusterName(final String clusterName) {
//        this.clusterName = clusterName;
//    }
//
//    public String getTransportHosts() {
//        return transportHosts;
//    }
//
//    public void setTransportHosts(final String transportHosts) {
//        this.transportHosts = transportHosts;
//    }

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
     * +     * This will operate as a read only field, if the user wants to modify the index itself, it
     * +     * is best done through Kibana
     * +     * @return The current state of the mappings in JSON
     * +
     */
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
