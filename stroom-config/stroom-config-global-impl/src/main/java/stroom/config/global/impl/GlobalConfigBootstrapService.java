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

    // This two must be different as they are effectively compared below
    // This is the initial value of our in memory variable
    private static final long UNINITIALISED_UPDATE_TIME_MS = -2;
    // This value is used when the record doesn't exit
    private static final long UNKNOWN_UPDATE_TIME_MS = -1;

    private volatile long lastConfigUpdateTimeMs = UNINITIALISED_UPDATE_TIME_MS;

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

    synchronized void updateConfigFromDb(final boolean deleteUnknownProps) {
        // This method gets called every minute and on every refresh of the props screen
        // on all nodes.
        // Refreshing all the props is quite involved, especially if there are a lot of props
        // with DB values so only do the update if something
        // has changed by checking the tracker table. The tracker table must be updated whenever
        // there is a change to the config table. The DAO should ensure this.

        final long latestConfigUpdateTimeMs = dao.getLatestConfigUpdateTimeMs()
                .orElse(UNKNOWN_UPDATE_TIME_MS);

        if (lastConfigUpdateTimeMs != latestConfigUpdateTimeMs) {
            final List<ConfigProperty> validDbProps = getValidProperties(deleteUnknownProps);
            configMapper.decorateAllDbConfigProperties(validDbProps);
            LOGGER.info("Completed updated of application config with global database properties");
            lastConfigUpdateTimeMs = latestConfigUpdateTimeMs;
        } else {
            LOGGER.debug("Application config is unchanged no update required " +
                            "(lastConfigUpdateTimeMs {}, latestConfigUpdateTimeMs {}",
                    lastConfigUpdateTimeMs,
                    latestConfigUpdateTimeMs);
        }
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
