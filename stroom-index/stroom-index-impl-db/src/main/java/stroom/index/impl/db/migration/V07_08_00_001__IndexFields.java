/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.index.impl.db.migration;

import stroom.docref.DocRef;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.impl.Serialiser2FactoryImpl;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.LuceneIndexField;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class V07_08_00_001__IndexFields extends BaseJavaMigration {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(V07_08_00_001__IndexFields.class);

    private final Serialiser2<LuceneIndexDoc> indexDocSerialiser =
            new Serialiser2FactoryImpl().createSerialiser(LuceneIndexDoc.class);

    @Override
    public void migrate(final Context context) throws Exception {
        final List<LuceneIndexDoc> docs = getDocs(context);
        for (final LuceneIndexDoc doc : docs) {
            if (NullSafe.hasItems(doc, LuceneIndexDoc::getFields)) {
                final DocRef docRef = doc.asDocRef();

                // Ensure field source
                createFieldSource(context, docRef);

                // Get field source
                final int fieldSourceId = getFieldSourceId(context, docRef);

                // Add fields to DB
                addFieldsToDb(context, fieldSourceId, doc.getFields());

                // Remove all fields from doc
                doc.setFields(null);

                // Write the updated doc
                writeDoc(context, doc);
            }
        }
    }

    private List<LuceneIndexDoc> getDocs(final Context context) throws SQLException {
        final List<LuceneIndexDoc> docs = new ArrayList<>();
        try (final PreparedStatement preparedStatement = context.getConnection()
                .prepareStatement("" +
                                  "SELECT" +
                                  " `uuid`," +
                                  " `name`," +
                                  " `data`" +
                                  " FROM `doc`" +
                                  " WHERE `type` = ?" +
                                  " AND `ext` = ?")) {
            preparedStatement.setString(1, LuceneIndexDoc.TYPE);
            preparedStatement.setString(2, "meta");

            try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    final String uuid = resultSet.getString(1);
                    final String name = resultSet.getString(2);
                    final String data = resultSet.getString(3);

                    // Read the doc
                    final LuceneIndexDoc doc = readDoc(data);
                    docs.add(doc);
                }
            }
        } catch (final SQLSyntaxErrorException e) {
            // Ignore errors as they are due to doc not existing, which is ok because it means we have nothing to
            // migrate.
            LOGGER.debug(e::getMessage, e);
        }
        return docs;
    }

    private void createFieldSource(final Context context, final DocRef docRef) throws SQLException {
        // Ensure field source
        try (final PreparedStatement ps = context.getConnection()
                .prepareStatement("" +
                                  "INSERT INTO `index_field_source`" +
                                  " (`type`," +
                                  " `uuid`," +
                                  " `name`)" +
                                  " VALUES (?, ?, ?)" +
                                  " ON DUPLICATE KEY UPDATE" +
                                  " `type`=`type`")) {
            ps.setString(1, docRef.getType());
            ps.setString(2, docRef.getUuid());
            ps.setString(3, docRef.getName());
            ps.execute();
        }
    }

    private int getFieldSourceId(final Context context, final DocRef docRef) throws SQLException {
        try (final PreparedStatement ps = context.getConnection()
                .prepareStatement("" +
                                  "SELECT `id`" +
                                  " FROM `index_field_source`" +
                                  " WHERE `type` = ?" +
                                  " AND `uuid` = ?")) {
            ps.setString(1, docRef.getType());
            ps.setString(2, docRef.getUuid());

            try (final ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new RuntimeException("Field source not found");
    }

    private void addFieldsToDb(final Context context,
                               final int fieldSourceId,
                               final List<LuceneIndexField> fields) throws SQLException {
        try (final PreparedStatement ps = context.getConnection()
                .prepareStatement("" +
                                  "INSERT INTO `index_field` (" +
                                  "`fk_index_field_source_id`, " +
                                  "`type`, " +
                                  "`name`, " +
                                  "`analyzer`, " +
                                  "`indexed`, " +
                                  "`stored`, " +
                                  "`term_positions`, " +
                                  "`case_sensitive`)" +
                                  " VALUES (?, ?, ?, ?, ?, ?, ?, ?)" +
                                  " ON DUPLICATE KEY UPDATE" +
                                  " `fk_index_field_source_id`=`fk_index_field_source_id`")) {

            for (final LuceneIndexField field : fields) {
                ps.setInt(1, fieldSourceId);
                ps.setByte(2, field.getFldType() == null
                        ? 0
                        : field.getFldType().getPrimitiveValue());
                ps.setString(3, field.getFldName());
                ps.setString(4, field.getAnalyzerType() == null
                        ? null
                        : field.getAnalyzerType().getDisplayValue());
                ps.setBoolean(5, field.isIndexed());
                ps.setBoolean(6, field.isStored());
                ps.setBoolean(7, field.isTermPositions());
                ps.setBoolean(8, field.isCaseSensitive());
                ps.executeUpdate();
            }
        }
    }

    private LuceneIndexDoc readDoc(final String data) {
        LuceneIndexDoc doc = null;
        try {
            doc = indexDocSerialiser.read(data.getBytes(StandardCharsets.UTF_8));
        } catch (final IOException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return doc;
    }

    private void writeDoc(final Context context, final LuceneIndexDoc doc) throws IOException, SQLException {
        final Map<String, byte[]> dataMap = indexDocSerialiser.write(doc);
        final byte[] newData = dataMap.remove("meta");
        // Add the records.
        try (final PreparedStatement ps = context.getConnection().prepareStatement(
                "UPDATE `doc` SET `data` = ? WHERE `type` = ? AND `uuid` = ? AND `ext` = ?")) {
            ps.setBytes(1, newData);
            ps.setString(2, doc.getType());
            ps.setString(3, doc.getUuid());
            ps.setString(4, "meta");
            ps.executeUpdate();
        }
    }
}
