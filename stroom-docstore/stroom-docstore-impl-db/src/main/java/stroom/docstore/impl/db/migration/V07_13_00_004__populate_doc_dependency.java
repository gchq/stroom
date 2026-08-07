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

package stroom.docstore.impl.db.migration;

import stroom.docref.DocRef;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

/**
 * One-time migration to populate the doc_dependency table from existing
 * document JSON content stored in the doc/doc_data tables.
 * <p>
 * For each non-deleted document with JSON content, this migration walks the
 * entire JSON tree looking for objects that have both a {@code type} (String)
 * and {@code uuid} (String) field — the defining characteristics of a DocRef.
 * Each discovered DocRef is recorded as a dependency edge from the owning
 * document to the referenced document.
 * <p>
 * <b>StroomQL-based doc types</b> (AnalyticRule, Report, Query) are
 * <b>skipped</b> because their data source is embedded in a StroomQL query
 * string that requires Guice-injected services to parse. These will have
 * their dependencies populated on first save via the entity event handler.
 * <p>
 * <b>ProcessorFilter</b> dependencies are handled separately by the
 * cross-module migration
 * {@code V07_14_00_005__populate_doc_dependency_processor_filters}
 * in {@code stroom-app}, since processor data may reside in a different
 * database.
 */
@SuppressWarnings("unused")
public class V07_14_00_004__populate_doc_dependency extends BaseJavaMigration {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory
            .getLogger(V07_14_00_004__populate_doc_dependency.class);

    /**
     * Doc types whose dependencies are embedded in StroomQL query strings
     * and cannot be extracted by JSON tree walking. They will be populated
     * on first save via the entity event handler.
     */
    private static final Set<String> SKIPPED_TYPES = Set.of(
            "AnalyticRule",
            "Report",
            "Query"
    );

    private static final String SELECT_DOCS = """
            SELECT d.type, d.uuid, d.name, dd.json_data
            FROM doc d
            JOIN doc_data dd ON dd.fk_doc_id = d.id AND dd.ext = 'json'
            WHERE d.deleted IS NULL
            AND dd.json_data IS NOT NULL
            """;

    // Uses ON DUPLICATE KEY UPDATE (a no-op self-assignment on the (from_uuid, to_uuid) unique key)
    // rather than INSERT IGNORE so the migration is idempotent if re-run, without silently swallowing
    // genuine errors (e.g. data truncation) the way INSERT IGNORE would.
    private static final String INSERT_DEPENDENCY = """
            INSERT INTO doc_dependency
                (from_type, from_uuid, from_name, to_type, to_uuid, to_name)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE from_type = from_type
            """;

    @Override
    public void migrate(final Context context) throws Exception {
        final Connection connection = context.getConnection();
        final ObjectMapper objectMapper = new ObjectMapper();

        int docCount = 0;
        int edgeCount = 0;
        int skipCount = 0;
        int errorCount = 0;

        try (final PreparedStatement selectStmt = connection.prepareStatement(SELECT_DOCS);
                final PreparedStatement insertStmt = connection.prepareStatement(INSERT_DEPENDENCY);
                final ResultSet rs = selectStmt.executeQuery()) {

            while (rs.next()) {
                final String fromType = rs.getString(1);
                final String fromUuid = rs.getString(2);
                final String fromName = rs.getString(3);
                final String jsonData = rs.getString(4);

                // Skip StroomQL-based doc types
                if (SKIPPED_TYPES.contains(fromType)) {
                    skipCount++;
                    continue;
                }

                if (jsonData == null || jsonData.isBlank()) {
                    continue;
                }

                docCount++;

                try {
                    final JsonNode root = objectMapper.readTree(jsonData);
                    final Set<DocRef> refs = new HashSet<>();
                    DocRefFinder.findDocRefs(root, fromUuid, refs);

                    for (final DocRef ref : refs) {
                        insertStmt.setString(1, DocRefFinder.safeStr(fromType));
                        insertStmt.setString(2, fromUuid);
                        insertStmt.setString(3, DocRefFinder.safeStr(fromName));
                        insertStmt.setString(4, DocRefFinder.safeStr(ref.getType()));
                        insertStmt.setString(5, ref.getUuid());
                        insertStmt.setString(6, DocRefFinder.safeStr(ref.getName()));
                        insertStmt.addBatch();
                        edgeCount++;
                    }

                    // Execute batch periodically to avoid OOM
                    if (docCount % 500 == 0) {
                        insertStmt.executeBatch();
                        LOGGER.info("Processed {} docs, {} edges so far", docCount, edgeCount);
                    }
                } catch (final Exception e) {
                    errorCount++;
                    LOGGER.error(() ->
                            "Error extracting dependencies from " + fromType + " '" +
                            fromName + "' (" + fromUuid + "): " + e.getMessage(), e);
                }
            }

            // Execute remaining batch
            insertStmt.executeBatch();
        }

        LOGGER.info("doc_dependency migration complete: " +
                    "processed={}, edges={}, skipped={}, errors={}",
                docCount, edgeCount, skipCount, errorCount);
    }
}
