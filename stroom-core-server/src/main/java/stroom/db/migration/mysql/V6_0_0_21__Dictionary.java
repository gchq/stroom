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
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docstore.server.JsonSerialiser;
import stroom.docstore.server.Serialiser;
import stroom.docstore.shared.DocRefUtil;
import stroom.query.api.v2.DocRef;

import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public class V6_0_0_21__Dictionary implements JdbcMigration {
    @Override
    public void migrate(final Connection connection) throws Exception {
        final Serialiser<DictionaryDoc> serialiser = new JsonSerialiser<>();
        try (final Statement statement = connection.createStatement()) {
            try (final ResultSet resultSet = statement.executeQuery("SELECT CRT_MS, CRT_USER, UPD_MS, UPD_USER, UUID, NAME, DAT FROM DICT")) {
                while (resultSet.next()) {
                    final long crtMs = resultSet.getLong(1);
                    final String crtUser = resultSet.getString(2);
                    final long updtMs = resultSet.getLong(3);
                    final String updUser = resultSet.getString(4);
                    final String uuid = resultSet.getString(5);
                    final String name = resultSet.getString(6);
                    final String data = resultSet.getString(7);

                    DictionaryDoc dictionaryDoc = new DictionaryDoc();
                    dictionaryDoc.setCreateTime(crtMs);
                    dictionaryDoc.setCreateUser(crtUser);
                    dictionaryDoc.setUpdateTime(updtMs);
                    dictionaryDoc.setUpdateUser(updUser);
                    dictionaryDoc.setType(DictionaryDoc.ENTITY_TYPE);
                    dictionaryDoc.setUuid(uuid);
                    dictionaryDoc.setName(name);
                    dictionaryDoc.setData(data);
                    dictionaryDoc.setVersion(UUID.randomUUID().toString());

                    final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    serialiser.write(outputStream, dictionaryDoc);

                    insert(connection, DocRefUtil.create(dictionaryDoc), outputStream.toByteArray());
                }
            }
        }
    }

    private void insert(final Connection connection, final DocRef docRef, final byte[] data) throws SQLException {
        // Insert node entry.
        try (final PreparedStatement preparedStatement = connection.prepareStatement("INSERT INTO doc (type, uuid, name, data) VALUES (?, ?, ?, ?)")) {
            preparedStatement.setString(1, docRef.getType());
            preparedStatement.setString(2, docRef.getUuid());
            preparedStatement.setString(3, docRef.getName());
            preparedStatement.setBytes(4, data);
            preparedStatement.executeUpdate();
        }
    }
}
