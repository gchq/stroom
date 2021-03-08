package stroom.core.receive;

import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.RepoConfig;
import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.inject.Singleton;

@Singleton
@JsonPropertyOrder({
        "repoDir",
        "dbDir",
        "aggregator"
})
public class ProxyAggregationConfig extends AbstractConfig implements RepoConfig {

    private String repoDir;
    private String dbDir;
    private AggregatorConfig aggregatorConfig = new AggregatorConfig();

    @Override
    @JsonProperty
    @JsonPropertyDescription("Tee location of a proxy repository that we want to aggregate data from")
    public String getRepoDir() {
        return repoDir;
    }

    public void setRepoDir(final String repoDir) {
        this.repoDir = repoDir;
    }

    @Override
    @JsonProperty
    @JsonPropertyDescription("The location of the local SQLite DB to use for the proxy aggregation process")
    public String getDbDir() {
        return dbDir;
    }

    public void setDbDir(final String dbDir) {
        this.dbDir = dbDir;
    }

    @JsonProperty("aggregator")
    @JsonPropertyDescription("Settings for aggregation")
    public AggregatorConfig getAggregatorConfig() {
        return aggregatorConfig;
    }

    public void setAggregatorConfig(final AggregatorConfig aggregatorConfig) {
        this.aggregatorConfig = aggregatorConfig;
    }
}
