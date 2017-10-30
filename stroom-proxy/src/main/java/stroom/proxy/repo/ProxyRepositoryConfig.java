package stroom.proxy.repo;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ProxyRepositoryConfig {
    private String repoDir;
    private String repositoryFormat = "${pathId}/${id}";
    private String simpleCron;

    /**
     * Optional Repository DIR. If set any incoming request will be written to the file system.
     */
    @JsonProperty
    public String getRepoDir() {
        return repoDir;
    }

    @JsonProperty
    public void setRepoDir(final String repoDir) {
        this.repoDir = repoDir;
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
    @JsonProperty
    public String getRepositoryFormat() {
        return repositoryFormat;
    }

    @JsonProperty
    public void setRepositoryFormat(final String repositoryFormat) {
        this.repositoryFormat = repositoryFormat;
    }

    /**
     * Interval to roll any writing repositories.
     */
    @JsonProperty
    public String getSimpleCron() {
        return simpleCron;
    }

    @JsonProperty
    public void setSimpleCron(final String simpleCron) {
        this.simpleCron = simpleCron;
    }


}
