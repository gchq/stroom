package stroom.core.receive;

import stroom.proxy.repo.AbstractRepoDbConfig;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public class ProxyAggregationRepoDbConfig extends AbstractRepoDbConfig implements IsStroomConfig {

    public ProxyAggregationRepoDbConfig() {
        super();
    }

    public ProxyAggregationRepoDbConfig(final String dbDir) {
        super(dbDir);
    }

    @JsonCreator
    public ProxyAggregationRepoDbConfig(@JsonProperty("dbDir") final String dbDir,
                                        @JsonProperty("globalPragma") final List<String> globalPragma,
                                        @JsonProperty("connectionPragma") final List<String> connectionPragma) {
        super(dbDir, globalPragma, connectionPragma);
    }

    @Override
    @RequiresRestart(value = RestartScope.SYSTEM)
    @JsonProperty
    @JsonPropertyDescription("The directory to use for the proxy repository DB")
    public String getDbDir() {
        return super.getDbDir();
    }

    @Override
    @RequiresRestart(value = RestartScope.SYSTEM)
    @JsonProperty
    @JsonPropertyDescription("A list of statements to run on the database on startup")
    public List<String> getGlobalPragma() {
        return super.getGlobalPragma();
    }

    @Override
    @RequiresRestart(value = RestartScope.SYSTEM)
    @JsonProperty
    @JsonPropertyDescription("A list of statements to run on the database for each connection")
    public List<String> getConnectionPragma() {
        return super.getConnectionPragma();
    }
}
