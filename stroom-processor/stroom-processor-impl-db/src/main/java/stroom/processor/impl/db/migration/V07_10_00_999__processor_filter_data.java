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

package stroom.processor.impl.db.migration;

import stroom.processor.impl.db.migration.legacyqd.LegacyQueryDataXMLSerialiser;
import stroom.processor.impl.db.migration.legacyqd.QueryData;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class V07_10_00_999__processor_filter_data extends BaseJavaMigration {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory
            .getLogger(V07_10_00_999__processor_filter_data.class);

    @Override
    public void migrate(final Context context) throws Exception {
        final Map<Long, String> updateMap = new HashMap<>();

        // Detect any activities that we can't find users for.
        final LegacyQueryDataXMLSerialiser serialiser = new LegacyQueryDataXMLSerialiser();
        try (final PreparedStatement preparedStatement = context.getConnection().prepareStatement(
                """
                        SELECT id, data
                        FROM processor_filter;
                        """)) {
            try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    try {
                        final Long id = resultSet.getLong(1);
                        final String xml = resultSet.getString(2);
                        if (NullSafe.isNonBlankString(xml) && xml.startsWith("<")) {
                            final QueryData queryData = serialiser.deserialise(xml);
                            final String json = JsonUtil.writeValueAsString(queryData);
                            updateMap.put(id, json);
                        }
                    } catch (final RuntimeException e) {
                        LOGGER.error(e.getMessage(), e);
                    }
                }
            }
        }

        try (final PreparedStatement preparedStatement = context.getConnection().prepareStatement(
                """
                        UPDATE processor_filter SET data = ? WHERE id = ?;
                        """)) {
            for (final Entry<Long, String> entry : updateMap.entrySet()) {
                preparedStatement.setString(1, entry.getValue());
                preparedStatement.setLong(2, entry.getKey());
                preparedStatement.execute();
            }
        }
    }
}
