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

package stroom.ai.impl.tools;

import stroom.ai.impl.tools.IndexSearches.Counted;
import stroom.ai.impl.tools.IndexSearches.Found;
import stroom.ai.impl.tools.IndexSearches.Probe;
import stroom.ai.impl.tools.validation.CloseMatches;
import stroom.ai.impl.workspace.ToolDeadline;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.docref.DocRef;
import stroom.index.shared.LuceneIndexDoc;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.query.api.Column;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.Format;
import stroom.query.api.Sort;
import stroom.query.api.Sort.SortDirection;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/// Finds events in an index through Stroom, as the user would on a dashboard (with their permissions; nothing is
/// saved): for an event the user describes but can't point at (ported from the Stroom MCP server's find_events and
/// `_affected`, asked for by the user: one rare event among many indexed streams, which sampling would miss). Each
/// hit has its Events stream's StreamId and its EventId; with how many events match in all, and in which streams;
/// and, for every stream, what a translation change to them reaches. Searches only fields the index has, and
/// refuses searches whose answer would mislead ([MisleadingSearches]). search_events does this when given an index.
public class IndexEvents {

    /// The most columns a hit has.
    static final int FIND_COLUMNS = 8;
    /// The Events streams listed with allStreams.
    static final int ALL_STREAMS = 10_000;
    /// The Events streams listed otherwise, those with the most matches.
    static final int STREAMS = 20;
    /// The most hits.
    static final int MAX_HITS = 20;
    /// The hits given unless asked.
    static final int DEFAULT_HITS = 10;
    /// The most characters of a hit's value.
    static final int VALUE_CHARS = 120;
    /// The most fields read of an index.
    static final int MAX_FIELDS = 2_000;
    /// The fields named when one asked for isn't the index's.
    static final int FIELDS_NAMED = 60;
    /// Streams looked up at a time for their raw streams.
    static final int META_BATCH = 500;
    /// The conditions a search takes, as a person sets them on a dashboard.
    static final List<Condition> CONDITIONS = List.of(Condition.EQUALS, Condition.NOT_EQUALS, Condition.CONTAINS,
            Condition.STARTS_WITH, Condition.ENDS_WITH, Condition.MATCHES_REGEX, Condition.GREATER_THAN,
            Condition.GREATER_THAN_OR_EQUAL_TO, Condition.LESS_THAN, Condition.LESS_THAN_OR_EQUAL_TO,
            Condition.BETWEEN, Condition.IN, Condition.IS_NULL, Condition.IS_NOT_NULL);

    private static final String STREAM_ID = "StreamId";
    private static final String EVENT_ID = "EventId";
    private static final List<String> IDS = List.of(STREAM_ID, EVENT_ID);

    private final DataSourceAccess dataSourceAccess;
    private final Provider<QueryService> queryServiceProvider;
    private final DocumentAccess documentAccess;
    private final IndexSearches indexSearches;
    private final Provider<MetaService> metaServiceProvider;

    /// @param dataSourceAccess     Finds the index, checking Use.
    /// @param queryServiceProvider Lists its fields.
    /// @param documentAccess       Reads an Elastic Index doc's index name, checking View.
    /// @param indexSearches        Runs the searches, as the user.
    /// @param metaServiceProvider  Finds the raw streams the Events streams were made from (only those of feeds
    ///                             the user may view).
    @Inject
    IndexEvents(final DataSourceAccess dataSourceAccess,
                final Provider<QueryService> queryServiceProvider,
                final DocumentAccess documentAccess,
                final IndexSearches indexSearches,
                final Provider<MetaService> metaServiceProvider) {
        this.dataSourceAccess = dataSourceAccess;
        this.queryServiceProvider = queryServiceProvider;
        this.documentAccess = documentAccess;
        this.indexSearches = indexSearches;
        this.metaServiceProvider = metaServiceProvider;
    }

