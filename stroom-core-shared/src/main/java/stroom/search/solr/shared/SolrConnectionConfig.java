package stroom.search.solr.shared;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.util.shared.HasDisplayValue;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

@XmlAccessorType(XmlAccessType.FIELD)
@JsonPropertyOrder({"useZk", "instanceType", "solrUrls", "zkHosts", "zkPath"})
@XmlRootElement(name = "connection")
@XmlType(name = "SolrConnectionConfig", propOrder = {"useZk", "instanceType", "solrUrls", "zkHosts", "zkPath"})
public class SolrConnectionConfig implements Serializable {
    private InstanceType instanceType = InstanceType.SINGLE_NOOE;
    private boolean useZk;
    private List<String> solrUrls;
    private List<String> zkHosts;
    private String zkPath;

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
        if (this == o) return true;
        if (!(o instanceof SolrConnectionConfig)) return false;
        final SolrConnectionConfig that = (SolrConnectionConfig) o;
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
