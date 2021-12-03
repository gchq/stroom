package stroom.proxy.repo;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.validation.ValidSimpleCron;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class ProxyRepositoryConfig extends AbstractConfig implements IsProxyConfig {

    private final boolean isStoringEnabled;
    private final String repoDir;
    private final String format;
    private final String rollCron;

    public ProxyRepositoryConfig() {
        isStoringEnabled = false;
        repoDir = null;
        format = "${pathId}/${id}";
        rollCron = null;
    }

    @JsonCreator
    public ProxyRepositoryConfig(@JsonProperty("storingEnabled") final boolean isStoringEnabled,
                                 @JsonProperty("repoDir") final String repoDir,
                                 @JsonProperty("format") final String format,
                                 @JsonProperty("rollCron") final String rollCron) {
        this.isStoringEnabled = isStoringEnabled;
        this.repoDir = repoDir;
        this.format = format;
        this.rollCron = rollCron;
    }

    @JsonProperty
    public boolean isStoringEnabled() {
        return isStoringEnabled;
    }

    /**
     * Optional Repository DIR. If set any incoming request will be written to the file system.
     */
    @JsonProperty
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

    /**
     * Interval to roll any writing repositories.
     */
    @ValidSimpleCron
    @JsonProperty
    public String getRollCron() {
        return rollCron;
    }

    public ProxyRepositoryConfig withRepoDir(final String repoDir) {
        return new ProxyRepositoryConfig(isStoringEnabled, repoDir, format, rollCron);
    }

    public ProxyRepositoryConfig withStoringEnabled(final boolean isStoringEnabled) {
        return new ProxyRepositoryConfig(isStoringEnabled, repoDir, format, rollCron);
    }
}
