package stroom.proxy.repo;

import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Singleton;

@Singleton
@JsonPropertyOrder(alphabetic = true)
public class RepoDbConfig extends AbstractConfig implements IsStroomConfig, IsProxyConfig {

    private String dbDir = "db";
    private List<String> globalPragma = new ArrayList<>();
    private List<String> connectionPragma = new ArrayList<>();

    public RepoDbConfig() {
        globalPragma.add("pragma journal_mode = WAL;");
        connectionPragma.add("pragma synchronous = normal;");
        connectionPragma.add("pragma temp_store = memory;");
        connectionPragma.add("pragma mmap_size = 30000000000;");
    }

    @RequiresRestart(value = RestartScope.SYSTEM)
    @JsonProperty
    @JsonPropertyDescription("The directory to use for the proxy repository DB")
    public String getDbDir() {
        return dbDir;
    }

    public void setDbDir(final String dbDir) {
        this.dbDir = dbDir;
    }

    @RequiresRestart(value = RestartScope.SYSTEM)
    @JsonProperty
    @JsonPropertyDescription("A list of statements to run on the database on startup")
    public List<String> getGlobalPragma() {
        return globalPragma;
    }

    public void setGlobalPragma(final List<String> globalPragma) {
        this.globalPragma = globalPragma;
    }

    @RequiresRestart(value = RestartScope.SYSTEM)
    @JsonProperty
    @JsonPropertyDescription("A list of statements to run on the database for each connection")
    public List<String> getConnectionPragma() {
        return connectionPragma;
    }

    public void setConnectionPragma(final List<String> connectionPragma) {
        this.connectionPragma = connectionPragma;
    }
}