    /// Finds the events in an index that meet the conditions.
    ///
    /// @param context The call, told what each search is doing as it runs.
    /// @param find    What to find.
    /// @return find_events' result: `{index, searched, total, hits, by_stream, affected?, errors?, next}`.
    /// @throws WorkspaceException saying why, for an index that isn't one the user can use, a field it doesn't
    ///                            have, a search whose answer would mislead, or nothing to search for.
    public Map<String, Object> find(final ToolCallContext context, final Find find) {
        final DocRef ref = index(find.index());
        final ElasticIndexDoc elastic = ElasticIndexDoc.TYPE.equals(ref.getType())
                ? documentAccess.read(ref, ElasticIndexDoc.class).orElse(null)
                : null;
        final List<QueryField> info = fields(ref);
        final Set<String> listed = new LinkedHashSet<>(info.stream().map(QueryField::getFldName).toList());
        final String backend = MisleadingSearches.backendOf(ref).orElse(MisleadingSearches.LUCENE);
        List<Term> terms = find.where();
        final Map<String, String> unindexed = MisleadingSearches.unindexed(ref, info,
                terms.stream().map(Term::field).toList());
        refuseUnknown(ref, listed, terms, find.fields());
        if (MisleadingSearches.ELASTICSEARCH.equals(backend)) {
            // Any value or none, which IS_NOT_NULL and IS_NULL mean: Stroom matches every document with them on
            // Elasticsearch.
            terms = terms.stream()
                    .map(term -> MisleadingSearches.elasticsearchNullAs(term.condition())
                            .map(condition -> new Term(term.field(), condition, "*"))
                            .orElse(term))
                    .toList();
        }
        final Optional<String> refusal = MisleadingSearches.refusal(backend, terms.stream()
                .map(term -> new MisleadingSearches.Search(term.field(), term.condition(), term.value()))
                .toList(), MisleadingSearches.ipFields(info), List.of(), Map.of(), unindexed);
        if (refusal.isPresent()) {
            throw new WorkspaceException(refusal.get());
        }

        final String timeField = dataSourceAccess.timeField(ref).map(QueryField::getFldName).orElse(null);
        final List<Term> children = new ArrayList<>(terms);
        if (find.timeFrom() != null || find.timeTo() != null) {
            if (timeField == null) {
                throw new WorkspaceException("Index '" + ref.getName() + "' has no time field to search a time "
                                             + "range on");
            }
            children.add(new Term(timeField, Condition.BETWEEN,
                    NullSafe.nonBlankStringElse(find.timeFrom(), "1970-01-01T00:00:00.000Z") + ","
                    + NullSafe.nonBlankStringElse(find.timeTo(), "day()+1d")));
        }
        if (children.isEmpty()) {
            throw new WorkspaceException("Give where (the conditions the event meets) or a time range: this finds "
                                         + "an event, it doesn't list the index");
        }
        final ExpressionOperator expression = expression(children);
        final List<Column> columns = shown(listed, timeField, terms, find.fields()).stream()
                .map(name -> name.equals(timeField)
                        ? IndexSearches.column(name).copy()
                                .sort(new Sort(0, SortDirection.DESCENDING)).build()
                        : IndexSearches.column(name))
                .toList();
        final String label = elastic != null && NullSafe.isNonBlankString(elastic.getIndexName())
                ? elastic.getIndexName()
                : ref.getName();
        final List<String> searchedEach = children.stream().map(Term::said).toList();
        final String searched = String.join(" AND ", children.stream().map(term -> term.said().stripTrailing())
                .toList());
        final Probe probe = new Probe(ref, ref.getName() + " search", expression, columns, find.limit());
        final Found found = indexSearches.search(context, probe, find.limit(),
                "Searching " + label + " for " + searched);
        final Counted counted = indexSearches.count(context, probe,
                "Counting the events in " + label + " matching " + searched);
        final List<Map<String, String>> hits = found.rows().stream()
                .limit(find.limit())
                .map(IndexEvents::shortened)
                .toList();
        List<Map<String, Object>> byStream = List.of();
        Map<String, Object> affected = null;
        if (listed.contains(STREAM_ID)) {
            byStream = byStream(context, probe, label, find.allStreams()
                    ? ALL_STREAMS
                    : STREAMS);
            if (find.allStreams()) {
                affected = affected(elastic == null
                        ? null
                        : elastic.getIndexName(), byStream, counted.count());
                byStream = byStream.subList(0, Math.min(STREAMS, byStream.size()));
            }
        }
        final Set<String> errors = new LinkedHashSet<>(found.errors());
        errors.addAll(counted.errors());

        final Map<String, Object> indexRef = new LinkedHashMap<>();
        indexRef.put("type", ref.getType());
        indexRef.put("uuid", ref.getUuid());
        indexRef.put("name", ref.getName());
        indexRef.put("backend", backend);
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("index", indexRef);
        result.put("searched", searchedEach);
        result.put("total", counted.count() != null
                ? counted.count()
                : (long) hits.size());
        result.put("hits", hits);
        result.put("by_stream", byStream);
        if (affected != null) {
            result.put("affected", affected);
        }
        if (!errors.isEmpty()) {
            result.put("errors", List.copyOf(errors));
        }
        result.put("next", next(hits, listed));
        return result;
    }

