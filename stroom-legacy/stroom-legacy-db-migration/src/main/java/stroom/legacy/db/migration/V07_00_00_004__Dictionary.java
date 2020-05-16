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

import stroom.dictionary.impl.DictionarySerialiser;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docstore.impl.JsonSerialiser2;
import stroom.docstore.impl.Serialiser2FactoryImpl;
import stroom.legacy.impex_6_1.MappingUtil;
import stroom.legacy.model_6_1.OldDictionaryDoc;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class V07_00_00_004__Dictionary extends BaseJavaMigration {
    @Override
    public void migrate(final Context context) throws Exception {
        final JsonSerialiser2<OldDictionaryDoc> oldSerialiser = new JsonSerialiser2<>(OldDictionaryDoc.class);
        final DictionarySerialiser newSerialiser = new DictionarySerialiser(new Serialiser2FactoryImpl());

        try (final PreparedStatement preparedStatement = context.getConnection().prepareStatement(
                "SELECT " +
                        "  id, " +
                        "  type, " +
                        "  uuid, " +
                        "  name, " +
                        "  ext, " +
                        "  data " +
                        "FROM doc " +
                        "WHERE type = 'Dictionary'")) {
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

                    final OldDictionaryDoc document = oldSerialiser.read(dataMap);

                    // Copy the values to the new dictionary document format.
                    final DictionaryDoc dictionaryDoc = new DictionaryDoc();
                    dictionaryDoc.setVersion(document.getVersion());
                    dictionaryDoc.setCreateTimeMs(document.getCreateTime());
                    dictionaryDoc.setUpdateTimeMs(document.getUpdateTime());
                    dictionaryDoc.setCreateUser(document.getCreateUser());
                    dictionaryDoc.setUpdateUser(document.getUpdateUser());
                    dictionaryDoc.setType(document.getType());
                    dictionaryDoc.setUuid(document.getUuid());
                    dictionaryDoc.setName(document.getName());
                    dictionaryDoc.setDescription(document.getDescription());
                    dictionaryDoc.setImports(MappingUtil.mapList(document.getImports(), MappingUtil::map));
                    dictionaryDoc.setData(document.getData());

                    // Serialise the new document.
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
                                "INSERT INTO doc (" +
                                        "  type, " +
                                        "  uuid, " +
                                        "  name, " +
                                        "  ext, " +
                                        "  data) " +
                                        "VALUES (?, ?, ?, ?, ?)")) {
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
