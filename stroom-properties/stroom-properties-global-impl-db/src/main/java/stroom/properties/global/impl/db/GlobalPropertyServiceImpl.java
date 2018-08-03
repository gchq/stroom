/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.properties.global.impl.db;


import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.properties.global.api.GlobalProperty;
import stroom.security.Security;
import stroom.security.shared.PermissionNames;
import stroom.util.config.StroomProperties;
import stroom.util.lifecycle.JobTrackedSchedule;
import stroom.util.lifecycle.StroomFrequencySchedule;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static stroom.properties.impl.db.stroom.tables.Property.PROPERTY;

@Singleton
class GlobalPropertyServiceImpl implements GlobalPropertyService {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalPropertyServiceImpl.class);

    private final ConnectionProvider connectionProvider;
    private final Security security;
    private final Map<String, GlobalProperty> globalProperties = new HashMap<>();

    @Inject
    GlobalPropertyServiceImpl(final ConnectionProvider connectionProvider,
                              final Security security) {
        this.connectionProvider = connectionProvider;
        this.security = security;
    }

    @Override
    public void initialise() {
        // Setup DB properties.
        LOGGER.info("Adding global properties to the DB");
        loadDefaultProperties();
        loadDBProperties();
    }

    @SuppressWarnings("resource")
    private void loadDefaultProperties() {
        try {
            final List<GlobalProperty> globalPropertyList = DefaultProperties.getList();
            for (final GlobalProperty globalProperty : globalPropertyList) {
                globalProperty.setSource("Default");
                globalProperty.setDefaultValue(globalProperty.getValue());
                globalProperties.put(globalProperty.getName(), globalProperty);

                if (globalProperty.getValue() != null) {
                    StroomProperties.setProperty(globalProperty.getName(), globalProperty.getValue(), StroomProperties.Source.GUICE);
                }
            }
        } catch (final RuntimeException e) {
            e.printStackTrace();
        }
    }

    private void loadDBProperties() {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            create
                    .selectFrom(PROPERTY)
                    .fetch()
                    .forEach(record -> {
                        if (record.getName() != null && record.getVal() != null) {
                            final GlobalProperty globalProperty = globalProperties.get(record.getName());
                            if (globalProperty != null) {
                                globalProperty.setId(record.getId());
                                globalProperty.setValue(record.getVal());
                                globalProperty.setSource("Database");
                                StroomProperties.setProperty(record.getName(), record.getVal(), StroomProperties.Source.DB);
                            }
                        }
                    });
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    /**
     * Refresh in background
     */
    @StroomFrequencySchedule("1m")
    @JobTrackedSchedule(jobName = "Property Cache Reload", description = "Reload properties in the cluster")
    public void update() {
        loadDBProperties();
    }

    public GlobalProperty getGlobalProperty(final String name) {
        return globalProperties.get(name);
    }

    public Map<String, GlobalProperty> getGlobalProperties() {
        return globalProperties;
    }

    @Override
    public List<GlobalProperty> list() {
        return security.secureResult(PermissionNames.MANAGE_PROPERTIES_PERMISSION, () -> {
            loadDBProperties();

            final List<GlobalProperty> list = new ArrayList<>(globalProperties.values());
            list.sort(Comparator.comparing(GlobalProperty::getName));

            return list;
        });
    }

    @Override
    public GlobalProperty load(final GlobalProperty globalProperty) {
        return security.secureResult(PermissionNames.MANAGE_PROPERTIES_PERMISSION, () -> {
            final GlobalProperty loaded = globalProperties.get(globalProperty.getName());

            try (final Connection connection = connectionProvider.getConnection()) {
                final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
                create
                        .selectFrom(PROPERTY)
                        .where(PROPERTY.ID.eq(globalProperty.getId()))
                        .fetchOptional()
                        .ifPresent(record -> {
                            if (record.getName() != null && record.getVal() != null) {
                                if (loaded != null) {
                                    loaded.setId(record.getId());
                                    loaded.setValue(record.getVal());
                                    loaded.setSource("Database");
                                }
                            }
                        });
            } catch (final SQLException e) {
                LOGGER.error(e.getMessage(), e);
            }

            return loaded;
        });
    }

    @Override
    public GlobalProperty save(final GlobalProperty globalProperty) {
        return security.secureResult(PermissionNames.MANAGE_PROPERTIES_PERMISSION, () -> {
            try (final Connection connection = connectionProvider.getConnection()) {
                final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
                create
                        .update(PROPERTY)
                        .set(PROPERTY.VAL, globalProperty.getValue())
                        .where(PROPERTY.NAME.eq(globalProperty.getName()));
                StroomProperties.setProperty(globalProperty.getName(), globalProperty.getValue(), StroomProperties.Source.DB);
            } catch (final SQLException e) {
                LOGGER.error(e.getMessage(), e);
            }

            return globalProperty;
        });
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (final Entry<String, GlobalProperty> entry : globalProperties.entrySet()) {
            sb.append(entry.getKey());
            sb.append("=");
            sb.append(entry.getValue());
            sb.append("\n");
        }
        return sb.toString();
    }
}
