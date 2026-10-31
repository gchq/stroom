/*
 * Copyright 2026 Crown Copyright
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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.IndexSearches;
import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.FieldPlans;
import stroom.ai.impl.tools.indexing.PlannedField;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Example values an index holds for fields the documentation's sample doesn't show, so a reader sees what kind of
/// data each holds (asked for by the user; not in the Stroom MCP server). One search of the index the pipeline
/// writes to, through Stroom's dashboard search as the user, bounded in time and rows; anything it can't do gives
/// no values, as they are a nicety.
class IndexValues {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexValues.class);

    /// The most values shown of a field.
    static final int MAX_VALUES = 3;
    /// The most documents read.
    static final int MAX_ROWS = 200;
    /// The longest the search is polled.
    static final Duration LONGEST = Duration.ofSeconds(20);
    // The plan's types a wildcard EQUALS finds any value of, by backend: on Elasticsearch keyword and text fields
    // (Stroom finds nothing on an ip field so); on Lucene those it keeps as terms (TEXT with the KEYWORD analyzer:
    // keyword, ip, boolean), not an analysed text field, nor a number or date, which take no wildcard.
    private static final Map<String, Set<String>> WILDCARD_TYPES = Map.of(
            FieldPlan.ELASTICSEARCH, Set.of("keyword", "text"),
            FieldPlan.LUCENE, Set.of("keyword", "ip", "boolean"));

    private final IndexDocs indexDocs;
    private final IndexSearches indexSearches;

    /// @param indexDocs     Finds the index a pipeline writes to.
    /// @param indexSearches Searches it, as the user.
    @Inject
    IndexValues(final IndexDocs indexDocs, final IndexSearches indexSearches) {
        this.indexDocs = indexDocs;
        this.indexSearches = indexSearches;
    }

    /// @param context The call.
    /// @param merged  The indexing pipeline, as Stroom runs it.
    /// @param plan    Its plan.
    /// @param fields  The plan's fields to find values of.
    /// @return Up to [#MAX_VALUES] distinct values of each field the index holds any of, in the order found;
    /// none when there are no fields, the pipeline writes to no index the user can read, or the search fails.
    Map<String, List<String>> examples(final ToolCallContext context,
                                       final MergedPipeline merged,
                                       final FieldPlan plan,
                                       final List<String> fields) {
        final Map<String, List<String>> out = new LinkedHashMap<>();
        if (NullSafe.isEmptyCollection(fields)) {
            return out;
        }
        try {
            final List<IndexDocs.Index> written = indexDocs.written(merged);
            if (written.isEmpty()) {
                return out;
            }
            final IndexDocs.Index index = written.getFirst();
            // The fields a wildcard finds a value of, searched for documents holding any; the rest in the first
            // documents read.
            final List<String> wild = wildcarded(index.backend(), plan, fields);
            final List<String> rest = fields.stream().filter(field -> !wild.contains(field)).toList();
            read(context, index, wild, anyOf(wild), out);
            read(context, index, rest, ExpressionOperator.builder().build(), out);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No example values read from the index: {}", e.getMessage()), e);
            out.clear();
        }
        return out;
    }

    private void read(final ToolCallContext context,
                      final IndexDocs.Index index,
                      final List<String> fields,
                      final ExpressionOperator expression,
                      final Map<String, List<String>> out) {
        if (fields.isEmpty()) {
            return;
        }
        final IndexSearches.Probe probe = new IndexSearches.Probe(index.docRef(), "field examples", expression,
                fields.stream().map(IndexSearches::column).toList(), MAX_ROWS);
        final IndexSearches.Found found = indexSearches.search(context, probe, MAX_ROWS,
                "Reading example values of " + fields.size() + " field(s) from " + NullSafe.nonBlankStringElse(
                        index.indexName(), index.docRef().getName()), LONGEST);
        for (final Map<String, String> row : found.rows()) {
            for (final String field : fields) {
                final String value = row.get(field);
                if (NullSafe.isNonBlankString(value)) {
                    final List<String> values = out.computeIfAbsent(field, k -> new ArrayList<>());
                    if (values.size() < MAX_VALUES && !values.contains(value)) {
                        values.add(value);
                    }
                }
            }
        }
    }

    /// @param backend `elasticsearch` or `lucene`.
    /// @param plan    The plan, which types each field.
    /// @param fields  Fields of it.
    /// @return Those a wildcard EQUALS finds any value of on the backend.
    static List<String> wildcarded(final String backend, final FieldPlan plan, final List<String> fields) {
        final Map<String, String> types = new LinkedHashMap<>();
        plan.leafFields().forEach(field -> types.put(field.name(), field.type()));
        final Set<String> wild = WILDCARD_TYPES.getOrDefault(backend, Set.of());
        return fields.stream().filter(field -> wild.contains(types.get(field))).toList();
    }

    /// @param fields Fields a wildcard EQUALS finds any value of.
    /// @return The documents holding any of them: EQUALS '*' on each, ORed.
    static ExpressionOperator anyOf(final List<String> fields) {
        final ExpressionOperator.Builder any = ExpressionOperator.builder().op(Op.OR);
        fields.forEach(field -> any.addTerm(IndexChecks.term(field, Condition.EQUALS, "*")));
        return any.build();
    }

    /// @param plan       The plan.
    /// @param population The share of the sample's events (%) each path is populated in.
    /// @return The plan's fields whose source no event of the sample has (not the ids, nor a shared template's).
    static List<String> notInSample(final FieldPlan plan, final Map<String, Double> population) {
        final List<String> out = new ArrayList<>();
        for (final PlannedField field : plan.leafFields()) {
            final String source = field.source();
            if (NullSafe.isBlankString(source) || source.startsWith("@") || plan.writtenBy(field.name()) != null) {
                continue;
            }
            final Double share = FieldPlans.populationOf(source, population);
            if (share == null || share == 0) {
                out.add(field.name());
            }
        }
        return out;
    }
}
