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

    private String repoDir = "proxy_repo";
    private String dbDir = "proxy_repo_db";
    private AggregatorConfig aggregatorConfig = new AggregatorConfig();

    @Override
    @JsonProperty
    @JsonPropertyDescription("The location of a proxy repository that we want to aggregate data from. " +
            "Typically this directory " +
            "will belong to the stroom-proxy that is populating the repository in it. If the value is a " +
            "relative path then it will be treated as being relative to stroom.path.home.")
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
