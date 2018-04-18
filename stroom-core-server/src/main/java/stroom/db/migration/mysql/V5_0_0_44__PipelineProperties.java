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

package stroom.db.migration.mysql;

import org.flywaydb.core.api.migration.jdbc.JdbcMigration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.util.XMLMarshallerUtil;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineProperty;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

public class V5_0_0_44__PipelineProperties implements JdbcMigration {
    private static final Logger LOGGER = LoggerFactory.getLogger(V5_0_0_44__PipelineProperties.class);

    private final JAXBContext jaxbContext;

    public V5_0_0_44__PipelineProperties() {
        try {
            jaxbContext = JAXBContext.newInstance(PipelineData.class);
        } catch (final JAXBException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public void migrate(final Connection connection) throws Exception {
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
                        final PipelineData object = XMLMarshallerUtil.unmarshal(jaxbContext, PipelineData.class, data);
                        if (object != null) {
                            modifyProperties(object.getAddedProperties());
                            modifyProperties(object.getRemovedProperties());
                            final String newData = XMLMarshallerUtil.marshal(jaxbContext, XMLMarshallerUtil.removeEmptyCollections(object));

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
}
