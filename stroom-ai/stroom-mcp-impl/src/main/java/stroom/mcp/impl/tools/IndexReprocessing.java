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

import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.MergedPipeline.ElementProperty;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// What an indexing pipeline's documentation says of how Stroom writes its documents and what reprocessing does, from
/// the pipeline's own indexing filter and its settings, so it is stated as Stroom does it. Seen in chat 177: the agent
/// wrote "The document _id is <StreamId>-<EventId>, so re-indexing the same Events stream replaces its documents",
/// which is false: Stroom's ElasticIndexingFilter sends bulk `create` operations with no id (Elasticsearch assigns
/// one), and only a reprocess processor filter, with `purgeOnReprocess` (true by default), first deletes the stream's
/// documents by StreamId. Stroom's Lucene IndexingFilter (and DynamicIndexingFilter) only adds documents.
final class IndexReprocessing {

    /// Elasticsearch's indexing filter.
    static final String ELASTIC_FILTER = "ElasticIndexingFilter";
    /// Its property saying whether a reprocess deletes the stream's documents first.
    static final String PURGE_ON_REPROCESS = "purgeOnReprocess";
    /// Lucene's indexing filters.
    static final List<String> LUCENE_FILTERS = List.of("IndexingFilter", "DynamicIndexingFilter");

    private IndexReprocessing() {
        // Static only.
    }

    /// @param merged The indexing pipeline, as Stroom runs it.
    /// @return The paragraph, or "" for a pipeline with no indexing filter.
    static String note(final MergedPipeline merged) {
        return merged == null
                ? ""
                : note(merged.elements(), merged.properties());
    }

    /// @param elements   Each element's type by its id.
    /// @param properties Each property's value by its element and name.
    /// @return The paragraph, or "" for a pipeline with no indexing filter.
    static String note(final Map<String, String> elements, final Map<ElementProperty, Object> properties) {
        final List<String> elastic = new ArrayList<>();
        boolean lucene = false;
        for (final Map.Entry<String, String> element : NullSafe.map(elements).entrySet()) {
            if (ELASTIC_FILTER.equals(element.getValue())) {
                elastic.add(element.getKey());
            } else if (LUCENE_FILTERS.contains(element.getValue())) {
                lucene = true;
            }
        }
        if (!elastic.isEmpty()) {
            final boolean purges = elastic.stream()
                    .allMatch(id -> !Boolean.FALSE.equals(purgeSetting(properties, id)));
            return "Reprocessing: Stroom writes each document as a bulk `create` with no id, so Elasticsearch "
                   + "assigns it; no document id is derived from the event. "
                   + (purges
                    ? "A reprocess processor filter first deletes the index's documents of each stream it "
                      + "reprocesses (by StreamId: the indexing filter's `" + PURGE_ON_REPROCESS + "` is true), "
                      + "then indexes the stream afresh. A stream processed again outside a reprocess filter is "
                      + "indexed again beside its earlier documents, so they are there twice."
                    : "The indexing filter's `" + PURGE_ON_REPROCESS + "` is false, so reprocessing a stream "
                      + "deletes nothing: its documents are indexed again beside the earlier ones, so they are "
                      + "there twice unless those are deleted first.");
        }
        if (lucene) {
            return "Reprocessing: Stroom's Lucene indexing adds each event's document to the index's shards and "
                   + "deletes none, whether the stream is reprocessed or not, so a stream indexed again is there "
                   + "twice until the shards holding its documents are deleted.";
        }
        return "";
    }

    // The element's purgeOnReprocess, or null when it isn't set (Stroom's default: true).
    private static Object purgeSetting(final Map<ElementProperty, Object> properties, final String element) {
        final Object value = NullSafe.map(properties).get(new ElementProperty(element, PURGE_ON_REPROCESS));
        if (value instanceof final String text) {
            return Boolean.parseBoolean(text.strip());
        }
        return value;
    }
}
