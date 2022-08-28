package stroom.proxy.repo;

import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class ProxyRepoConfig extends AbstractConfig implements IsProxyConfig {

    protected static final boolean DEFAULT_STORING_ENABLED = false;
    protected static final String DEFAULT_REPO_DIR = "repo";

    private final boolean storingEnabled;
    private final String repoDir;

    public ProxyRepoConfig() {
        storingEnabled = DEFAULT_STORING_ENABLED;
        repoDir = DEFAULT_REPO_DIR;
    }

    @JsonCreator
    public ProxyRepoConfig(@JsonProperty("storingEnabled") final boolean storingEnabled,
                           @JsonProperty("repoDir") final String repoDir) {
        this.storingEnabled = storingEnabled;
        this.repoDir = repoDir;
    }

    @JsonProperty
    public boolean isStoringEnabled() {
        return storingEnabled;
    }

    /**
     * Optional Repository DIR. If set any incoming request will be written to the file system.
     */
    @RequiresRestart(value = RestartScope.SYSTEM)
    @JsonProperty
    public String getRepoDir() {
        return repoDir;
    }

    public ProxyRepoConfig withRepoDir(final String repoDir) {
        return new ProxyRepoConfig(storingEnabled, repoDir);
    }

    public ProxyRepoConfig withStoringEnabled(final boolean storingEnabled) {
        return new ProxyRepoConfig(storingEnabled, repoDir);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private boolean storingEnabled = DEFAULT_STORING_ENABLED;
        private String repoDir = DEFAULT_REPO_DIR;


        private Builder() {
        }

        public Builder storingEnabled(final boolean storingEnabled) {
            this.storingEnabled = storingEnabled;
            return this;
        }

        public Builder repoDir(final String repoDir) {
            this.repoDir = repoDir;
            return this;
        }

        public ProxyRepoConfig build() {
            return new ProxyRepoConfig(storingEnabled, repoDir);
        }
    }
}
