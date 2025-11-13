package stroom.gitrepo.api;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.BootStrapConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotNull;

/**
 * Provides configuration for the GitRepo stuff on the server.
 */
@JsonPropertyOrder(alphabetic = true)
public class GitRepoConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {
    /**
     * Default location where local GitRepos are stored
     */
    static final String DEFAULT_LOCAL_DIR = "git_repo";

    /**
     * Where local GitRepos are stored.
     */
    private final String localDir;

    /**
     * Database config
     */
    private final GitRepoDbConfig dbConfig;

    /**
     * Default constructor. Configuration created with default values.
     */
    public GitRepoConfig() {
        localDir = DEFAULT_LOCAL_DIR;
        dbConfig = new GitRepoDbConfig();
    }

    /**
     * Constructor called when creating configuration from JSON or YAML.
     * @param localDir The local git repository relative path.
     */
    @SuppressWarnings("unused")
    @JsonCreator
    public GitRepoConfig(@JsonProperty("localDir") final String localDir,
                         @JsonProperty("db") final GitRepoDbConfig dbConfig) {
        this.localDir = localDir;
        this.dbConfig = dbConfig;
    }

    /**
     * @return Where to store local Git repos.
     */
    @NotNull
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("The path relative to the home directory to use "
            + "for storing local Git Repositories." +
            "The directory will be created if it doesn't exist." +
            "If the value is a relative path then it will be treated as being relative to stroom.path.home.")
    public String getLocalDir() {
        return localDir;
    }

    @Override
    @JsonProperty("db")
    public GitRepoDbConfig getDbConfig() {
        return dbConfig;
    }

    /**
     * @return debug info about this object.
     */
    @Override
    public String toString() {
        return "GitRepoConfig { localDir='" + localDir + "'}";
    }

    /**
     * DB configuration.
     */
    @BootStrapConfig
    public static class GitRepoDbConfig extends AbstractDbConfig {
        public GitRepoDbConfig() {
            super();
        }

        @JsonCreator
        @SuppressWarnings("unused")
        public GitRepoDbConfig(
                @JsonProperty(AbstractDbConfig.PROP_NAME_CONNECTION)
                final ConnectionConfig connectionConfig,
                @JsonProperty(AbstractDbConfig.PROP_NAME_CONNECTION_POOL)
                final ConnectionPoolConfig connectionPoolConfig) {
            super(connectionConfig, connectionPoolConfig);
        }

    }
}
