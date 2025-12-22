/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.config.global.impl;

import stroom.config.global.shared.ConfigProperty;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.PropertyPath;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

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

        // Make sure we have a row in the table
        dao.ensureTracker(UNKNOWN_UPDATE_TIME_MS);

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
