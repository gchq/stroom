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

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.ProcessorFilterAccess;
import stroom.ai.impl.tools.StreamReader;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/// What a pipeline's processor filters read: each filter's conditions, the feeds they select (named, by reference,
/// in a Dictionary and the dictionaries it imports, or through a sample stream's id), the stream types, and the
/// pipelines whose output they read (an indexing pipeline's events pipeline, with the feeds that one reads).
///
/// Seen: asked which feeds a pipeline acts on, an agent found its filters' conditions unreadable (a dictionary of
/// feeds showed only as 'Feed IN_DICTIONARY'), and fell back on a keyword search for documents.
///
/// Read as the user: filters need the Manage Processors permission and View on their pipeline, and dictionaries
/// and streams are only read where the user may.
class PipelineFeeds {

    static final String FEED = "Feed";
    static final String TYPE = "Type";
    static final String PIPELINE = "Pipeline";
    static final String ID = "Id";
    /// How far up the pipelines whose output a filter reads are followed.
    static final int MAX_UPSTREAM_DEPTH = 2;
    /// How deep dictionary imports are followed.
    static final int MAX_IMPORT_DEPTH = 5;

    private final ProcessorFilterAccess processorFilterAccess;
    private final DocumentAccess documentAccess;
    private final StreamReader streamReader;

    @Inject
    PipelineFeeds(final ProcessorFilterAccess processorFilterAccess,
                  final DocumentAccess documentAccess,
                  final StreamReader streamReader) {
        this.processorFilterAccess = processorFilterAccess;
        this.documentAccess = documentAccess;
        this.streamReader = streamReader;
    }

    /// @param pipelineUuid The pipeline.
    /// @return What its filters read, as [#pipelineFeeds(String, int, List)].
    Map<String, Object> pipelineFeeds(final String pipelineUuid) {
        return pipelineFeeds(pipelineUuid, 0, null);
    }

