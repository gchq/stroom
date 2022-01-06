package stroom.config.global.impl;

import stroom.config.global.shared.ConfigProperty;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.PropertyPath;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton // Needs to be singleton to prevent initialise being called multiple times
public class GlobalConfigBootstrapService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GlobalConfigBootstrapService.class);

    private final ConfigPropertyDao dao;
    private final ConfigMapper configMapper;

    @SuppressWarnings("unused")
    @Inject
    public GlobalConfigBootstrapService(final ConfigPropertyDao dao,
                                        final ConfigMapper configMapper) {
        this.dao = dao;
        this.configMapper = configMapper;

        LOGGER.debug("Initialising GlobalConfigService");
        updateConfigFromDb(true);
        LOGGER.info("Config initialised with all effective values");
    }

    void updateConfigFromDb(final boolean deleteUnknownProps) {
        final List<ConfigProperty> validDbProps = getValidProperties(deleteUnknownProps);
        configMapper.decorateAllDbConfigProperties(validDbProps);
        LOGGER.info("Updated application config with global database properties");
    }

    private List<ConfigProperty> getValidProperties(final boolean deleteUnknownProps) {
        // Get all props held in the DB, which may be a subset of those in the config
        // object model

        final List<ConfigProperty> allDbProps = dao.list();
        final List<ConfigProperty> validDbProps = new ArrayList<>(allDbProps.size());

        allDbProps.forEach(dbConfigProp -> {
            if (dbConfigProp.getName() == null || !configMapper.validatePropertyPath(dbConfigProp.getName())) {
                LOGGER.debug("Property {} is in the database but not in the appConfig model",
                        dbConfigProp.getName());
                if (deleteUnknownProps) {
                    deleteFromDb(dbConfigProp.getName());
                }
            } else {
                validDbProps.add(dbConfigProp);
            }
        });

        return validDbProps;
    }

    private void deleteFromDb(final PropertyPath name) {
        LOGGER.warn(() ->
                LogUtil.message("Deleting property {} as it is not valid in the object model", name));
        dao.delete(name);
    }
}
