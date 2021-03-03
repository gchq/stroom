package stroom.proxy.repo;

import stroom.config.common.DbConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Duration;
import javax.inject.Singleton;

@Singleton
@JsonPropertyOrder({
        "storingEnabled",
        "repoDir",
        "format",
        "db",
        "lockDeleteAge",
        "dirCleanDelay"
})
public class ProxyRepoConfig extends AbstractConfig implements HasDbConfig {

    private boolean storingEnabled = false;
    public static String repoDir;
    private String format = "${pathId}/${id}";
    private DbConfig dbConfig;
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
    public String getRepoDir() {
        return repoDir;
    }

    public void setRepoDir(final String repoDir) {
        ProxyRepoConfig.repoDir = repoDir;
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

    @JsonProperty("db")
    public DbConfig getDbConfig() {
//        if (dbConfig == null) {
//            Path path;
//            if (repoDir == null) {
//                throw new RuntimeException("No proxy repository dir has been defined");
//            } else {
//                path = Paths.get(repoDir);
//            }
//            if (!Files.isDirectory(path)) {
//                throw new RuntimeException("Unable to find repo dir: " + FileUtil.getCanonicalPath(path));
//            }
//
//            path = path.resolve("db");
//            FileUtil.mkdirs(path);
//            path = path.resolve("proxy-repo.db");
//
//            final String fullPath = FileUtil.getCanonicalPath(path);
//
//            final ConnectionConfig connectionConfig = new ConnectionConfig();
//            connectionConfig.setClassName("org.sqlite.JDBC");
//            connectionConfig.setUrl("jdbc:sqlite:" + fullPath);
////            connectionConfig.setUser("sa");
////            connectionConfig.setPassword("sa");
//
//            dbConfig = new DbConfig();
//            dbConfig.setConnectionConfig(connectionConfig);
//        }

        return dbConfig;
    }

    public void setDbConfig(final DbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }
}
