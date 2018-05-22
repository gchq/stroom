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
import stroom.entity.shared.DocRefs;
import stroom.query.api.v2.DocRef;
import stroom.script.ScriptSerialiser;
import stroom.script.shared.ScriptDoc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class V6_2_0_7__Script implements JdbcMigration {
    @Override
    public void migrate(final Connection connection) throws Exception {
        final ScriptSerialiser serialiser = new ScriptSerialiser();

        try (final PreparedStatement preparedStatement = connection.prepareStatement("SELECT CRT_MS, CRT_USER, UPD_MS, UPD_USER, NAME, UUID, DESCRIP, DEP, FK_RES_ID FROM SCRIPT")) {
            try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    final Long crtMs = resultSet.getLong(1);
                    final String crtUser = resultSet.getString(2);
                    final Long updMs = resultSet.getLong(3);
                    final String updUser = resultSet.getString(4);
                    final String name = resultSet.getString(5);
                    final String uuid = resultSet.getString(6);
                    final String descrip = resultSet.getString(7);
                    final String dep = resultSet.getString(8);
                    final Long resId = resultSet.getLong(9);

                    final ScriptDoc document = new ScriptDoc();
                    document.setType(ScriptDoc.DOCUMENT_TYPE);
                    document.setUuid(uuid);
                    document.setName(name);
                    document.setVersion(UUID.randomUUID().toString());
                    document.setCreateTime(crtMs);
                    document.setUpdateTime(updMs);
                    document.setCreateUser(crtUser);
                    document.setUpdateUser(updUser);
                    document.setDescription(descrip);

                    final DocRefs docRefs = serialiser.getDocRefsFromLegacyXML(dep);
                    if (docRefs != null) {
                        final List<DocRef> dependencies = new ArrayList<>(docRefs.getDoc());
                        dependencies.sort(DocRef::compareTo);
                        document.setDependencies(dependencies);
                    }

                    if (resId != null) {
                        try (final PreparedStatement ps = connection.prepareStatement("SELECT DAT FROM RES WHERE ID = ?")) {
                            ps.setLong(1, resId);
                            try (final ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) {
                                    final String dat = resultSet.getString(1);
                                    document.setData(dat);
                                }
                            }
                        }
                    }

                    final Map<String, byte[]> dataMap = serialiser.write(document);

                    // Add the records.
                    dataMap.forEach((k, v) -> {
                        try (final PreparedStatement ps = connection.prepareStatement("INSERT INTO doc (type, uuid, name, ext, data) VALUES (?, ?, ?, ?, ?)")) {
                            ps.setString(1, ScriptDoc.DOCUMENT_TYPE);
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

        try (final PreparedStatement preparedStatement = connection.prepareStatement("RENAME TABLE SCRIPT TO OLD_SCRIPT")) {
            preparedStatement.execute();
        }
        try (final PreparedStatement preparedStatement = connection.prepareStatement("RENAME TABLE RES TO OLD_RES")) {
            preparedStatement.execute();
        }
    }
}
