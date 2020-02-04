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

package stroom.core.db.migration.mysql;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import stroom.core.db.migration._V07_00_00.doc._V07_00_00_JsonSerialiser2;
import stroom.core.db.migration._V07_00_00.doc.dictionary._V07_00_00_DictionaryDoc;
import stroom.core.db.migration._V07_00_00.doc.dictionary._V07_00_00_DictionarySerialiser;
import stroom.core.db.migration._V07_00_00.doc.dictionary._V07_00_00_OldDictionaryDoc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class V07_00_00_003__Dictionary extends BaseJavaMigration {

    @Override
    public void migrate(final Context context) throws Exception {
        final _V07_00_00_JsonSerialiser2<_V07_00_00_OldDictionaryDoc> oldSerialiser = new _V07_00_00_JsonSerialiser2<>(_V07_00_00_OldDictionaryDoc.class);
        final _V07_00_00_DictionarySerialiser newSerialiser = new _V07_00_00_DictionarySerialiser();

        try (final PreparedStatement preparedStatement = context.getConnection().prepareStatement(
                "SELECT id, type, uuid, name, ext, data FROM doc WHERE type = 'Dictionary'")) {
            try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    final long id = resultSet.getLong(1);
                    final String type = resultSet.getString(2);
                    final String uuid = resultSet.getString(3);
                    final String name = resultSet.getString(4);
                    final String ext = resultSet.getString(5);
                    final byte[] data = resultSet.getBytes(6);

                    // Deserialise the old dictionary document format.
                    final Map<String, byte[]> dataMap = new HashMap<>();
                    dataMap.put("meta", data);

                    final _V07_00_00_OldDictionaryDoc document = oldSerialiser.read(dataMap);

                    // Copy the values to the new dictionary document format.
                    final _V07_00_00_DictionaryDoc dictionaryDoc = new _V07_00_00_DictionaryDoc();
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
                    final _V07_00_00_DictionarySerialiser dictionarySerialiser = new _V07_00_00_DictionarySerialiser();
                    final Map<String, byte[]> newDataMap = newSerialiser.write(dictionaryDoc);

                    // Update the meta record.
                    final byte[] newData = newDataMap.remove("meta");
                    try (final PreparedStatement ps = context.getConnection().prepareStatement(
                            "UPDATE doc SET " +
                                    "type = ?, " +
                                    "uuid = ?, " +
                                    "name = ?, " +
                                    "ext = ?, " +
                                    "data = ? " +
                                    "WHERE id = ?")) {
                        ps.setString(1, type);
                        ps.setString(2, uuid);
                        ps.setString(3, name);
                        ps.setString(4, ext);
                        ps.setBytes(5, newData);
                        ps.setLong(6, id);
                        ps.executeUpdate();
                    }

                    // Add the text record.
                    newDataMap.forEach((k, v) -> {
                        try (final PreparedStatement ps = context.getConnection().prepareStatement(
                                "INSERT INTO doc (type, uuid, name, ext, data) VALUES (?, ?, ?, ?, ?)")) {
                            ps.setString(1, type);
                            ps.setString(2, uuid);
                            ps.setString(3, name);
                            ps.setString(4, k);
                            ps.setBytes(5, v);
                            ps.executeUpdate();
                        } catch (final SQLException e) {
                            throw new RuntimeException(e.getMessage(), e);
                        }
                    });
                }
            }
        }
    }
}
