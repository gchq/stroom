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

package stroom.streamstore.server;

import stroom.entity.server.util.StroomDatabaseInfo;
import stroom.entity.server.util.ConnectionUtil;
import stroom.streamstore.shared.StreamAttributeValue;
import stroom.util.logging.StroomLogger;
import stroom.util.logging.LogExecutionTime;
import org.hsqldb.types.Types;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

@Transactional
@Component
public class StreamAttributeValueServiceTransactionHelper {
    private static StroomLogger LOGGER = StroomLogger.getLogger(StreamAttributeValueServiceTransactionHelper.class);

    @Resource
    private DataSource dataSource;

    @Resource
    private StroomDatabaseInfo stroomDatabaseInfo;

    private static final String INSERT_SQL = "INSERT INTO " + StreamAttributeValue.TABLE_NAME + " ("
            + StreamAttributeValue.VERSION + ", " + StreamAttributeValue.CREATE_MS + ", "
            + StreamAttributeValue.VALUE_STRING + ", " + StreamAttributeValue.VALUE_NUMBER + ", "
            + StreamAttributeValue.STREAM_ID + ", " + StreamAttributeValue.STREAM_ATTRIBUTE_KEY_ID
            + ") VALUES (?, ?, ?, ?, ?, ?)";

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveBatch(final List<StreamAttributeValue> list) {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        if (list.size() > 0) {
            Connection connection = null;
            try {
                connection = dataSource.getConnection();
                try (PreparedStatement ps = connection.prepareStatement(INSERT_SQL)) {
                    for (final StreamAttributeValue streamAttributeValue : list) {
                        ps.setInt(1, 1);
                        ps.setLong(2, streamAttributeValue.getCreateMs());
                        if (streamAttributeValue.getValueString() != null) {
                            ps.setString(3, streamAttributeValue.getValueString());
                        } else {
                            ps.setNull(3, Types.VARCHAR);
                        }
                        if (streamAttributeValue.getValueNumber() != null) {
                            ps.setLong(4, streamAttributeValue.getValueNumber());
                        } else {
                            ps.setNull(4, Types.BIGINT);
                        }
                        ps.setLong(5, streamAttributeValue.getStreamId());
                        ps.setLong(6, streamAttributeValue.getStreamAttributeKeyId());

                        ps.addBatch();
                    }

                    ps.executeBatch();
                    ps.close();
                }

            } catch (final Exception ex) {
                LOGGER.error("saveBatch()", ex);
            } finally {
                ConnectionUtil.close(connection);
            }
        }
        LOGGER.debug("saveBatch() - inserted %s records in %s", list.size(), logExecutionTime);
    }
}
