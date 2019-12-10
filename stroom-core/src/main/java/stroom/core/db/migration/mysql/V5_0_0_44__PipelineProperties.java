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
 */

package stroom.core.db.migration.mysql;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.query.api.v2.Query;
import stroom.util.xml.XMLMarshallerUtil;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

public class V5_0_0_44__PipelineProperties extends BaseJavaMigration {
    private static final Logger LOGGER = LoggerFactory.getLogger(V5_0_0_44__PipelineProperties.class);
    private JAXBContext jaxbContext;

    @Override
    public void migrate(final Context flywayContext) throws Exception {
        migrate(flywayContext.getConnection());
    }

    private void migrate(final Connection connection) throws Exception {
        try (final Statement statement = connection.createStatement()) {
            try (final ResultSet resultSet = statement.executeQuery("SELECT ID, NAME, DAT FROM PIPE;")) {
                while (resultSet.next()) {
                    final long id = resultSet.getLong(1);
                    final String name = resultSet.getString(2);
                    final String data = resultSet.getString(3);

                    LOGGER.info("Starting pipeline upgrade: " + name);

                    if (data == null) {
                        LOGGER.info("Incomplete configuration found");

                    } else {
                        final PipelineData object = XMLMarshallerUtil.unmarshal(getContext(), PipelineData.class, data);
                        if (object != null) {
                            modifyProperties(object.getAddedProperties());
                            modifyProperties(object.getRemovedProperties());
                            final String newData = XMLMarshallerUtil.marshal(getContext(), XMLMarshallerUtil.removeEmptyCollections(object));

                            if (!newData.equals(data)) {
                                LOGGER.info("Modifying pipeline");

                                try (final PreparedStatement preparedStatement = connection.prepareStatement("UPDATE PIPE SET DAT = ? WHERE ID = ?")) {
                                    preparedStatement.setString(1, newData);
                                    preparedStatement.setLong(2, id);
                                    preparedStatement.executeUpdate();
                                }
                            } else {
                                LOGGER.info("No change required");
                            }
                        }
                    }

                    LOGGER.info("Finished pipeline upgrade: " + name);
                }
            }
        }
    }

    private void modifyProperties(final List<PipelineProperty> properties) {
        if (properties != null && properties.size() > 0) {
            properties.removeIf(property -> "usePool".equalsIgnoreCase(property.getName()));
        }
    }

    private JAXBContext getContext() {
        if (jaxbContext == null) {
            try {
                jaxbContext = JAXBContext.newInstance(PipelineData.class);
            } catch (final JAXBException e) {
                LOGGER.error(e.getMessage(), e);
                throw new RuntimeException(e.getMessage());
            }
        }

        return jaxbContext;
    }
}
