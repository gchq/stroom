package stroom.proxy.repo;

import stroom.util.config.annotations.RequiresProxyRestart;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.validation.ValidDirectoryPath;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.validation.ValidationMethod;

import java.util.Objects;


@JsonPropertyOrder(alphabetic = true)
public class ProxyRepoConfig extends AbstractConfig implements IsProxyConfig {

    public static final String PROP_NAME_STORING_ENABLED = "storingEnabled";
    public static final String PROP_NAME_REPO_DIR = "repoDir";

    protected static final boolean DEFAULT_STORING_ENABLED = true;
    protected static final String DEFAULT_REPO_DIR = "repo";

    private final boolean storingEnabled;
    private final String repoDir;

    public ProxyRepoConfig() {
        storingEnabled = DEFAULT_STORING_ENABLED;
        repoDir = DEFAULT_REPO_DIR;
    }

    @JsonCreator
    public ProxyRepoConfig(@JsonProperty(PROP_NAME_STORING_ENABLED) final boolean storingEnabled,
                           @JsonProperty(PROP_NAME_REPO_DIR) final String repoDir) {
        this.storingEnabled = storingEnabled;
        this.repoDir = repoDir;
    }

    @RequiresProxyRestart
    @JsonProperty
    public boolean isStoringEnabled() {
        return storingEnabled;
    }

    @RequiresProxyRestart
    @ValidDirectoryPath(ensureExistence = true)
    @JsonProperty
    public String getRepoDir() {
        return repoDir;
    }

    @JsonIgnore
    @SuppressWarnings("unused")
    @ValidationMethod(message = "If storingEnabled is true, repoDir must be set.")
    public boolean isRepoConfigValid() {
        return !storingEnabled
                || (repoDir != null && !repoDir.isEmpty());
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ProxyRepoConfig that = (ProxyRepoConfig) o;
        return storingEnabled == that.storingEnabled && Objects.equals(repoDir, that.repoDir);
    }

    @Override
    public int hashCode() {
        return Objects.hash(storingEnabled, repoDir);
    }

    @Override
    public String toString() {
        return "ProxyRepoConfig{" +
                "storingEnabled=" + storingEnabled +
                ", repoDir='" + repoDir + '\'' +
                '}';
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    public static class Builder {

        private boolean storingEnabled = DEFAULT_STORING_ENABLED;
        private String repoDir = DEFAULT_REPO_DIR;


        private Builder() {
        }

        private Builder(final ProxyRepoConfig config) {
            this.storingEnabled = config.storingEnabled;
            this.repoDir = config.repoDir;
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
