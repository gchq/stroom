package stroom.proxy.repo;

import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Duration;
import javax.inject.Singleton;

@Singleton
@JsonPropertyOrder({
        "storingEnabled",
        "repoDir",
        "dbDir",
        "format",
        "cleanupFrequency",
        "lockDeleteAge",
        "dirCleanDelay"
})
public class ProxyRepoConfig implements RepoConfig {

    private boolean storingEnabled = false;
    private String repoDir = "${stroom.home}/repo";
    private String dbDir = "${stroom.home}/repo";
    private String format = "${pathId}/${id}";
    private StroomDuration cleanupFrequency = StroomDuration.of(Duration.ofHours(1));
    private StroomDuration lockDeleteAge = StroomDuration.of(Duration.ofHours(1));
    private StroomDuration dirCleanDelay = StroomDuration.of(Duration.ofSeconds(10));

    @JsonProperty
    public boolean isStoringEnabled() {
        return storingEnabled;
    }

    public void setStoringEnabled(final boolean storingEnabled) {
        this.storingEnabled = storingEnabled;
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

    public void setRepoDir(final String repoDir) {
        this.repoDir = repoDir;
    }

    @RequiresRestart(value = RestartScope.SYSTEM)
    @JsonProperty
    @Override
    public String getDbDir() {
        return dbDir;
    }

    public void setDbDir(final String dbDir) {
        this.dbDir = dbDir;
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

    public void setFormat(final String format) {
        this.format = format;
    }

    @JsonProperty
    public StroomDuration getCleanupFrequency() {
        return cleanupFrequency;
    }

    public void setCleanupFrequency(final StroomDuration cleanupFrequency) {
        this.cleanupFrequency = cleanupFrequency;
    }

    @JsonProperty
    public StroomDuration getLockDeleteAge() {
        return lockDeleteAge;
    }

    public void setLockDeleteAge(final StroomDuration lockDeleteAge) {
        this.lockDeleteAge = lockDeleteAge;
    }

    @JsonProperty
    public StroomDuration getDirCleanDelay() {
        return dirCleanDelay;
    }

    public void setDirCleanDelay(final StroomDuration dirCleanDelay) {
        this.dirCleanDelay = dirCleanDelay;
    }
}
