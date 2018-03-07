/*
 * Copyright 2016 Crown Copyright
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

package stroom.properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.SQLNameConstants;
import stroom.entity.util.ConnectionUtil;
import stroom.node.shared.GlobalProperty;
import stroom.util.config.StroomProperties;

import javax.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

@Singleton
public class GlobalProperties {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalProperties.class);

    private static final String LOAD_DB_PROPERTIES_SQL = "SELECT " + SQLNameConstants.NAME + ", " + SQLNameConstants.VALUE + " FROM " + GlobalProperty.TABLE_NAME;

    private static GlobalProperties instance;
    private final Map<String, GlobalProperty> globalProperties = new HashMap<>();

    GlobalProperties() {
        if (instance == null) {
            loadDefaultProperties();
            loadDBProperties();
        }
        instance = this;
    }

    /**
     * This method exists so that tests and other classes that are not able to
     * inject this object using Spring can get the current instance or create
     * it. It is recommended that most code injects GlobalProperties instead.
     *
     * @return Either the current instance of GlobalProperties or a new instance
     * if one has not previously been constructed.
     */
    public static GlobalProperties getInstance() {
        if (instance == null) {
            new GlobalProperties();
        }
        return instance;
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
                    StroomProperties.setProperty(globalProperty.getName(), globalProperty.getValue(), StroomProperties.Source.SPRING);
                }
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    private void loadDBProperties() {
        try (final Connection connection = ConnectionUtil.getConnection()) {
            // Overwrite some properties from values in the DB.
            if (ConnectionUtil.tableExists(connection, GlobalProperty.TABLE_NAME)) {
                LOGGER.debug(">>> %s", LOAD_DB_PROPERTIES_SQL);
                try (final PreparedStatement preparedStatement = connection.prepareStatement(LOAD_DB_PROPERTIES_SQL)) {
                    try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                        while (resultSet.next()) {
                            final String name = resultSet.getString(1);
                            final String value = resultSet.getString(2);

                            if (value != null) {
                                final GlobalProperty globalProperty = globalProperties.get(name);
                                if (globalProperty != null) {
                                    globalProperty.setValue(value);
                                    globalProperty.setSource("Database");
                                    StroomProperties.setProperty(name, value, StroomProperties.Source.DB);
                                }
                            }
                        }
                    }
                } catch (final SQLException e) {
                    LOGGER.error(e.getMessage(), e);
                    throw e;
                }
            }
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    public GlobalProperty getGlobalProperty(final String name) {
        return globalProperties.get(name);
    }

    public Map<String, GlobalProperty> getGlobalProperties() {
        return globalProperties;
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
