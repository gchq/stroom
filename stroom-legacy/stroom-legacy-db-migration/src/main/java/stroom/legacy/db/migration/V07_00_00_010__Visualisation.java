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

import stroom.dashboard.impl.visualisation.VisualisationSerialiser;
import stroom.db.util.DbUtil;
import stroom.docstore.impl.Serialiser2FactoryImpl;
import stroom.legacy.db.ObjectMarshaller;
import stroom.legacy.impex_6_1.MappingUtil;
import stroom.legacy.model_6_1.DocRef;
import stroom.visualisation.shared.VisualisationDoc;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("unused")
@Deprecated
public class V07_00_00_010__Visualisation extends BaseJavaMigration {

    @Override
    public void migrate(final Context context) throws Exception {
        final VisualisationSerialiser serialiser = new VisualisationSerialiser(new Serialiser2FactoryImpl());
        ObjectMarshaller<DocRef> docRefMarshaller = new ObjectMarshaller<>(DocRef.class);

        try (final PreparedStatement preparedStatement = context.getConnection().prepareStatement(
                "SELECT " +
                        "  CRT_MS, " +
                        "  CRT_USER, " +
                        "  UPD_MS, " +
                        "  UPD_USER, " +
                        "  NAME, " +
                        "  UUID, " +
                        "  DESCRIP, " +
                        "  FUNC_NAME, " +
                        "  SETTINGS, " +
                        "  SCRIPT " +
                        "FROM OLD_VIS")) {
            try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    final Long crtMs = DbUtil.getLong(resultSet, 1);
                    final String crtUser = resultSet.getString(2);
                    final Long updMs = DbUtil.getLong(resultSet, 3);
                    final String updUser = resultSet.getString(4);
                    final String name = resultSet.getString(5);
                    final String uuid = resultSet.getString(6);
                    final String descrip = resultSet.getString(7);
                    final String funcName = resultSet.getString(8);
                    final String settings = resultSet.getString(9);
                    final String script = resultSet.getString(10);

                    final VisualisationDoc document = new VisualisationDoc();
                    document.setUuid(uuid);
                    document.setName(name);
                    document.setVersion(UUID.randomUUID().toString());
                    document.setCreateTimeMs(crtMs);
                    document.setUpdateTimeMs(updMs);
                    document.setCreateUser(crtUser);
                    document.setUpdateUser(updUser);
                    document.setDescription(descrip);
                    document.setFunctionName(funcName);
                    document.setSettings(settings);

                    final DocRef scriptRef = docRefMarshaller.unmarshal(script);
                    if (scriptRef != null) {
                        document.setScriptRef(MappingUtil.map(scriptRef));
                    }

                    final Map<String, byte[]> dataMap = serialiser.write(document);

                    // Add the records.
                    dataMap.forEach((k, v) -> {
                        try (final PreparedStatement ps = context.getConnection().prepareStatement(
                                "INSERT INTO doc (" +
                                        "  type, " +
                                        "  uuid, " +
                                        "  name, " +
                                        "  ext, " +
                                        "  data) " +
                                        "VALUES (?, ?, ?, ?, ?)")) {
                            ps.setString(1, VisualisationDoc.DOCUMENT_TYPE);
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
