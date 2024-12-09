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

import stroom.db.util.DbUtil;
import stroom.docstore.impl.Serialiser2FactoryImpl;
import stroom.feed.impl.FeedSerialiser;
import stroom.feed.shared.FeedDoc;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("unused")
@Deprecated
public class V07_00_00_016__Feed extends BaseJavaMigration {

    @Override
    public void migrate(final Context context) throws Exception {
        final FeedSerialiser serialiser = new FeedSerialiser(new Serialiser2FactoryImpl());

        try (final PreparedStatement preparedStatement = context.getConnection().prepareStatement(
                "SELECT " +
                        "  f.CRT_MS, " +
                        "  f.CRT_USER, " +
                        "  f.UPD_MS, " +
                        "  f.UPD_USER, " +
                        "  f.NAME, " +
                        "  f.UUID, " +
                        "  f.DESCRIP, " +
                        "  st.NAME, " +
                        "  f.CLS, " +
                        "  f.ENC, " +
                        "  f.CTX_ENC, " +
                        "  f.STAT, " +
                        "  f.RETEN_DAY_AGE, " +
                        "  f.REF " +
                        "FROM OLD_FD f " +
                        "LEFT OUTER JOIN OLD_STRM_TP st ON (st.ID = f.FK_STRM_TP_ID)")) {
            try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    final Long crtMs = DbUtil.getLong(resultSet, 1);
                    final String crtUser = resultSet.getString(2);
                    final Long updMs = DbUtil.getLong(resultSet, 3);
                    final String updUser = resultSet.getString(4);
                    final String name = resultSet.getString(5);
                    final String uuid = resultSet.getString(6);
                    final String descrip = resultSet.getString(7);
                    final String streamTypeName = resultSet.getString(8);
                    final String classification = resultSet.getString(9);
                    final String encoding = resultSet.getString(10);
                    final String contextEncoding = resultSet.getString(11);
                    final byte status = resultSet.getByte(12);
                    final Integer retentionDayAge = DbUtil.getInteger(resultSet, 13);
                    final boolean reference = resultSet.getBoolean(14);

                    final FeedDoc document = new FeedDoc();
                    document.setUuid(uuid);
                    document.setName(name);
                    document.setVersion(UUID.randomUUID().toString());
                    document.setCreateTimeMs(crtMs);
                    document.setUpdateTimeMs(updMs);
                    document.setCreateUser(crtUser);
                    document.setUpdateUser(updUser);
                    document.setDescription(descrip);
                    document.setStreamType(streamTypeName);
                    document.setClassification(classification);
                    document.setEncoding(encoding);
                    document.setContextEncoding(contextEncoding);
                    document.setStatus(FeedDoc.FeedStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(status));
                    document.setRetentionDayAge(retentionDayAge);
                    document.setReference(reference);

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
                            ps.setString(1, FeedDoc.DOCUMENT_TYPE);
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
