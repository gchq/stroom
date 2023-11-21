package stroom.proxy.repo;

import stroom.util.config.annotations.RequiresProxyRestart;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Min;

import java.util.List;
import java.util.Objects;

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
    private static final int DEFAULT_BATCH_SIZE = 1_000_000;
    protected static final StroomDuration DEFAULT_FLUSH_FREQUENCY = StroomDuration.ofSeconds(1);
    protected static final StroomDuration DEFAULT_CLEANUP_FREQUENCY = StroomDuration.ofSeconds(1);

    private final String dbDir;
    private final String libraryDir;
    private final List<String> globalPragma;
    private final List<String> connectionPragma;
    private final List<String> maintenancePragma;
    private final StroomDuration maintenancePragmaFrequency;
    private final int batchSize;

    private final StroomDuration flushFrequency;
    private final StroomDuration cleanupFrequency;

    public ProxyDbConfig() {
        dbDir = "db";
        libraryDir = "sqlite_library";
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
            @JsonProperty("libraryDir") final String libraryDir,
            @JsonProperty("globalPragma") final List<String> globalPragma,
            @JsonProperty("connectionPragma") final List<String> connectionPragma,
            @JsonProperty("maintenancePragma") final List<String> maintenancePragma,
            @JsonProperty("maintenancePragmaFrequency") final StroomDuration maintenancePragmaFrequency,
            @JsonProperty("batchSize") final int batchSize,
            @JsonProperty("flushFrequency") final StroomDuration flushFrequency,
            @JsonProperty("cleanupFrequency") final StroomDuration cleanupFrequency) {
        this.dbDir = dbDir;
        this.libraryDir = libraryDir;
        this.globalPragma = List.copyOf(globalPragma);
        this.connectionPragma = List.copyOf(connectionPragma);
        this.maintenancePragma = maintenancePragma;
        this.maintenancePragmaFrequency = maintenancePragmaFrequency;
        this.batchSize = batchSize;
        this.flushFrequency = flushFrequency;
        this.cleanupFrequency = cleanupFrequency;
    }

    @RequiresProxyRestart
    @JsonProperty
    @JsonPropertyDescription("The directory to use for the proxy repository DB")
    public String getDbDir() {
        return dbDir;
    }

    @RequiresProxyRestart
    @JsonProperty
    @JsonPropertyDescription("The directory to use for the proxy repository DB Sqlite library files")
    public String getLibraryDir() {
        return libraryDir;
    }

    @RequiresProxyRestart
    @JsonProperty
    @JsonPropertyDescription("A list of statements to run on the database on startup")
    public List<String> getGlobalPragma() {
        return globalPragma;
    }

    @RequiresProxyRestart
    @JsonProperty
    @JsonPropertyDescription("A list of statements to run on the database for each connection")
    public List<String> getConnectionPragma() {
        return connectionPragma;
    }

    @RequiresProxyRestart
    @JsonProperty
    @JsonPropertyDescription("A list of statements to run on the database according to the provided frequency")
    public List<String> getMaintenancePragma() {
        return maintenancePragma;
    }

    @RequiresProxyRestart
    @JsonProperty
    @JsonPropertyDescription("A frequency that maintenance statements should be executed")
    public StroomDuration getMaintenancePragmaFrequency() {
        return maintenancePragmaFrequency;
    }

    // No idea why it needs to be >=10.  66 put this condition in when he wrote it and now can't remember why
    @Min(10)
    @RequiresProxyRestart
    @JsonProperty
    @JsonPropertyDescription("Choose the batch processing size. Batch size must be at least 10.")
    public int getBatchSize() {
        return batchSize;
    }

    @RequiresProxyRestart
    @JsonProperty
    public StroomDuration getFlushFrequency() {
        return flushFrequency;
    }

    @RequiresProxyRestart
    @JsonProperty
    public StroomDuration getCleanupFrequency() {
        return cleanupFrequency;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ProxyDbConfig that = (ProxyDbConfig) o;
        return batchSize == that.batchSize
                && Objects.equals(dbDir, that.dbDir)
                && Objects.equals(libraryDir, that.libraryDir)
                && Objects.equals(globalPragma, that.globalPragma)
                && Objects.equals(connectionPragma, that.connectionPragma)
                && Objects.equals(maintenancePragma, that.maintenancePragma)
                && Objects.equals(maintenancePragmaFrequency, that.maintenancePragmaFrequency)
                && Objects.equals(flushFrequency, that.flushFrequency)
                && Objects.equals(cleanupFrequency, that.cleanupFrequency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                dbDir,
                libraryDir,
                globalPragma,
                connectionPragma,
                maintenancePragma,
                maintenancePragmaFrequency,
                batchSize,
                flushFrequency,
                cleanupFrequency);
    }

    @Override
    public String toString() {
        return "ProxyDbConfig{" +
                "dbDir='" + dbDir + '\'' +
                ", libraryDir=" + libraryDir +
                ", globalPragma=" + globalPragma +
                ", connectionPragma=" + connectionPragma +
                ", maintenancePragma=" + maintenancePragma +
                ", maintenancePragmaFrequency=" + maintenancePragmaFrequency +
                ", batchSize=" + batchSize +
                ", flushFrequency=" + flushFrequency +
                ", cleanupFrequency=" + cleanupFrequency +
                '}';
    }
}
