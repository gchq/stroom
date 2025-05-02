package stroom.gitrepo.impl;

import stroom.gitrepo.api.GitRepoConfig;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NotInjectableConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotNull;

@JsonPropertyOrder(alphabetic = true)
@NotInjectableConfig
public class GitRepoConfigImpl extends AbstractConfig implements GitRepoConfig, IsStroomConfig {
    /**
     * Default location where local GitRepos are stored
     */
    static final String DEFAULT_LOCAL_DIR = "git_repo";

    private final String localDir;

    /**
     * Default constructor. Configuration created with default values.
     */
    public GitRepoConfigImpl() {
        localDir = DEFAULT_LOCAL_DIR;
    }

    /**
     * Constructor called when creating configuration from JSON or YAML.
     * @param localDir The local git repository relative path.
     */
    @JsonCreator
    public GitRepoConfigImpl(@JsonProperty("localDir") final String localDir) {
        this.localDir = localDir;
    }

    @Override
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
    public String toString() {
        return "GitRepoConfig { localDir='" + localDir + "'}";
    }

}
