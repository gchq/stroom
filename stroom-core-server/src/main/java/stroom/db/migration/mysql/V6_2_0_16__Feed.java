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
import stroom.feed.FeedSerialiser;
import stroom.feed.shared.FeedDoc;
import stroom.feed.shared.FeedDoc.FeedStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

public class V6_2_0_16__Feed implements JdbcMigration {
    @Override
    public void migrate(final Connection connection) throws Exception {
        final FeedSerialiser serialiser = new FeedSerialiser();

        try (final PreparedStatement preparedStatement = connection.prepareStatement("SELECT f.CRT_MS, f.CRT_USER, f.UPD_MS, f.UPD_USER, f.NAME, f.UUID, f.DESCRIP, st.NAME, f.CLS, f.ENC, f.CTX_ENC, f.STAT, f.RETEN_DAY_AGE, f.REF FROM FD f LEFT OUTER JOIN STRM_TP st ON (st.ID = f.FK_STRM_TP_ID)")) {
            try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    final Long crtMs = resultSet.getLong(1);
                    final String crtUser = resultSet.getString(2);
                    final Long updMs = resultSet.getLong(3);
                    final String updUser = resultSet.getString(4);
                    final String name = resultSet.getString(5);
                    final String uuid = resultSet.getString(6);
                    final String descrip = resultSet.getString(7);
                    final String streamTypeName = resultSet.getString(8);
                    final String classification = resultSet.getString(9);
                    final String encoding = resultSet.getString(10);
                    final String contextEncoding = resultSet.getString(11);
                    final byte status = resultSet.getByte(12);
                    final Integer retentionDayAge = resultSet.getInt(13);
                    final boolean reference = resultSet.getBoolean(14);

                    final FeedDoc document = new FeedDoc();
                    document.setType(FeedDoc.DOCUMENT_TYPE);
                    document.setUuid(uuid);
                    document.setName(name);
                    document.setVersion(UUID.randomUUID().toString());
                    document.setCreateTime(crtMs);
                    document.setUpdateTime(updMs);
                    document.setCreateUser(crtUser);
                    document.setUpdateUser(updUser);
                    document.setDescription(descrip);
                    document.setStreamType(streamTypeName);

                    document.setClassification(classification);
                    document.setEncoding(encoding);
                    document.setContextEncoding(contextEncoding);
                    document.setStatus(FeedStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(status));
                    document.setRetentionDayAge(retentionDayAge);
                    document.setReference(reference);

                    final Map<String, byte[]> dataMap = serialiser.write(document);

                    // Add the records.
                    dataMap.forEach((k, v) -> {
                        try (final PreparedStatement ps = connection.prepareStatement("INSERT INTO doc (type, uuid, name, ext, data) VALUES (?, ?, ?, ?, ?)")) {
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

        try (final PreparedStatement preparedStatement = connection.prepareStatement("ALTER TABLE FD DROP FOREIGN KEY FD_FK_STRM_TP_ID;")) {
            preparedStatement.execute();
        }
        final String[] cols = new String[] {"DESCRIP", "FK_STRM_TP_ID", "CLS", "ENC", "CTX_ENC", "STAT", "RETEN_DAY_AGE", "REF"};
        for (final String col : cols) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement("ALTER TABLE FD DROP COLUMN " + col + ";")) {
                preparedStatement.execute();
            }
        }
    }
}
