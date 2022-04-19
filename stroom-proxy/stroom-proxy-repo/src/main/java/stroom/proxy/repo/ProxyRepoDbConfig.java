package stroom.proxy.repo;

import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.shared.IsProxyConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder(alphabetic = true)
public class ProxyRepoDbConfig extends AbstractRepoDbConfig implements IsProxyConfig {

    private static final List<String> DEFAULT_MAINTENANCE_PRAGMA = List.of(
            "pragma wal_checkpoint(truncate);",
            "pragma vacuum;",
            "pragma optimize;");

//    "pragma incremental_vacuum;""

    private static final StroomDuration DEFAULT_MAINTENANCE_PRAGMA_FREQUENCY = StroomDuration.ofMinutes(1);

    private final List<String> maintenancePragma;
    private final StroomDuration maintenancePragmaFrequency;

    public ProxyRepoDbConfig() {
        this.maintenancePragma = DEFAULT_MAINTENANCE_PRAGMA;
        this.maintenancePragmaFrequency = DEFAULT_MAINTENANCE_PRAGMA_FREQUENCY;
    }

    @JsonCreator
    public ProxyRepoDbConfig(
            @JsonProperty("dbDir") final String dbDir,
            @JsonProperty("globalPragma") final List<String> globalPragma,
            @JsonProperty("connectionPragma") final List<String> connectionPragma,
            @JsonProperty("maintenancePragma") final List<String> maintenancePragma,
            @JsonProperty("maintenancePragmaFrequency") final StroomDuration maintenancePragmaFrequency,
            @JsonProperty("batchSize") final int batchSize) {
        super(dbDir, globalPragma, connectionPragma, batchSize);
        this.maintenancePragma = maintenancePragma;
        this.maintenancePragmaFrequency = maintenancePragmaFrequency;
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

    @RequiresRestart(value = RestartScope.SYSTEM)
    @JsonProperty
    @JsonPropertyDescription("A list of statements to run on the database according to the provided frequency")
    public List<String> getMaintenancePragma() {
        return maintenancePragma;
    }

    @RequiresRestart(value = RestartScope.SYSTEM)
    @JsonProperty
    @JsonPropertyDescription("A frequency that maintenance statements should be executed")
    public StroomDuration getMaintenancePragmaFrequency() {
        return maintenancePragmaFrequency;
    }
}
