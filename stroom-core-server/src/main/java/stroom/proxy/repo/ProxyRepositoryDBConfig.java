package stroom.proxy.repo;

import stroom.util.config.StroomProperties;

public class ProxyRepositoryDBConfig implements ProxyRepositoryConfig {
    /**
     * Optional Repository DIR. If set any incoming request will be written to the file system.
     */
    @Override
    public String getRepoDir() {
        return StroomProperties.getProperty("stroom.proxy.store.dir");
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
    @Override
    public String getRepositoryFormat() {
        return StroomProperties.getProperty("stroom.proxy.store.format", false);
    }


    /**
     * Interval to roll any writing repositories.
     */
    @Override
    public String getRollCron() {
        return StroomProperties.getProperty("stroom.proxy.store.rollCron");
    }
}