    /// What a translation change to these events reaches: the Events streams holding them, the raw streams they
    /// were made from (to reprocess, the user's to do), and for Elasticsearch the request that deletes their old
    /// documents before they are indexed again (or they are indexed twice).
    ///
    /// @param indexName An Elastic Index doc's index name, or null for a Lucene index.
    /// @param byStream  The Events streams holding them, each `{stream_id, events}`.
    /// @param total     How many events there are, or null if Stroom gave no count.
    /// @return `{events, events_streams, raw_streams, reprocess, note?, elasticsearch_delete?, delete_when?}`.
    Map<String, Object> affected(final String indexName,
                                 final List<Map<String, Object>> byStream,
                                 final Long total) {
        final List<Long> ids = new ArrayList<>();
        for (final Map<String, Object> row : byStream) {
            try {
                ids.add((long) Double.parseDouble(String.valueOf(row.get("stream_id"))));
            } catch (final NumberFormatException e) {
                // Not a stream id: left out.
            }
        }
        final Set<Long> raw = new TreeSet<>();
        final MetaService metaService = metaServiceProvider.get();
        for (int start = 0; start < ids.size(); start += META_BATCH) {
            ToolDeadline.check();
            final List<Long> batch = ids.subList(start, Math.min(ids.size(), start + META_BATCH));
            final FindMetaCriteria criteria = new FindMetaCriteria(ExpressionOperator.builder().op(Op.AND)
                    .addTerm(MetaFields.ID.getFldName(), Condition.IN, ProcessorTasksTool.ids(batch))
                    .build());
            criteria.setPageRequest(new PageRequest(0, batch.size()));
            final ResultPage<Meta> found = metaService.find(criteria);
            for (final Meta meta : NullSafe.list(NullSafe.get(found, ResultPage::getValues))) {
                if (meta != null && meta.getParentMetaId() != null) {
                    raw.add(meta.getParentMetaId());
                }
            }
        }
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("events", total);
        out.put("events_streams", ids);
        out.put("raw_streams", List.copyOf(raw));
        out.put("reprocess", "The raw streams are the user's to reprocess, once the change is promoted: exactly "
                             + "these, not the whole feed.");
        if (ids.size() >= ALL_STREAMS) {
            out.put("note", "Stopped at " + ALL_STREAMS + " Events streams: narrow the time range and search "
                            + "again for the rest.");
        }
        if (NullSafe.isNonBlankString(indexName)) {
            out.put("elasticsearch_delete", "POST " + indexName + "/_delete_by_query {\"query\": {\"terms\": "
                                            + "{\"StreamId\": [" + String.join(", ", ids.stream()
                    .map(String::valueOf).toList()) + "]}}}");
            out.put("delete_when", "The cluster's admin runs it just before the raw streams are reprocessed (the "
                                   + "new Events are then indexed again): otherwise the index holds these events "
                                   + "twice.");
        }
        return out;
    }

    // The Events streams holding the matches, with how many each, the most first.
    private List<Map<String, Object>> byStream(final ToolCallContext context,
                                               final Probe probe,
                                               final String label,
                                               final int streams) {
        final Found grouped = indexSearches.search(context, probe.withColumns(List.of(
                IndexSearches.column(STREAM_ID).copy().group(0).build(),
                Column.builder().id("mcp-n").name("events").expression("count()").format(Format.NUMBER)
                        .visible(true).sort(new Sort(0, SortDirection.DESCENDING)).build()), streams),
                streams, "Grouping the matching events in " + label + " by stream");
        return grouped.rows().stream()
                .map(row -> {
                    final Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("stream_id", row.get(STREAM_ID));
                    entry.put("events", row.get("events"));
                    return entry;
                })
                .toList();
    }

    // The index doc: an Elastic Index or a Lucene Index the user can use.
    private DocRef index(final String given) {
        final List<DocRef> found = dataSourceAccess.find(given).stream()
                .filter(docRef -> ElasticIndexDoc.TYPE.equals(docRef.getType())
                                  || LuceneIndexDoc.TYPE.equals(docRef.getType()))
                .toList();
        if (found.isEmpty()) {
            throw new WorkspaceException("No Elastic Index or Lucene Index doc with UUID or name '" + given
                                         + "' that you can use");
        }
        if (found.size() > 1) {
            final StringBuilder sb = new StringBuilder("Several indexes you can use are called '").append(given)
                    .append("'. Give the uuid of the one you mean:");
            found.forEach(docRef -> sb.append("\n- ").append(DataSourceAccess.describe(docRef)));
            throw new WorkspaceException(sb.toString());
        }
        return found.getFirst();
    }

    // The fields Stroom has for the index doc.
    private List<QueryField> fields(final DocRef ref) {
        final ResultPage<QueryField> found = queryServiceProvider.get().findFields(new FindFieldCriteria(
                new PageRequest(0, MAX_FIELDS), FindFieldCriteria.DEFAULT_SORT_LIST, ref, null, null));
        return NullSafe.list(NullSafe.get(found, ResultPage::getValues)).stream()
                .filter(field -> field != null && NullSafe.isNonEmptyString(field.getFldName()))
                .toList();
    }

