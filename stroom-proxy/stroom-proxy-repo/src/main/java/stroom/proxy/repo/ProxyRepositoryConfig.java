package stroom.proxy.repo;

import stroom.config.common.ConnectionConfig;
import stroom.config.common.DbConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.io.FileUtil;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.inject.Singleton;

@Singleton
public class ProxyRepositoryConfig implements HasDbConfig {

    private boolean isStoringEnabled = false;
    private String repoDir;
    private String format = "${pathId}/${id}";
    private String rollCron;
    private DbConfig dbConfig;

    @JsonProperty
    public boolean isStoringEnabled() {
        return isStoringEnabled;
    }

    public void setStoringEnabled(final boolean storingEnabled) {
        isStoringEnabled = storingEnabled;
    }

    /**
     * Optional Repository DIR. If set any incoming request will be written to the file system.
     */
    @JsonProperty
    public String getDir() {
        return repoDir;
    }

    @JsonProperty
    public void setRepoDir(final String repoDir) {
        this.repoDir = repoDir;
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
    public void setFormat(final String format) {
        this.format = format;
    }

    /**
     * Interval to roll any writing repositories.
     */
    @JsonProperty
    public String getRollCron() {
        return rollCron;
    }

    @JsonProperty
    public void setRollCron(final String rollCron) {
        this.rollCron = rollCron;
    }

    @JsonProperty("db")
    public DbConfig getDbConfig() {
        if (dbConfig == null) {
            Path path;
            if (repoDir == null) {
                path = Paths.get("");
            } else {
                path = Paths.get(repoDir);
            }
            if (!Files.isDirectory(path)) {
                throw new RuntimeException("Unable to find repo dir: " + FileUtil.getCanonicalPath(path));
            }

            path = path.resolve("db");
            FileUtil.mkdirs(path);
            path = path.resolve("proxy-repo");

            final String fullPath = FileUtil.getCanonicalPath(path);

            final ConnectionConfig connectionConfig = new ConnectionConfig();
            connectionConfig.setClassName("org.h2.Driver");
            connectionConfig.setUrl("jdbc:h2:file:" + fullPath);
            connectionConfig.setUser("sa");
            connectionConfig.setPassword("sa");

            dbConfig = new DbConfig();
            dbConfig.setConnectionConfig(connectionConfig);
        }

        return dbConfig;
    }

    public void setDbConfig(final DbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }
}
