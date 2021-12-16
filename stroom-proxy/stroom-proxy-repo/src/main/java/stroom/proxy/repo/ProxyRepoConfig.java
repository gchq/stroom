package stroom.proxy.repo;

import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class ProxyRepoConfig extends AbstractConfig implements IsProxyConfig, RepoConfig {

    private final boolean storingEnabled;
    private final String repoDir;
    private final String format;
    private final StroomDuration cleanupFrequency;
    private final StroomDuration lockDeleteAge;
    private final StroomDuration dirCleanDelay;

    public ProxyRepoConfig() {
        storingEnabled = false;
        repoDir = "repo";
        format = "${pathId}/${id}";
        cleanupFrequency = StroomDuration.ofHours(1);
        lockDeleteAge = StroomDuration.ofHours(1);
        dirCleanDelay = StroomDuration.ofSeconds(10);
    }

    @JsonCreator
    public ProxyRepoConfig(@JsonProperty("storingEnabled") final boolean storingEnabled,
                                 @JsonProperty("repoDir") final String repoDir,
                                 @JsonProperty("format") final String format,
                                 @JsonProperty("cleanupFrequency") final StroomDuration cleanupFrequency,
                                 @JsonProperty("lockDeleteAge") final StroomDuration lockDeleteAge,
                                 @JsonProperty("dirCleanDelay") final StroomDuration dirCleanDelay) {
        this.storingEnabled = storingEnabled;
        this.repoDir = repoDir;
        this.format = format;
        this.cleanupFrequency = cleanupFrequency;
        this.lockDeleteAge = lockDeleteAge;
        this.dirCleanDelay = dirCleanDelay;
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
    @Override
    public String getRepoDir() {
        return repoDir;
    }

    /**
     * Optionally supply a template for naming the files in the repository. This can be specified using multiple
     * replacement variables.
     * The standard template is '${pathId}/${id}' and will be used if this property is not set.
     * This pattern will produce the following paths for the following identities:
     * \t1 = 001.zip
     * \t100 = 100.zip
     * \t1000 = 001/001000.zip
     * \t10000 = 010/010000.zip
     * \t100000 = 100/100000.zip
     * Other replacement variables can be used to in the template including header meta data parameters
     * (e.g. '${feed}') and time based parameters (e.g. '${year}').
     * Replacement variables that cannot be resolved will be output as '_'.
     * Please ensure that all templates include the '${id}' replacement variable at the start of the file name,
     * failure to do this will result in an invalid repository.
     */
    @JsonProperty
    public String getFormat() {
        return format;
    }

    @JsonProperty
    public StroomDuration getCleanupFrequency() {
        return cleanupFrequency;
    }

    @JsonProperty
    public StroomDuration getLockDeleteAge() {
        return lockDeleteAge;
    }

    @JsonProperty
    public StroomDuration getDirCleanDelay() {
        return dirCleanDelay;
    }

    public ProxyRepoConfig withRepoDir(final String repoDir) {
        return new ProxyRepoConfig(storingEnabled, repoDir, format, cleanupFrequency, lockDeleteAge, dirCleanDelay);
    }

    public ProxyRepoConfig withStoringEnabled(final boolean storingEnabled) {
        return new ProxyRepoConfig(storingEnabled, repoDir, format, cleanupFrequency, lockDeleteAge, dirCleanDelay);
    }
}
