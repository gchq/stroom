package stroom.search.solr.shared;

import stroom.docref.HasDisplayValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"useZk", "instanceType", "solrUrls", "zkHosts", "zkPath"})
@JsonInclude(Include.NON_NULL)
@XmlRootElement(name = "connection")
@XmlType(name = "SolrConnectionConfig", propOrder = {"useZk", "instanceType", "solrUrls", "zkHosts", "zkPath"})
public class SolrConnectionConfig implements Serializable {

    @JsonProperty
    private InstanceType instanceType;
    @JsonProperty
    private boolean useZk;
    @JsonProperty
    private List<String> solrUrls;
    @JsonProperty
    private List<String> zkHosts;
    @JsonProperty
    private String zkPath;

    public SolrConnectionConfig() {
    }

    @JsonCreator
    public SolrConnectionConfig(@JsonProperty("instanceType") final InstanceType instanceType,
                                @JsonProperty("useZk") final boolean useZk,
                                @JsonProperty("solrUrls") final List<String> solrUrls,
                                @JsonProperty("zkHosts") final List<String> zkHosts,
                                @JsonProperty("zkPath") final String zkPath) {
        this.instanceType = instanceType;
        this.useZk = useZk;
        this.solrUrls = solrUrls;
        this.zkHosts = zkHosts;
        this.zkPath = zkPath;
    }

    public InstanceType getInstanceType() {
        return instanceType;
    }

    public void setInstanceType(final InstanceType instanceType) {
        this.instanceType = instanceType;
    }

    public boolean isUseZk() {
        return useZk;
    }

    public void setUseZk(final boolean useZk) {
        this.useZk = useZk;
    }

    public List<String> getSolrUrls() {
        return solrUrls;
    }

    public void setSolrUrls(final List<String> solrUrls) {
        this.solrUrls = solrUrls;
    }

    public List<String> getZkHosts() {
        return zkHosts;
    }

    public void setZkHosts(final List<String> zkHosts) {
        this.zkHosts = zkHosts;
    }

    public String getZkPath() {
        return zkPath;
    }

    public void setZkPath(final String zkPath) {
        this.zkPath = zkPath;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof final SolrConnectionConfig that)) {
            return false;
        }
        return useZk == that.useZk &&
               instanceType == that.instanceType &&
               Objects.equals(solrUrls, that.solrUrls) &&
               Objects.equals(zkHosts, that.zkHosts) &&
               Objects.equals(zkPath, that.zkPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instanceType, useZk, solrUrls, zkHosts, zkPath);
    }

    @Override
    public String toString() {
        return "SolrConnectionConfig{" +
               "instanceType=" + instanceType +
               ", useZk=" + useZk +
               ", solrUrls=" + solrUrls +
               ", zkHosts=" + zkHosts +
               ", zkPath='" + zkPath + '\'' +
               '}';
    }

    public enum InstanceType implements HasDisplayValue {
        SINGLE_NOOE("Single Node"),
        SOLR_CLOUD("Solr Cloud");

        private final String displayValue;

        InstanceType(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }
}
