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

package stroom.db.migration.mysql;

import org.flywaydb.core.api.migration.jdbc.JdbcMigration;
import stroom.db.migration.OldDictionaryDoc;
import stroom.dictionary.DictionarySerialiser;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docstore.JsonSerialiser2;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class V6_2_0_2__Dictionary implements JdbcMigration {
    @Override
    public void migrate(final Connection connection) throws Exception {
        final JsonSerialiser2<OldDictionaryDoc> oldSerialiser = new JsonSerialiser2<>(OldDictionaryDoc.class);
        final DictionarySerialiser newSerialiser = new DictionarySerialiser();

        try (final PreparedStatement preparedStatement = connection.prepareStatement("SELECT id, type, uuid, name, extension, data FROM doc WHERE type = 'Dictionary'")) {
            try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    final long id = resultSet.getLong(1);
                    final String type = resultSet.getString(2);
                    final String uuid = resultSet.getString(3);
                    final String name = resultSet.getString(4);
                    final String extension = resultSet.getString(5);
                    final byte[] data = resultSet.getBytes(6);

                    // Deserialise the old dictionary document format.
                    final Map<String, byte[]> dataMap = new HashMap<>();
                    dataMap.put("meta", data);

                    final OldDictionaryDoc document = oldSerialiser.read(dataMap);

                    // Copy the values to the new dictionary document format.
                    final DictionaryDoc dictionaryDoc = new DictionaryDoc();
                    dictionaryDoc.setVersion(document.getVersion());
                    dictionaryDoc.setCreateTime(document.getCreateTime());
                    dictionaryDoc.setUpdateTime(document.getUpdateTime());
                    dictionaryDoc.setCreateUser(document.getCreateUser());
                    dictionaryDoc.setUpdateUser(document.getUpdateUser());
                    dictionaryDoc.setType(document.getType());
                    dictionaryDoc.setUuid(document.getUuid());
                    dictionaryDoc.setName(document.getName());
                    dictionaryDoc.setDescription(document.getDescription());
                    dictionaryDoc.setImports(document.getImports());
                    dictionaryDoc.setData(document.getData());

                    // Serialise the new document.
                    final DictionarySerialiser dictionarySerialiser = new DictionarySerialiser();
                    final Map<String, byte[]> newDataMap = newSerialiser.write(dictionaryDoc);

                    // Update the meta record.
                    final byte[] newData = newDataMap.remove("meta");
                    try (final PreparedStatement ps = connection.prepareStatement("UPDATE doc SET (type, uuid, name, extension, data) VALUES (?, ?, ?, ?, ?) WHERE id = ?")) {
                        ps.setString(1, type);
                        ps.setString(2, uuid);
                        ps.setString(3, name);
                        ps.setString(4, extension);
                        ps.setBytes(5, newData);
                        ps.setLong(6, id);
                        ps.executeUpdate();
                    }

                    // Add the text record.
                    newDataMap.forEach((ext, dat) -> {
                        try (final PreparedStatement ps = connection.prepareStatement("INSERT INTO doc (type, uuid, name, extension, data) VALUES (?, ?, ?, ?, ?)")) {
                            ps.setString(1, type);
                            ps.setString(2, uuid);
                            ps.setString(3, name);
                            ps.setString(4, ext);
                            ps.setBytes(5, dat);
                            ps.executeUpdate();
                        } catch (final SQLException e) {
                            throw new RuntimeException(e.getMessage(), e);
                        }
                    });
                }
            }
        }

        try (final PreparedStatement preparedStatement = connection.prepareStatement("RENAME TABLE DICT TO OLD_DICT")) {
            preparedStatement.execute();
        }
    }
}
