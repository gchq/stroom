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

    private static final List<ScheduledPragma> DEFAULT_SCHEDULED_PRAGMA = List.of(
//            ScheduledPragma.builder()
//                    .statement("pragma vacuum;")
//                    .frequency(StroomDuration.ofMinutes(1))
//                    .build(),
//            ScheduledPragma.builder()
//                    .statement("pragma optimize;")
//                    .frequency(StroomDuration.ofMinutes(1))
//                    .build(),
//            ScheduledPragma.builder()
//                    .statement("pragma wal_checkpoint(truncate);")
//                    .frequency(StroomDuration.ofMinutes(1))
//                    .build()
    );


    //            DBMaintenanceTask.builder().statement("pragma incremental_vacuum;").frequency(StroomDuration.ofMinutes(1)).build(),

    private final List<ScheduledPragma> scheduledPragma;

    public ProxyRepoDbConfig() {
        this.scheduledPragma = DEFAULT_SCHEDULED_PRAGMA;
    }

    @JsonCreator
    public ProxyRepoDbConfig(@JsonProperty("dbDir") final String dbDir,
                             @JsonProperty("globalPragma") final List<String> globalPragma,
                             @JsonProperty("connectionPragma") final List<String> connectionPragma,
                             @JsonProperty("scheduledPragma") final List<ScheduledPragma> scheduledPragma) {
        super(dbDir, globalPragma, connectionPragma);
        this.scheduledPragma = scheduledPragma;
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
    @JsonPropertyDescription("A list of statements to run on the database according to the provided schedule")
    public List<ScheduledPragma> getScheduledPragma() {
        return scheduledPragma;
    }
}
