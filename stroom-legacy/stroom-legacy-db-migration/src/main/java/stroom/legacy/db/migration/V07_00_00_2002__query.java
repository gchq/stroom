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

package stroom.legacy.db.migration;

import stroom.dashboard.shared.StoredQuery;
import stroom.legacy.impex_6_1.MappingUtil;
import stroom.query.api.v2.Query;
import stroom.storedquery.impl.db.StoredQuerySerialiser;
import stroom.util.xml.XMLMarshallerUtil;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXBContext;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Deprecated
public class V07_00_00_2002__query extends BaseJavaMigration {
    private static final Logger LOGGER = LoggerFactory.getLogger(V07_00_00_2002__query.class);

    @Override
    public void migrate(final Context context) throws Exception {
        final JAXBContext jaxbContext = JAXBContext.newInstance(Query.class);

        try (final PreparedStatement preparedStatement = context.getConnection().prepareStatement(
                "SELECT " +
                        "  id, " +
                        "  data " +
                        "FROM query")) {
            try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    try {
                        final int id = resultSet.getInt(1);
                        final String data = resultSet.getString(2);

                        if (data != null) {
                            final stroom.legacy.model_6_1.Query query = XMLMarshallerUtil.unmarshal(jaxbContext, stroom.legacy.model_6_1.Query.class, data);
                            final stroom.query.api.v2.Query mapped = MappingUtil.map(query);

                            final StoredQuery storedQuery = new StoredQuery();
                            storedQuery.setQuery(mapped);
                            StoredQuerySerialiser.serialise(storedQuery);
                            final String newData = storedQuery.getData();

                            // Update the record.
                            try (final PreparedStatement ps = context.getConnection().prepareStatement(
                                    "UPDATE query SET " +
                                            "  data = ? " +
                                            "WHERE id = ?")) {
                                ps.setString(1, newData);
                                ps.setInt(2, id);
                                ps.executeUpdate();
                            } catch (final SQLException e) {
                                throw new RuntimeException(e.getMessage(), e);
                            }
                        }
                    } catch (final RuntimeException e) {
                        LOGGER.error(e.getMessage(), e);
                    }
                }
            }
        }
    }
}
