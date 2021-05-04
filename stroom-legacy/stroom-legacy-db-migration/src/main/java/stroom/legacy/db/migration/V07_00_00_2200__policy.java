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

import stroom.data.retention.shared.DataRetentionRule;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.db.util.DbUtil;
import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.impl.Serialiser2FactoryImpl;
import stroom.legacy.impex_6_1.MappingUtil;
import stroom.legacy.model_6_1.DataRetentionPolicy;
import stroom.legacy.model_6_1.XMLMarshallerUtil;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.xml.bind.JAXBContext;

@SuppressWarnings("unused")
@Deprecated
public class V07_00_00_2200__policy extends BaseJavaMigration {

    @Override
    public void migrate(final Context context) throws Exception {
        final DocumentSerialiser2<DataRetentionRules> serialiser = new Serialiser2FactoryImpl().createSerialiser(
                DataRetentionRules.class);

        try (final PreparedStatement preparedStatement = context.getConnection().prepareStatement(
                "SELECT " +
                        "  p.CRT_MS, " +
                        "  p.CRT_USER, " +
                        "  p.UPD_MS, " +
                        "  p.UPD_USER, " +
                        "  p.NAME, " +
                        "  p.DAT " +
                        "FROM OLD_POLICY p")) {
            try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    final Long crtMs = DbUtil.getLong(resultSet, 1);
                    final String crtUser = resultSet.getString(2);
                    final Long updMs = DbUtil.getLong(resultSet, 3);
                    final String updUser = resultSet.getString(4);
                    final String name = resultSet.getString(5);
                    final String dat = resultSet.getString(6);

                    if ("Data Retention".equals(name)) {
                        final DataRetentionPolicy dataRetentionPolicy = unmarshal(dat);
                        final List<DataRetentionRule> rules = MappingUtil
                                .mapList(dataRetentionPolicy.getRules(), MappingUtil::map);

                        final DataRetentionRules document = new DataRetentionRules();
                        document.setType(DataRetentionRules.DOCUMENT_TYPE);
                        document.setUuid(UUID.randomUUID().toString());
                        document.setName(name);
                        document.setVersion(UUID.randomUUID().toString());
                        document.setCreateTimeMs(crtMs);
                        document.setUpdateTimeMs(updMs);
                        document.setCreateUser(crtUser);
                        document.setUpdateUser(updUser);
                        document.setRules(rules);

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
                                ps.setString(1, document.getType());
                                ps.setString(2, document.getUuid());
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

    private DataRetentionPolicy unmarshal(final String data) throws Exception {
        final JAXBContext context = JAXBContext.newInstance(DataRetentionPolicy.class);
        return XMLMarshallerUtil.unmarshal(context, DataRetentionPolicy.class, data);
    }
}
