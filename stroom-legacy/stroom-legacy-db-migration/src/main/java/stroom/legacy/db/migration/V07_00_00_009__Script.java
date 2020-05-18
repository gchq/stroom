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

import stroom.dashboard.impl.script.ScriptSerialiser;
import stroom.docstore.impl.Serialiser2FactoryImpl;
import stroom.legacy.impex_6_1.MappingUtil;
import stroom.legacy.db.ObjectMarshaller;
import stroom.legacy.model_6_1.DocRefs;
import stroom.script.shared.ScriptDoc;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("unused")
@Deprecated
public class V07_00_00_009__Script extends BaseJavaMigration {

    @Override
    public void migrate(final Context context) throws Exception {
        Connection connection = context.getConnection();

        final ScriptSerialiser serialiser = new ScriptSerialiser(new Serialiser2FactoryImpl());

        final ObjectMarshaller<DocRefs> docRefSetMarshaller = new ObjectMarshaller<>(stroom.legacy.model_6_1.DocRefs.class);

        try (final PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT " +
                        "  CRT_MS, " +
                        "  CRT_USER, " +
                        "  UPD_MS, " +
                        "  UPD_USER, " +
                        "  NAME, " +
                        "  UUID, " +
                        "  DESCRIP, " +
                        "  DEP, " +
                        "  DAT " +
                        "FROM OLD_SCRIPT")) {
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

                    final ScriptDoc document = new ScriptDoc();
                    document.setType(ScriptDoc.DOCUMENT_TYPE);
                    document.setUuid(uuid);
                    document.setName(name);
                    document.setVersion(UUID.randomUUID().toString());
                    document.setCreateTimeMs(crtMs);
                    document.setUpdateTimeMs(updMs);
                    document.setCreateUser(crtUser);
                    document.setUpdateUser(updUser);
                    document.setDescription(descrip);
                    document.setData(dat);

                    final stroom.legacy.model_6_1.DocRefs docRefs = docRefSetMarshaller.unmarshal(dep);
                    if (docRefs != null && docRefs.getDoc() != null) {
                        document.setDependencies(MappingUtil.map(docRefs));
                    }

                    final Map<String, byte[]> dataMap = serialiser.write(document);

                    // Add the records.
                    dataMap.forEach((k, v) -> {
                        try (final PreparedStatement ps = connection.prepareStatement(
                                "INSERT INTO doc (" +
                                        "  type, " +
                                        "  uuid, " +
                                        "  name, " +
                                        "  ext, " +
                                        "  data) " +
                                        "VALUES (?, ?, ?, ?, ?)")) {
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
    }
}
