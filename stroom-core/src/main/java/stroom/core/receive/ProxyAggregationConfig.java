package stroom.core.receive;

import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.RepoConfig;
import stroom.proxy.repo.RepoDbConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.inject.Singleton;

@Singleton
@JsonPropertyOrder(alphabetic = true)
public class ProxyAggregationConfig extends AbstractConfig implements IsStroomConfig, RepoConfig {

    private String repoDir = "proxy_repo";
    private AggregatorConfig aggregatorConfig = new AggregatorConfig();
    private RepoDbConfig repoDbConfig = new RepoDbConfig();

    public ProxyAggregationConfig() {
        repoDbConfig.setDbDir("proxy_repo_db");
    }

    @Override
    @JsonProperty
    @JsonPropertyDescription("The location of a proxy repository that we want to aggregate data from")
    public String getRepoDir() {
        return repoDir;
    }

    public void setRepoDir(final String repoDir) {
        this.repoDir = repoDir;
    }

    @JsonProperty
    @JsonPropertyDescription("The location of the local SQLite DB to use for the proxy aggregation process")
    public RepoDbConfig getRepoDbConfig() {
        return repoDbConfig;
    }

    public void setRepoDbConfig(final RepoDbConfig repoDbConfig) {
        this.repoDbConfig = repoDbConfig;
    }

    @JsonProperty("aggregator")
    @JsonPropertyDescription("Settings for aggregation")
    public AggregatorConfig getAggregatorConfig() {
        return aggregatorConfig;
    }

    public void setAggregatorConfig(final AggregatorConfig aggregatorConfig) {
        this.aggregatorConfig = aggregatorConfig;
    }

    @Override
    public String toString() {
        return "ProxyAggregationConfig{" +
                "repoDir='" + repoDir + '\'' +
                ", aggregatorConfig=" + aggregatorConfig +
                '}';
    }
}