    // Refuses fields the index doesn't have, naming the ones most like each.
    private static void refuseUnknown(final DocRef ref,
                                      final Set<String> listed,
                                      final List<Term> terms,
                                      final List<String> fields) {
        final Set<String> unknown = new LinkedHashSet<>();
        terms.forEach(term -> unknown.add(term.field()));
        unknown.addAll(fields);
        unknown.removeIf(listed::contains);
        if (unknown.isEmpty()) {
            return;
        }
        final List<String> names = new ArrayList<>(listed);
        final List<String> said = new ArrayList<>();
        for (final String name : unknown) {
            final List<String> near = CloseMatches.closeMatches(name, names, 3, 0.5);
            said.add(near.isEmpty()
                    ? name
                    : name + " (like " + String.join(", ", near) + ")");
        }
        throw new WorkspaceException("Not fields of index '" + ref.getName() + "': " + String.join("; ", said)
                                     + ". Its fields: " + String.join(", ", new TreeSet<>(names).stream()
                .limit(FIELDS_NAMED).toList()));
    }

    // The columns each hit shows: its stream and event ids and the time, the fields searched and those asked for.
    private static List<String> shown(final Set<String> listed,
                                      final String timeField,
                                      final List<Term> terms,
                                      final List<String> fields) {
        final Set<String> shown = new LinkedHashSet<>();
        for (final String name : List.of(STREAM_ID, EVENT_ID)) {
            if (listed.contains(name)) {
                shown.add(name);
            }
        }
        if (timeField != null && listed.contains(timeField)) {
            shown.add(timeField);
        }
        terms.forEach(term -> shown.add(term.field()));
        shown.addAll(fields);
        return shown.stream().limit(FIND_COLUMNS).toList();
    }

    private static ExpressionOperator expression(final List<Term> children) {
        final List<ExpressionItem> items = new ArrayList<>();
        for (final Term term : children) {
            items.add(ExpressionTerm.builder()
                    .field(term.field())
                    .condition(term.condition())
                    .value(term.value())
                    .build());
        }
        return ExpressionOperator.builder().op(Op.AND).children(items).build();
    }

    private static Map<String, String> shortened(final Map<String, String> row) {
        final Map<String, String> hit = new LinkedHashMap<>();
        row.forEach((key, value) -> hit.put(key, value == null
                ? null
                : value.substring(0, Math.min(VALUE_CHARS, value.length()))));
        return hit;
    }

    private static String next(final List<Map<String, String>> hits, final Set<String> listed) {
        if (hits.isEmpty()) {
            return "Nothing matches. Check the conditions with the user (the values as the dashboard shows them; "
                   + "EQUALS takes * wildcards), or ask them for the event's StreamId and EventId from the "
                   + "dashboard row where they saw it.";
        } else if (!listed.containsAll(IDS)) {
            return "The index has no StreamId and EventId: ask the user for the Events stream and event id.";
        }
        return "Confirm with the user which hit is the event they mean (the hits are newest first), then "
               + "locate_event stream_id=<its StreamId> event_id=<its EventId> traces it to its raw record. "
               + "by_stream says which Events streams hold every match, with how many each.";
    }


    // --------------------------------------------------------------------------------


    /// What to find.
    ///
    /// @param index      The index doc's uuid or name: an Elastic Index or a Lucene Index.
    /// @param where      The conditions the event meets, all of them.
    /// @param timeFrom   From this time, or null.
    /// @param timeTo     Up to this time, or null.
    /// @param fields     Index fields to show for each hit beside StreamId, EventId and the time.
    /// @param limit      Hits to return, newest first.
    /// @param allStreams True for every Events stream holding a match, and what a change to them reaches.
    public record Find(String index,
                       List<Term> where,
                       String timeFrom,
                       String timeTo,
                       List<String> fields,
                       int limit,
                       boolean allStreams) {

        /// Makes the lists never null, and the limit 1 to [#MAX_HITS].
        public Find {
            where = NullSafe.list(where);
            fields = NullSafe.list(fields);
            limit = Math.max(1, Math.min(MAX_HITS, limit));
        }
    }

    /// One condition, as a person would set it on a dashboard.
    ///
    /// @param field     The index's field.
    /// @param condition The condition.
    /// @param value     BETWEEN: 'from,to'; IN: values separated by commas; EQUALS takes * wildcards.
    public record Term(String field, Condition condition, String value) {

        /// Makes the condition EQUALS and the value empty if not given.
        public Term {
            condition = condition == null
                    ? Condition.EQUALS
                    : condition;
            value = NullSafe.string(value);
        }

        /// @return The term as find_events says it, e.g. `user.name EQUALS alice`.
        String said() {
            return field + " " + condition.name() + " " + value;
        }
    }

}
