package stroom.core.receive;

import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.CleanupConfig;
import stroom.proxy.repo.RepoConfig;
import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.inject.Singleton;

@Singleton
@JsonPropertyOrder({
        "repoDir",
        "dbDir",
        "aggregator",
        "cleanup"
})
public class ProxyAggregationConfig extends AbstractConfig implements RepoConfig {

    private String repoDir;
    private String dbDir;
    private AggregatorConfig aggregatorConfig = new AggregatorConfig();
    private CleanupConfig cleanupConfig = new CleanupConfig();

    @Override
    @JsonProperty
    public String getRepoDir() {
        return repoDir;
    }

    public void setRepoDir(final String repoDir) {
        this.repoDir = repoDir;
    }

    @Override
    @JsonProperty
    public String getDbDir() {
        return dbDir;
    }

    public void setDbDir(final String dbDir) {
        this.dbDir = dbDir;
    }

    @JsonProperty("aggregator")
    public AggregatorConfig getAggregatorConfig() {
        return aggregatorConfig;
    }

    public void setAggregatorConfig(final AggregatorConfig aggregatorConfig) {
        this.aggregatorConfig = aggregatorConfig;
    }

    @JsonProperty("cleanup")
    public CleanupConfig getCleanupConfig() {
        return cleanupConfig;
    }

    public void setCleanupConfig(final CleanupConfig cleanupConfig) {
        this.cleanupConfig = cleanupConfig;
    }
}
