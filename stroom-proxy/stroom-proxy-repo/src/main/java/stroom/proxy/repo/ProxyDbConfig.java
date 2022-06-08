package stroom.proxy.repo;

import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder(alphabetic = true)
public class ProxyDbConfig extends AbstractConfig implements IsProxyConfig {

    private static final List<String> DEFAULT_GLOBAL_PRAGMA = List.of(
            "pragma journal_mode = WAL;"
//            "pragma auto_vacuum = incremental;"
    );
    private static final List<String> DEFAULT_CONNECTION_PRAGMA = List.of(
            "pragma synchronous = normal;",
            "pragma temp_store = memory;",
            "pragma mmap_size = 30000000000;");
//            "pragma busy_timeout = 600000;"

    private static final List<String> DEFAULT_MAINTENANCE_PRAGMA = List.of(
            "pragma wal_checkpoint(truncate);",
            "pragma vacuum;",
            "pragma optimize;");

//    "pragma incremental_vacuum;""

    private static final StroomDuration DEFAULT_MAINTENANCE_PRAGMA_FREQUENCY = StroomDuration.ofMinutes(1);
    private static final int DEFAULT_BATCH_SIZE = 1_0000_000;
    protected static final StroomDuration DEFAULT_FLUSH_FREQUENCY = StroomDuration.ofSeconds(1);
    protected static final StroomDuration DEFAULT_CLEANUP_FREQUENCY = StroomDuration.ofSeconds(1);

    private final String dbDir;
    private final List<String> globalPragma;
    private final List<String> connectionPragma;
    private final List<String> maintenancePragma;
    private final StroomDuration maintenancePragmaFrequency;
    private final int batchSize;

    private final StroomDuration flushFrequency;
    private final StroomDuration cleanupFrequency;

    public ProxyDbConfig() {
        dbDir = "db";
        globalPragma = DEFAULT_GLOBAL_PRAGMA;
        connectionPragma = DEFAULT_CONNECTION_PRAGMA;
        maintenancePragma = DEFAULT_MAINTENANCE_PRAGMA;
        maintenancePragmaFrequency = DEFAULT_MAINTENANCE_PRAGMA_FREQUENCY;
        batchSize = DEFAULT_BATCH_SIZE;
        flushFrequency = DEFAULT_FLUSH_FREQUENCY;
        cleanupFrequency = DEFAULT_CLEANUP_FREQUENCY;
    }

    @JsonCreator
    public ProxyDbConfig(
            @JsonProperty("dbDir") final String dbDir,
            @JsonProperty("globalPragma") final List<String> globalPragma,
            @JsonProperty("connectionPragma") final List<String> connectionPragma,
            @JsonProperty("maintenancePragma") final List<String> maintenancePragma,
            @JsonProperty("maintenancePragmaFrequency") final StroomDuration maintenancePragmaFrequency,
            @JsonProperty("batchSize") final int batchSize,
            @JsonProperty("flushFrequency") final StroomDuration flushFrequency,
            @JsonProperty("cleanupFrequency") final StroomDuration cleanupFrequency) {
        this.dbDir = dbDir;
        this.globalPragma = List.copyOf(globalPragma);
        this.connectionPragma = List.copyOf(connectionPragma);
        this.maintenancePragma = maintenancePragma;
        this.maintenancePragmaFrequency = maintenancePragmaFrequency;
        if (batchSize > 9) {
            this.batchSize = batchSize;
        } else {
            this.batchSize = DEFAULT_BATCH_SIZE;
        }
        this.flushFrequency = flushFrequency;
        this.cleanupFrequency = cleanupFrequency;
    }

    @RequiresRestart(value = RestartScope.SYSTEM)
    @JsonProperty
    @JsonPropertyDescription("The directory to use for the proxy repository DB")
    public String getDbDir() {
        return dbDir;
    }

    @RequiresRestart(value = RestartScope.SYSTEM)
    @JsonProperty
    @JsonPropertyDescription("A list of statements to run on the database on startup")
    public List<String> getGlobalPragma() {
        return globalPragma;
    }

    @RequiresRestart(value = RestartScope.SYSTEM)
    @JsonProperty
    @JsonPropertyDescription("A list of statements to run on the database for each connection")
    public List<String> getConnectionPragma() {
        return connectionPragma;
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

    @RequiresRestart(value = RestartScope.SYSTEM)
    @JsonProperty
    @JsonPropertyDescription("Choose the batch processing size")
    public int getBatchSize() {
        return batchSize;
    }

    @RequiresRestart(value = RestartScope.SYSTEM)
    @JsonProperty
    public StroomDuration getFlushFrequency() {
        return flushFrequency;
    }

    @RequiresRestart(value = RestartScope.SYSTEM)
    @JsonProperty
    public StroomDuration getCleanupFrequency() {
        return cleanupFrequency;
    }

}
