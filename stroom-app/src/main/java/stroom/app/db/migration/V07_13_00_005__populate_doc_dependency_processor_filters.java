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

package stroom.app.db.migration;

import stroom.docref.DocRef;
import stroom.docstore.impl.db.DocStoreDbConnProvider;
import stroom.docstore.impl.db.migration.DocRefFinder;
import stroom.processor.impl.db.ProcessorDbConnProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Cross-module migration to populate {@code doc_dependency} edges for
 * ProcessorFilter entities.
 * <p>
 * ProcessorFilter data resides in the {@code processor_filter} and
 * {@code processor} tables, which may be in a different database to the
 * {@code doc}/{@code doc_dependency} tables. This migration therefore
 * injects both {@link ProcessorDbConnProvider} and
 * {@link DocStoreDbConnProvider} to query each database independently.
 * <p>
 * For each non-deleted processor filter:
 * <ul>
 *   <li>The {@code data} column (serialised QueryData JSON) is walked for
 *       DocRef objects — this picks up the {@code dataSource} DocRef and
 *       any DocRefs inside the expression tree (e.g. dictionaries).</li>
 *   <li>The linked {@code processor.pipeline_uuid} (a bare UUID string)
 *       is added as an explicit Pipeline dependency, with the pipeline
 *       name resolved from the {@code doc} table in the docstore DB.</li>
 * </ul>
 */
public class V07_14_00_005__populate_doc_dependency_processor_filters
        extends AbstractCrossModuleJavaDbMigration {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory
            .getLogger(V07_14_00_005__populate_doc_dependency_processor_filters.class);

    private static final String PIPELINE_TYPE = "Pipeline";
    private static final String PROCESSOR_FILTER_TYPE = "ProcessorFilter";

    /**
     * Select all non-deleted processor filters with their pipeline UUID.
     * Runs against the processor database.
     */
    private static final String SELECT_PROCESSOR_FILTERS = """
            SELECT pf.uuid,
                   pf.data,
                   p.pipeline_uuid
            FROM processor_filter pf
            JOIN processor p ON p.id = pf.fk_processor_id
            WHERE pf.deleted = 0
            AND pf.data IS NOT NULL
            """;

    /**
     * Resolve a pipeline UUID to its document name.
     * Runs against the docstore database.
     */
    private static final String SELECT_PIPELINE_NAME = """
            SELECT name FROM doc
            WHERE uuid = ? AND deleted IS NULL
            """;

    /**
     * Insert a dependency edge.
     * Runs against the docstore database (where doc_dependency lives).
     * <p>
     * Uses ON DUPLICATE KEY UPDATE (a no-op self-assignment on the (from_uuid, to_uuid) unique key)
     * rather than INSERT IGNORE so the migration is idempotent if re-run, without silently swallowing
     * genuine errors (e.g. data truncation) the way INSERT IGNORE would.
     */
    private static final String INSERT_DEPENDENCY = """
            INSERT INTO doc_dependency
                (from_type, from_uuid, from_name, to_type, to_uuid, to_name)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE from_type = from_type
            """;

    private final ProcessorDbConnProvider processorDbConnProvider;
    private final DocStoreDbConnProvider docStoreDbConnProvider;

    @Inject
    public V07_14_00_005__populate_doc_dependency_processor_filters(
            final ProcessorDbConnProvider processorDbConnProvider,
            final DocStoreDbConnProvider docStoreDbConnProvider) {
        this.processorDbConnProvider = processorDbConnProvider;
        this.docStoreDbConnProvider = docStoreDbConnProvider;
    }

    @Override
    public void migrate(final Context context) throws Exception {
        final ObjectMapper objectMapper = new ObjectMapper();

        int filterCount = 0;
        int edgeCount = 0;
        int errorCount = 0;

        // Read from the processor DB, write to the docstore DB
        try (final Connection processorConn = processorDbConnProvider.getConnection();
                final Connection docStoreConn = docStoreDbConnProvider.getConnection();
                final PreparedStatement selectStmt = processorConn.prepareStatement(SELECT_PROCESSOR_FILTERS);
                final PreparedStatement nameStmt = docStoreConn.prepareStatement(SELECT_PIPELINE_NAME);
                final PreparedStatement insertStmt = docStoreConn.prepareStatement(INSERT_DEPENDENCY);
                final ResultSet rs = selectStmt.executeQuery()) {

            while (rs.next()) {
                final String filterUuid = rs.getString(1);
                final String queryDataJson = rs.getString(2);
                final String pipelineUuid = rs.getString(3);

                filterCount++;

                try {
                    final Set<DocRef> refs = new HashSet<>();

                    // Walk the QueryData JSON for DocRefs (dataSource, expression docRefs)
                    if (queryDataJson != null && !queryDataJson.isBlank()) {
                        final JsonNode root = objectMapper.readTree(queryDataJson);
                        DocRefFinder.findDocRefs(root, filterUuid, refs);
                    }

                    // Add the pipeline as an explicit dependency (stored as a bare UUID string)
                    if (pipelineUuid != null && !pipelineUuid.isBlank()) {
                        final String pipelineName = resolvePipelineName(nameStmt, pipelineUuid);
                        refs.add(new DocRef(PIPELINE_TYPE, pipelineUuid, pipelineName));
                    }

                    // Insert edges — processor filters don't have display names
                    for (final DocRef ref : refs) {
                        insertStmt.setString(1, PROCESSOR_FILTER_TYPE);
                        insertStmt.setString(2, filterUuid);
                        insertStmt.setString(3, "");
                        insertStmt.setString(4, DocRefFinder.safeStr(ref.getType()));
                        insertStmt.setString(5, ref.getUuid());
                        insertStmt.setString(6, DocRefFinder.safeStr(ref.getName()));
                        insertStmt.addBatch();
                        edgeCount++;
                    }

                    // Execute batch periodically
                    if (filterCount % 500 == 0) {
                        insertStmt.executeBatch();
                        LOGGER.info("ProcessorFilters: processed {} filters, {} edges so far",
                                filterCount, edgeCount);
                    }
                } catch (final Exception e) {
                    errorCount++;
                    LOGGER.error(() ->
                            "Error extracting dependencies from ProcessorFilter (" +
                            filterUuid + "): " + e.getMessage(), e);
                }
            }

            // Execute remaining batch
            insertStmt.executeBatch();
        }

        LOGGER.info("doc_dependency processor filter migration complete: " +
                    "processed={}, edges={}, errors={}",
                filterCount, edgeCount, errorCount);
    }

    /**
     * Look up a pipeline's display name from the doc table.
     * Returns empty string if the pipeline is not found (deleted or missing).
     */
    private String resolvePipelineName(final PreparedStatement nameStmt,
                                       final String pipelineUuid) throws Exception {
        nameStmt.setString(1, pipelineUuid);
        try (final ResultSet rs = nameStmt.executeQuery()) {
            if (rs.next()) {
                return DocRefFinder.safeStr(rs.getString(1));
            }
        }
        return "";
    }
}