    /// @param pipelineUuid The pipeline.
    /// @param depth        How far up this pipeline is from the one asked about.
    /// @param filters      Its filters, if already listed; else null to list them.
    /// @return `filters` (each `id`, `enabled`, `selects`, `feeds`, and any `stream_types`,
    /// `feeds_from_dictionaries` and `output_of`), `feeds`, and any `reads_output_of` and `note`; or
    /// `filters_unreadable` saying why they couldn't be listed. As the Stroom MCP server gave them.
    Map<String, Object> pipelineFeeds(final String pipelineUuid,
                                      final int depth,
                                      final List<ProcessorFilter> filters) {
        final List<ProcessorFilter> listed;
        if (filters != null) {
            listed = filters;
        } else {
            final ProcessorFilterAccess.Filters found = processorFilterAccess.filtersOf(pipelineRef(pipelineUuid));
            if (found.refused() != null) {
                return Map.of("filters_unreadable", "Stroom refused to list this pipeline's processor filters: "
                                                    + found.refused() + ". Listing them needs permission to view "
                                                    + "the pipeline's processors.");
            }
            listed = found.filters();
        }
        final Map<Object, Object> known = new HashMap<>();
        final List<Map<String, Object>> out = new ArrayList<>();
        final Set<String> feeds = new TreeSet<>();
        final List<String> sources = new ArrayList<>();
        for (final ProcessorFilter filter : listed) {
            final ExpressionOperator expression = NullSafe.get(filter.getQueryData(), QueryData::getExpression);
            final List<ExpressionTerm> terms = filterTerms(expression);
            final List<String> read = List.copyOf(new TreeSet<>(filterFeeds(terms, known, false)));
            final List<String> types = terms.stream()
                    .filter(t -> TYPE.equals(t.getField()) && NullSafe.isNonEmptyString(t.getValue()))
                    .map(ExpressionTerm::getValue)
                    .toList();
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", filter.getId());
            entry.put("enabled", filter.isEnabled());
            entry.put("selects", terms(expression));
            entry.put("feeds", read);
            if (!types.isEmpty()) {
                entry.put("stream_types", types);
            }
            final List<String> dictionaries = terms.stream()
                    .filter(t -> Condition.IN_DICTIONARY.equals(t.getCondition()))
                    .map(t -> NullSafe.get(t.getDocRef(), DocRef::getName))
                    .filter(NullSafe::isNonEmptyString)
                    .toList();
            if (!dictionaries.isEmpty()) {
                entry.put("feeds_from_dictionaries", dictionaries);
            }
            for (final ExpressionTerm term : terms) {
                final DocRef ref = term.getDocRef();
                if (!PIPELINE.equals(term.getField()) || ref == null || ref.getUuid() == null) {
                    continue;
                }
                // Events written by another pipeline: the feeds that one reads are what this pipeline acts on in
                // the end.
                final Map<String, Object> above = depth < MAX_UPSTREAM_DEPTH
                        ? pipelineFeeds(ref.getUuid(), depth + 1, null)
                        : Map.of();
                final List<String> itsFeeds = feedsOf(above);
                final Map<String, Object> upstream = new LinkedHashMap<>();
                upstream.put("pipeline", ref.getName());
                upstream.put("uuid", ref.getUuid());
                upstream.put("its_feeds", itsFeeds);
                outputOf(entry).add(upstream);
                sources.add(ref.getName());
                feeds.addAll(itsFeeds);
            }
            feeds.addAll(read);
            out.add(entry);
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("filters", out);
        result.put("feeds", List.copyOf(feeds));
        if (!sources.isEmpty()) {
            result.put("reads_output_of", sources);
        }
        if (out.isEmpty()) {
            result.put("note", "No processor filters: this pipeline processes nothing on its own (it may be a "
                               + "template others inherit, or only stepped).");
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> outputOf(final Map<String, Object> entry) {
        return (List<Map<String, Object>>) entry.computeIfAbsent("output_of", k -> new ArrayList<>());
    }

    @SuppressWarnings("unchecked")
    private static List<String> feedsOf(final Map<String, Object> described) {
        return described.get("feeds") instanceof final List<?> list
                ? (List<String>) list
                : List.of();
    }

    private DocRef pipelineRef(final String uuid) {
        return documentAccess.findDocRef(uuid)
                .filter(docRef -> PipelineDoc.TYPE.equals(docRef.getType()))
                .orElse(null);
    }

    /// @param expression A processor filter expression, or null.
    /// @return Its terms, at any depth.
    static List<ExpressionTerm> filterTerms(final ExpressionItem expression) {
        final List<ExpressionTerm> out = new ArrayList<>();
        if (expression instanceof final ExpressionOperator operator) {
            for (final ExpressionItem child : NullSafe.list(operator.getChildren())) {
                out.addAll(child instanceof ExpressionOperator
                        ? filterTerms(child)
                        : child instanceof final ExpressionTerm term
                                ? List.of(term)
                                : List.of());
            }
        }
        return out;
    }

    /// The feeds a processor filter reads: Feed EQUALS (one, or several between commas), IS_DOC_REF (the feed doc:
    /// its name is in the reference, with no value), IN_DICTIONARY (the feeds a Dictionary lists, and its
    /// imports'), or with no Feed term, the feed of the first stream its Id terms pick (filters on sample
    /// streams). Seen on a production Stroom: 34 filters named their feed by IS_DOC_REF and 6 by a dictionary,
    /// which reading the value alone missed.
    ///
    /// @param terms The filter's terms.
    /// @param known Caches dictionaries and streams across one walk of the filters.
    /// @param lower True for the names lower-cased; false to keep them as written, to show.
    /// @return The feeds.
    Set<String> filterFeeds(final List<ExpressionTerm> terms, final Map<Object, Object> known, final boolean lower) {
        final Set<String> feeds = new LinkedHashSet<>();
        for (final ExpressionTerm term : terms) {
            if (!FEED.equals(term.getField())) {
                continue;
            }
            final DocRef ref = term.getDocRef();
            if (Condition.IS_DOC_REF.equals(term.getCondition())) {
                final String name = NullSafe.nonBlankStringElse(NullSafe.get(ref, DocRef::getName), term.getValue());
                if (NullSafe.isNonBlankString(name)) {
                    feeds.add(cased(name, lower));
                }
            } else if (Condition.IN_DICTIONARY.equals(term.getCondition()) && ref != null && ref.getUuid() != null) {
                feeds.addAll(dictionaryLines(ref.getUuid(), known, 0, lower));
            } else {
                for (final String value : NullSafe.string(term.getValue()).split(",")) {
                    if (!value.isBlank()) {
                        feeds.add(cased(value.strip(), lower));
                    }
                }
            }
        }
        final List<String> ids = terms.stream()
                .filter(t -> ID.equals(t.getField()) && Condition.EQUALS.equals(t.getCondition())
                             && NullSafe.isNonBlankString(t.getValue()))
                .map(ExpressionTerm::getValue)
                .toList();
        if (feeds.isEmpty() && !ids.isEmpty()) {
            final List<String> key = List.of("stream", ids.getFirst());
            if (!known.containsKey(key)) {
                known.put(key, streamFeed(ids.getFirst()).orElse(null));
            }
            final Object feed = known.get(key);
            if (feed != null) {
                feeds.add(cased(String.valueOf(feed), lower));
            }
        }
        return feeds;
    }

    private Optional<String> streamFeed(final String id) {
        try {
            return Optional.ofNullable(streamReader.meta(Long.parseLong(id.strip()))).map(Meta::getFeedName);
        } catch (final RuntimeException e) {
            return Optional.empty();
        }
    }

    // A Dictionary's lines, lower-cased (or as written), with those of the dictionaries it imports.
    @SuppressWarnings("unchecked")
    private Set<String> dictionaryLines(final String uuid,
                                        final Map<Object, Object> known,
                                        final int depth,
                                        final boolean lower) {
        final List<String> key = List.of(lower
                ? "dictionary"
                : "dictionary as written", uuid);
        if (!known.containsKey(key)) {
            // A dictionary importing itself, or one another imports, read once.
            known.put(key, Set.of());
            final Optional<DictionaryDoc> doc = documentAccess.read(DictionaryDoc.getDocRef(uuid),
                    DictionaryDoc.class);
            if (doc.isEmpty()) {
                return Set.of();
            }
            final Set<String> lines = new LinkedHashSet<>();
            for (final String line : NullSafe.string(doc.get().getData()).split("\\R")) {
                if (!line.isBlank()) {
                    lines.add(cased(line.strip(), lower));
                }
            }
            if (depth < MAX_IMPORT_DEPTH) {
                for (final DocRef imported : NullSafe.list(doc.get().getImports())) {
                    if (imported != null && imported.getUuid() != null) {
                        lines.addAll(dictionaryLines(imported.getUuid(), known, depth + 1, lower));
                    }
                }
            }
            known.put(key, lines);
        }
        return (Set<String>) known.get(key);
    }

    private static String cased(final String value, final boolean lower) {
        return lower
                ? value.toLowerCase(Locale.ROOT)
                : value;
    }

    /// @param expression A processor filter expression, or null.
    /// @return Its terms in words, e.g. `Feed EQUALS ACME` or `OR(Type EQUALS Events, ...)`; a document by
    /// reference (Feed or Pipeline IS_DOC_REF) shows its name.
    static List<String> terms(final ExpressionOperator expression) {
        final List<String> out = new ArrayList<>();
        if (expression == null) {
            return out;
        }
        for (final ExpressionItem child : NullSafe.list(expression.getChildren())) {
            if (child instanceof final ExpressionOperator operator) {
                out.add(NullSafe.getOrElse(operator.getOp(), Enum::name, "AND") + "("
                        + String.join(", ", terms(operator)) + ")");
            } else if (child instanceof final ExpressionTerm term) {
                final String value = term.getValue() != null
                        ? term.getValue()
                        : NullSafe.get(term.getDocRef(), DocRef::getName);
                out.add(term.getField() + " " + NullSafe.get(term.getCondition(), Enum::name) + " " + value);
            }
        }
        return out;
    }
}
