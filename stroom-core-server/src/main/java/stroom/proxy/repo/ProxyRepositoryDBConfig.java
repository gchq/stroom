package stroom.proxy.repo;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class ProxyRepositoryDBConfig implements ProxyRepositoryConfig {
    private String dir = "${stroom.temp}/stroom-proxy";
    private String format = "#{'$'}{pathId}/#{'$'}{id}";
    private String rollCron = "";

    /**
     * Optional Repository DIR. If set any incoming request will be written to the file system.
     */
    @JsonPropertyDescription("The stroom proxy dir to write data to from a pipeline")
    @Override
    public String getDir() {
        return dir;
    }

    public void setDir(final String dir) {
        this.dir = dir;
    }

    /**
     * Optionally supply a template for naming the files in the repository. This can be specified using multiple replacement variables.
     * The standard template is '${pathId}/${id}' and will be used if this property is not set.
     * This pattern will produce the following paths for the following identities:
     * \t1 = 001.zip
     * \t100 = 100.zip
     * \t1000 = 001/001000.zip
     * \t10000 = 010/010000.zip
     * \t100000 = 100/100000.zip
     * Other replacement variables can be used to in the template including header meta data parameters (e.g. '${feed}') and time based parameters (e.g. '${year}').
     * Replacement variables that cannot be resolved will be output as '_'.
     * Please ensure that all templates include the '${id}' replacement variable at the start of the file name, failure to do this will result in an invalid repository.
     */
    @JsonPropertyDescription("The format to use for the stroom proxy store")
    @Override
    public String getFormat() {
        return format;
    }

    public void setFormat(final String format) {
        this.format = format;
    }

    /**
     * Interval to roll any writing repositories.
     */
    @JsonPropertyDescription("How often should the stroom proxy store be rolled")
    @Override
    public String getRollCron() {
        return rollCron;
    }

    public void setRollCron(final String rollCron) {
        this.rollCron = rollCron;
    }
}
