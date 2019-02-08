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

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import stroom.db.migration._V07_00_00.doc.script._V07_00_00_ScriptDoc;
import stroom.db.migration._V07_00_00.doc.script._V07_00_00_ScriptSerialiser;
import stroom.db.migration._V07_00_00.docref._V07_00_00_DocRef;
import stroom.db.migration._V07_00_00.entity.shared._V07_00_00_DocRefs;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class V07_00_00_017__Script extends BaseJavaMigration {

    @Override
    public void migrate(final Context context) throws Exception {
        Connection connection = context.getConnection();

        final _V07_00_00_ScriptSerialiser serialiser = new _V07_00_00_ScriptSerialiser();

        try (final PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT CRT_MS, CRT_USER, UPD_MS, UPD_USER, NAME, UUID, DESCRIP, DEP, DAT FROM SCRIPT")) {
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
                    final String dat = resultSet.getString(9);

                    final _V07_00_00_ScriptDoc document = new _V07_00_00_ScriptDoc();
                    document.setType(_V07_00_00_ScriptDoc.DOCUMENT_TYPE);
                    document.setUuid(uuid);
                    document.setName(name);
                    document.setVersion(UUID.randomUUID().toString());
                    document.setCreateTime(crtMs);
                    document.setUpdateTime(updMs);
                    document.setCreateUser(crtUser);
                    document.setUpdateUser(updUser);
                    document.setDescription(descrip);
                    document.setData(dat);

                    final _V07_00_00_DocRefs docRefs = serialiser.getDocRefsFromLegacyXML(dep);
                    if (docRefs != null) {
                        final List<_V07_00_00_DocRef> dependencies = new ArrayList<>(docRefs.getDoc());
                        dependencies.sort(_V07_00_00_DocRef::compareTo);
                        document.setDependencies(dependencies);
                    }

                    final Map<String, byte[]> dataMap = serialiser.write(document);

                    // Add the records.
                    dataMap.forEach((k, v) -> {
                        try (final PreparedStatement ps = connection.prepareStatement(
                                "INSERT INTO doc (type, uuid, name, ext, data) VALUES (?, ?, ?, ?, ?)")) {
                            ps.setString(1, _V07_00_00_ScriptDoc.DOCUMENT_TYPE);
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
