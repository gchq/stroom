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

package stroom.dictionary.impl.db.migration;

import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.impl.Serialiser2FactoryImpl;
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
public class V07_12_00_002__DictionaryWords extends BaseJavaMigration {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(V07_12_00_002__DictionaryWords.class);

    private final Serialiser2<DictionaryDoc> dictionaryDocSerialiser =
            new Serialiser2FactoryImpl().createSerialiser(DictionaryDoc.class);

    @Override
    public void migrate(final Context context) throws Exception {
        final List<DictionaryDoc> docs = getDocs(context);
        for (DictionaryDoc doc : docs) {
            if (NullSafe.isNonBlankString(doc.getData())) {
                final DocRef docRef = doc.asDocRef();

                // Ensure word source
                createWordSource(context, docRef);

                // Get word source
                final int wordSourceId = getWordSourceId(context, docRef);

                // Add words to DB
                addWordsToDb(context, wordSourceId, doc.getData());

                // Remove all words from doc
                doc = doc.copy().data(null).build();

                // Write the updated doc
                writeDoc(context, doc);
            }
        }
    }

    private List<DictionaryDoc> getDocs(final Context context) throws SQLException {
        final List<DictionaryDoc> docs = new ArrayList<>();
        try (final PreparedStatement preparedStatement = context.getConnection()
                .prepareStatement("" +
                                  "SELECT" +
                                  " `uuid`," +
                                  " `name`," +
                                  " `data`" +
                                  " FROM `doc`" +
                                  " WHERE `type` = ?" +
                                  " AND `ext` = ?")) {
            preparedStatement.setString(1, DictionaryDoc.TYPE);
            preparedStatement.setString(2, "meta");

            try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    final String uuid = resultSet.getString(1);
                    final String name = resultSet.getString(2);
                    final String data = resultSet.getString(3);

                    // Read the doc
                    final DictionaryDoc doc = readDoc(data);
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

    private void createWordSource(final Context context, final DocRef docRef) throws SQLException {
        // Ensure word source
        try (final PreparedStatement ps = context.getConnection()
                .prepareStatement("" +
                                  "INSERT INTO `dictionary_word_source`" +
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

    private int getWordSourceId(final Context context, final DocRef docRef) throws SQLException {
        try (final PreparedStatement ps = context.getConnection()
                .prepareStatement("" +
                                  "SELECT `id`" +
                                  " FROM `dictionary_word_source`" +
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
        throw new RuntimeException("Word source not found");
    }

    private void addWordsToDb(final Context context,
                              final int wordSourceId,
                              final String words) throws SQLException {
        try (final PreparedStatement ps = context.getConnection()
                .prepareStatement("" +
                                  "INSERT INTO `dictionary_word` (" +
                                  "`fk_dictionary_word_source_id`, " +
                                  "`word`)" +
                                  " VALUES (?, ?)" +
                                  " ON DUPLICATE KEY UPDATE" +
                                  " `fk_dictionary_word_source_id`=`fk_dictionary_word_source_id`")) {

            for (final String word : words.split("\n")) {
                if (NullSafe.isNonBlankString(word)) {
                    ps.setInt(1, wordSourceId);
                    ps.setString(2, word);
                    ps.executeUpdate();
                }
            }
        }
    }

    private DictionaryDoc readDoc(final String data) {
        DictionaryDoc doc = null;
        try {
            doc = dictionaryDocSerialiser.read(data.getBytes(StandardCharsets.UTF_8));
        } catch (final IOException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return doc;
    }

    private void writeDoc(final Context context, final DictionaryDoc doc) throws IOException, SQLException {
        final Map<String, byte[]> dataMap = dictionaryDocSerialiser.write(doc);
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
