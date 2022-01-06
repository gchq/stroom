package stroom.proxy.repo;

import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder(alphabetic = true)
public class ProxyRepoDbConfig extends AbstractRepoDbConfig implements IsProxyConfig {

    public ProxyRepoDbConfig() {
        super();
    }

    @JsonCreator
    public ProxyRepoDbConfig(@JsonProperty("dbDir") final String dbDir,
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
