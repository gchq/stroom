package stroom.core.receive;

import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.RepoConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class ProxyAggregationConfig extends AbstractConfig implements IsStroomConfig, RepoConfig {

    private final String repoDir;
    private final AggregatorConfig aggregatorConfig;
    private final ProxyAggregationRepoDbConfig repoDbConfig;

    public ProxyAggregationConfig() {
        repoDbConfig = new ProxyAggregationRepoDbConfig("proxi_repo_db");
        repoDir = "proxy_repo";
        aggregatorConfig = new AggregatorConfig();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ProxyAggregationConfig(@JsonProperty("repoDir") final String repoDir,
                                  @JsonProperty("aggregator") final AggregatorConfig aggregatorConfig,
                                  @JsonProperty("repoDb") final ProxyAggregationRepoDbConfig repoDbConfig) {
        this.repoDir = repoDir;
        this.aggregatorConfig = aggregatorConfig;
        this.repoDbConfig = repoDbConfig;
    }

    @Override
    @JsonProperty
    @JsonPropertyDescription("The location of a proxy repository that we want to aggregate data from")
    public String getRepoDir() {
        return repoDir;
    }

    @JsonProperty("repoDb")
    @JsonPropertyDescription("The location of the local SQLite DB to use for the proxy aggregation process")
    public ProxyAggregationRepoDbConfig getRepoDbConfig() {
        return repoDbConfig;
    }

    @JsonProperty("aggregator")
    @JsonPropertyDescription("Settings for aggregation")
    public AggregatorConfig getAggregatorConfig() {
        return aggregatorConfig;
    }

    @Override
    public String toString() {
        return "ProxyAggregationConfig{" +
                "repoDir='" + repoDir + '\'' +
                ", aggregatorConfig=" + aggregatorConfig +
                '}';
    }
}
