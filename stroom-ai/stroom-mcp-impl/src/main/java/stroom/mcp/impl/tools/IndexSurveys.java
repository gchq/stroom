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

import stroom.ai.impl.tools.CallProgress;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.IndexSearches;
import stroom.ai.impl.tools.IndexSearches.Found;
import stroom.ai.impl.tools.IndexSearches.Probe;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.docref.DocRef;
import stroom.feed.shared.FeedDoc;
import stroom.index.shared.LuceneIndexDoc;
import stroom.meta.shared.Meta;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.Column;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.Format;
import stroom.query.api.Sort;
import stroom.query.api.Sort.SortDirection;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/// What an existing index holds, surveyed through Stroom: the fields Stroom has for the index doc, a sample of its
/// documents read through dashboard searches that are never saved, the pipelines that feed it, and the whole index's
/// totals and sources. Used for read_document's survey of an index doc (through [DocumentAnalysesImpl]) and
/// write_documentation's documentation of an index.
class IndexSurveys {

    /// Fields read per survey; more are listed as not surveyed.
    static final int SURVEY_FIELDS = 600;
    /// The sample is read from documents this recent when the index has any.
    static final int RECENT_DAYS = 30;
    /// How long the sample's searches wait in all, before the sample is what Stroom has found by then.
    static final Duration SAMPLE_WAIT = Duration.ofSeconds(10);
    /// The least a sample search waits.
    static final Duration LEAST_WAIT = Duration.ofSeconds(2);
    // Columns per dashboard search, and how many content matches are read for the feeding pipelines.
    private static final int SURVEY_COLUMNS = 100;
    private static final int FEEDING_LOOKUP = 100;
    private static final Pattern NAME_VALUES = Pattern.compile("[*{]");

    private final IndexSearches indexSearches;
    private final IndexFields indexFields;
    private final DocumentAccess documentAccess;
    private final StreamReader streamReader;
    private final PipelineIndex pipelineIndex;
    private final PipelineShapes pipelineShapes;
    private final KeptMappings keptMappings;
    private final FeedNames feedNames;

    /// @param indexSearches  Searches the index as a dashboard does.
    /// @param indexFields    Lists the index's fields.
    /// @param documentAccess Reads the docs, as the user.
    /// @param streamReader   Reads the streams' meta.
    /// @param pipelineIndex  Finds the pipelines naming the index.
    /// @param pipelineShapes Reads a pipeline as Stroom runs it.
    /// @param keptMappings   Reads the plan kept with a pipeline's indexing XSLT.
    /// @param feedNames      Finds a feed by name.
    @Inject
    IndexSurveys(final IndexSearches indexSearches,
                 final IndexFields indexFields,
                 final DocumentAccess documentAccess,
                 final StreamReader streamReader,
                 final PipelineIndex pipelineIndex,
                 final PipelineShapes pipelineShapes,
                 final KeptMappings keptMappings,
                 final FeedNames feedNames) {
        this.indexSearches = indexSearches;
        this.indexFields = indexFields;
        this.documentAccess = documentAccess;
        this.streamReader = streamReader;
        this.pipelineIndex = pipelineIndex;
        this.pipelineShapes = pipelineShapes;
        this.keptMappings = keptMappings;
        this.feedNames = feedNames;
    }

    /// What an existing index holds: the fields Stroom has for the index doc, a sample of its documents (each
    /// field: how often the sample held it, its values), and the pipelines that feed it; with profile, the whole
    /// index's totals and sources too ([#indexProfile]). A wide index is read in groups of columns, joined on
    /// StreamId and EventId; past [#SURVEY_FIELDS] fields, the rest are listed as not surveyed.
    ///
    /// The sample is not sorted: Stroom sorts a table itself, so a sorted search reads every document in the index,
    /// while an unsorted one stops at maxDocuments (seen: the newest 100 documents of a large Elasticsearch index,
    /// sorted on its time field, took searches of a minute each, and the survey three minutes). It is read from the
    /// last [#RECENT_DAYS] days when there are documents that recent, so it shows what the index is written with now.
    ///
    /// An unsorted search stops early only once it has its rows, and Stroom drops every hit whose StreamId isn't a
    /// stream the user may read: an index holding fewer readable documents than asked for is read to its end
    /// (seen: 38 s for any 100 documents of an Elasticsearch index written mostly from outside Stroom). The
    /// sample's searches wait [#SAMPLE_WAIT] in all, and the sample is then what they had found, said to be
    /// partial.
    ///
    /// @param context      The call, told what the survey is doing.
    /// @param index        The Lucene Index or Elastic Index doc.
    /// @param maxDocuments The most documents sampled.
    /// @param profile      True for the whole index's totals and sources too.
    /// @return The survey.
    /// @throws ToolRefusal if the index doc can't be read.
    Map<String, Object> surveyIndex(final ToolCallContext context,
                                    final DocRef index,
                                    final int maxDocuments,
                                    final boolean profile) {
        final CallProgress progress = CallProgress.of(context);
        final IndexDoc doc = read(index);
        final DocRef ref = new DocRef(index.getType(), index.getUuid(), doc.name());
        // What the user knows the index by: its name in Elasticsearch.
        final String label = NullSafe.nonBlankStringElse(doc.indexName(), doc.name());
        progress.say("Reading the fields of " + doc.name() + (NullSafe.isNonEmptyString(doc.indexName())
                ? " (" + doc.indexName() + ")"
                : ""));
        final List<Map<String, Object>> fields = indexFields.indexFields(ref);
        final List<String> names = fields.stream().map(field -> String.valueOf(field.get("name"))).toList();
        final String timeField = NullSafe.isNonEmptyString(doc.timeField())
                ? doc.timeField()
                : fields.stream()
                        .filter(field -> "date".equals(field.get("type")) || "date_field".equals(field.get("type")))
                        .map(field -> String.valueOf(field.get("name")))
                        .findFirst()
                        .orElse(null);
        final List<String> ids = IndexChecks.IDS.stream().filter(names::contains).toList();
        final List<String> base = new ArrayList<>(ids);
        if (timeField != null && names.contains(timeField) && !ids.contains(timeField)) {
            base.add(timeField);
        }
        final List<String> readable = names.stream().filter(name -> !base.contains(name))
                .limit(Math.max(0, SURVEY_FIELDS - base.size()))
                .toList();
        final int width = Math.max(1, SURVEY_COLUMNS - base.size());
        List<List<String>> groups = new ArrayList<>();
        for (int i = 0; i < readable.size(); i += width) {
            groups.add(readable.subList(i, Math.min(readable.size(), i + width)));
        }
        if (groups.isEmpty()) {
            groups.add(List.of());
        }
        if (ids.size() < 2) {
            // Without both ids, groups of columns cannot be joined: the first is read.
            groups = groups.subList(0, 1);
        }
        final Sample sample = sample(context, ref, doc.name(), base, groups.getFirst(), timeField, names,
                maxDocuments, label);
        final List<String> errors = new ArrayList<>(sample.found().errors());
        final List<Map<String, String>> rows = new ArrayList<>();
        sample.found().rows().forEach(row -> rows.add(new LinkedHashMap<>(row)));
        if (!rows.isEmpty() && groups.size() > 1) {
            errors.addAll(otherGroups(context, ref, doc.name(), base, groups, ids, rows, maxDocuments));
        }
        final List<String> surveyed = new ArrayList<>(base);
        groups.forEach(surveyed::addAll);
        final List<Map<String, List<String>>> documents = new ArrayList<>();
        for (final Map<String, String> row : rows) {
            final Map<String, List<String>> document = new LinkedHashMap<>();
            for (final String name : surveyed) {
                final String value = row.get(name);
                document.put(name, NullSafe.isEmptyString(value)
                        ? List.of()
                        : List.of(value));
            }
            documents.add(document);
        }
        final List<String> times = documents.stream()
                .filter(document -> timeField != null && !document.getOrDefault(timeField, List.of()).isEmpty())
                .map(document -> document.get(timeField).getFirst())
                .sorted()
                .toList();
        final int total = documents.size();
        Map<String, Object> totals = Map.of();
        if (total > 0 && profile) {
            try {
                totals = indexProfile(context, ref, timeField, names, 20, label);
            } catch (final ToolRefusal e) {
                totals = Map.of("error", e.getMessage());
            }
        }
        final String note = note(index.getType(), total, sample.cutShort(), maxDocuments);
        progress.say("Looking for the pipelines that write to " + label);
        final Map<String, Object> indexEntry = new LinkedHashMap<>();
        indexEntry.put("type", ref.getType());
        indexEntry.put("uuid", ref.getUuid());
        indexEntry.put("name", ref.getName());
        indexEntry.put("path", PipelineIndex.path(documentAccess, ref));
        indexEntry.put("link", context.links().documentTarget(ref));
        final Map<String, Object> survey = new LinkedHashMap<>();
        survey.put("index", indexEntry);
        if (note != null) {
            survey.put("note", note);
        }
        survey.put("backend", LuceneIndexDoc.TYPE.equals(index.getType())
                ? "lucene"
                : "elasticsearch");
        survey.put("index_name", doc.indexName());
        survey.put("time_field", timeField);
        survey.put("fields", fields);
        survey.put("surveyed_fields", surveyed);
        survey.put("documents_sampled", total);
        if (total > 0) {
            survey.put("sampled_from", sample.recent()
                    ? "the last " + RECENT_DAYS + " days"
                    : "any time");
        }
        if (profile) {
            survey.put("profile", totals);
        }
        survey.put("earliest", times.isEmpty()
                ? null
                : times.getFirst());
        survey.put("latest", times.isEmpty()
                ? null
                : times.getLast());
        final Map<String, Object> populated = new LinkedHashMap<>();
        final Map<String, Object> values = new LinkedHashMap<>();
        for (final String name : surveyed) {
            if (total > 0) {
                final long held = documents.stream().filter(document -> !document.get(name).isEmpty()).count();
                populated.put(name, Math.round(1000.0 * held / total) / 10.0);
            }
            final Set<String> seen = new LinkedHashSet<>();
            documents.forEach(document -> seen.addAll(document.get(name)));
            values.put(name, seen.stream().limit(3).toList());
        }
        survey.put("populated", populated);
        survey.put("values", values);
        survey.put("fed_by", feedingPipelines(ref, doc.indexName()));
        survey.put("documents", documents);
        survey.put("errors", errors);
        return survey;
    }

    // The last RECENT_DAYS days; else any documents; else every time there is (some backends want a term). A
    // recent window too sparse to fill the sample gives way to any documents when those are more: five documents
    // say little of how often each field is held.
    private Sample sample(final ToolCallContext context,
                          final DocRef ref,
                          final String name,
                          final List<String> base,
                          final List<String> group,
                          final String timeField,
                          final List<String> names,
                          final int maxDocuments,
                          final String label) {
        final List<Object[]> tries = new ArrayList<>();
        if (timeField != null && names.contains(timeField)) {
            tries.add(new Object[]{true, between(timeField, "day()-" + RECENT_DAYS + "d,day()+1d"),
                    "from the last " + RECENT_DAYS + " days"});
        }
        tries.add(new Object[]{false, ExpressionOperator.builder().build(), "of any time"});
        if (timeField != null) {
            tries.add(new Object[]{false, between(timeField, "1970-01-01T00:00:00.000Z,day()+1d"), "of any time"});
        }
        Found first = new Found(List.of(), List.of(), true);
        boolean recent = false;
        final long deadline = System.nanoTime() + SAMPLE_WAIT.toNanos();
        for (final Object[] attempt : tries) {
            final Duration left = Duration.ofNanos(deadline - System.nanoTime());
            if ((left.isZero() || left.isNegative()) && !first.rows().isEmpty()) {
                break;
            }
            final List<String> columns = new ArrayList<>(base);
            columns.addAll(group);
            final Found found = indexSearches.search(context, probe(ref, name + " survey",
                            (ExpressionOperator) attempt[1], columns, maxDocuments), maxDocuments,
                    "Searching " + label + " for up to " + maxDocuments + " documents " + attempt[2],
                    left.compareTo(LEAST_WAIT) > 0
                            ? left
                            : LEAST_WAIT);
            if (found.rows().size() > first.rows().size() || first.rows().isEmpty()) {
                first = found;
                recent = (boolean) attempt[0];
            }
            // Any documents found, or Stroom still looking for some when the wait ran out: a term would find none
            // it hasn't (seen: a dev Stroom whose streams were deleted in a migration, its indices kept).
            if (first.rows().size() >= maxDocuments
                || (!(boolean) attempt[0] && (!first.rows().isEmpty() || !found.complete()))) {
                break;
            }
        }
        return new Sample(first, recent, !first.complete());
    }

    // The other groups read exactly the documents the first did, by their ids: an unsorted search returns
    // whichever documents it reaches first, which need not be the same ones twice. Each is a short search for up
    // to maxDocuments documents, made one after the other.
    private List<String> otherGroups(final ToolCallContext context,
                                     final DocRef ref,
                                     final String name,
                                     final List<String> base,
                                     final List<List<String>> groups,
                                     final List<String> ids,
                                     final List<Map<String, String>> rows,
                                     final int maxDocuments) {
        final ExpressionOperator.Builder same = ExpressionOperator.builder().op(Op.OR);
        final Map<String, Map<String, String>> byKey = new LinkedHashMap<>();
        for (final Map<String, String> row : rows) {
            final ExpressionOperator.Builder pair = ExpressionOperator.builder();
            ids.forEach(id -> pair.addTerm(IndexChecks.term(id, Condition.EQUALS, String.valueOf(row.get(id)))));
            same.addOperator(pair.build());
            byKey.put(key(row, ids), row);
        }
        CallProgress.of(context).say("Reading " + (groups.size() - 1) + " more group" + (groups.size() > 2
                ? "s"
                : "") + " of columns for the " + rows.size() + " documents");
        final List<String> errors = new ArrayList<>();
        for (final List<String> group : groups.subList(1, groups.size())) {
            final List<String> columns = new ArrayList<>(base);
            columns.addAll(group);
            final Found more = indexSearches.search(context, probe(ref, name + " survey", same.build(), columns,
                    maxDocuments), maxDocuments, null);
            errors.addAll(more.errors());
            for (final Map<String, String> row : more.rows()) {
                final Map<String, String> held = byKey.get(key(row, ids));
                if (held != null) {
                    group.forEach(column -> held.put(column, row.get(column)));
                }
            }
        }
        return errors;
    }

    // Stroom checks every hit's stream, Lucene's as Elasticsearch's (ExtractionDecorator, shared by both): a
    // document whose stream is gone (seen: a dev Stroom whose streams were deleted in a migration) or unreadable
    // never comes back.
    private static String note(final String type, final int total, final boolean cutShort, final int maxDocuments) {
        if (total == 0) {
            final String note = ElasticIndexDoc.TYPE.equals(type)
                    ? "No documents came back through Stroom. Stroom returns a hit only when its StreamId is a "
                      + "stream in this Stroom that you may read (it checks the stream's feed for every hit), so "
                      + "documents written from outside Stroom, without StreamId or with another system's, or whose "
                      + "streams have been deleted, are not shown: the index is described from its mapping only, "
                      + "its values not read."
                    : "No documents came back through Stroom: the index is empty, holds no stored values to show, "
                      + "or holds only documents whose streams you may not read or that have been deleted (Stroom "
                      + "returns a hit only when its StreamId is a stream in this Stroom that you may read).";
            return cutShort
                    ? "Stroom found no document it could show within " + SAMPLE_WAIT.toSeconds() + " s, and was "
                      + "still looking. " + note
                    : note;
        }
        if (cutShort && total < maxDocuments) {
            return "The sample is " + total + " documents, what Stroom had found after " + SAMPLE_WAIT.toSeconds()
                   + " s: it was still looking. Stroom returns a hit only when its StreamId is a stream you may "
                   + "read, so an index written mostly from outside Stroom holds few it can show, and it reads the "
                   + "whole index looking for them.";
        }
        return null;
    }

    /// The whole index at a glance, through grouped dashboard searches: how many documents, the time they span and
    /// how many streams they came from; and, for the streams with the most documents, the feed and stream type
    /// each belongs to and the pipeline that produced it (from the stream's meta).
    ///
    /// @param context   The call, told what is counted.
    /// @param ref       The index doc.
    /// @param timeField Its time field, or null.
    /// @param names     Its fields.
    /// @param streams   How many of the streams with the most documents are looked up.
    /// @param label     The index, as the user knows it, or null for the doc's name.
    /// @return `documents`, `earliest`, `latest`, `streams`, `sources`, `streams_examined`, `partial`; empty when
    /// nothing was counted.
    Map<String, Object> indexProfile(final ToolCallContext context,
                                     final DocRef ref,
                                     final String timeField,
                                     final List<String> names,
                                     final int streams,
                                     final String label) {
        final String shown = NullSafe.nonBlankStringElse(label, ref.getName());
        final ExpressionOperator everything = ExpressionOperator.builder().build();
        final List<Column> totals = new ArrayList<>();
        totals.add(column("all", "'all'").copy().group(0).build());
        totals.add(column("documents", "count()"));
        if (timeField != null && names.contains(timeField)) {
            totals.add(column("earliest", "min(${" + timeField + "})"));
            totals.add(column("latest", "max(${" + timeField + "})"));
        }
        if (names.contains("StreamId")) {
            totals.add(column("streams", "countUnique(${StreamId})"));
        }
        final Found found = indexSearches.search(context, probe(ref, ref.getName() + " profile", everything, totals,
                1), 1, "Counting every document in " + shown + " (this reads the whole index)");
        if (found.rows().isEmpty()) {
            return Map.of();
        }
        final Map<String, String> row = found.rows().getFirst();
        final Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("documents", whole(row.get("documents")));
        profile.put("earliest", row.get("earliest"));
        profile.put("latest", row.get("latest"));
        profile.put("streams", NullSafe.isNonEmptyString(row.get("streams"))
                ? whole(row.get("streams"))
                : null);
        if (!found.complete()) {
            // Counting reads every document through Stroom: on a large index it can outlast the wait, and what was
            // counted by then is a floor, not the index.
            profile.put("partial", "Stroom had not finished counting after " + IndexSearches.SEARCH_WAIT.toSeconds()
                                   + " s: these are the documents it had read by then, not the whole index");
        }
        if (!names.contains("StreamId")) {
            return profile;
        }
        final Found byStream = indexSearches.search(context, probe(ref, ref.getName() + " profile", everything,
                List.of(column("StreamId", "${StreamId}").copy().group(0).build(),
                        column("documents", "count()").copy().sort(new Sort(0, SortDirection.DESCENDING)).build()),
                streams), streams, "Grouping the documents in " + shown + " by stream (this reads the whole index)");
        final List<Map<String, String>> top = byStream.rows().subList(0, Math.min(streams, byStream.rows().size()));
        CallProgress.of(context).say("Looking up where the " + Math.min(streams, byStream.rows().size())
                                     + " streams with the most documents came from");
        final Map<List<String>, Map<String, Object>> sources = new LinkedHashMap<>();
        final Map<String, Optional<String>> pipelines = new LinkedHashMap<>();
        for (final Map<String, String> streamRow : top) {
            final Meta meta;
            try {
                meta = streamReader.meta((long) Double.parseDouble(streamRow.get("StreamId")));
            } catch (final RuntimeException e) {
                continue;
            }
            final String madeBy = meta.getPipelineUuid();
            if (madeBy != null && !pipelines.containsKey(madeBy)) {
                pipelines.put(madeBy, documentAccess.read(new DocRef(PipelineDoc.TYPE, madeBy), PipelineDoc.class)
                        .map(PipelineDoc::getName));
            }
            final String producedBy = madeBy == null
                    ? null
                    : pipelines.get(madeBy).orElse(null);
            final List<String> key = new ArrayList<>();
            key.add(meta.getFeedName());
            key.add(meta.getTypeName());
            key.add(producedBy);
            final Map<String, Object> entry = sources.computeIfAbsent(key, k -> {
                final Map<String, Object> made = new LinkedHashMap<>();
                made.put("feed", k.get(0));
                made.put("type", k.get(1));
                made.put("produced_by", k.get(2));
                made.put("streams", 0L);
                made.put("documents", 0L);
                return made;
            });
            entry.put("streams", (long) entry.get("streams") + 1);
            entry.put("documents", (long) entry.get("documents") + whole(streamRow.get("documents")));
        }
        for (final Map<String, Object> entry : sources.values()) {
            final String feed = (String) entry.get("feed");
            feedNames.exact(NullSafe.string(feed))
                    .flatMap(ref2 -> documentAccess.read(ref2, FeedDoc.class))
                    .ifPresent(doc -> entry.put("description", NullSafe.string(doc.getDescription()).strip()));
        }
        profile.put("sources", sources.values().stream()
                .sorted(Comparator.comparingLong(entry -> -(long) entry.get("documents")))
                .toList());
        profile.put("streams_examined", top.size());
        if (!byStream.complete() && !profile.containsKey("partial")) {
            profile.put("partial", "Stroom had not finished grouping the documents by stream after "
                                   + IndexSearches.SEARCH_WAIT.toSeconds() + " s: the sources are from the documents "
                                   + "it had read by then");
        }
        return profile;
    }

    /// The pipelines that write to the index, each with the index plan kept with its XSLT when there is one. Found
    /// by content (a Lucene IndexingFilter names the doc, an Elasticsearch one the index name), then each confirmed
    /// from its effective properties, so a mention elsewhere (a description, foo-v10 for foo-v1) does not count.
    ///
    /// @param index     The index doc.
    /// @param indexName An Elastic Index doc's index name, or null.
    /// @return Each pipeline: `uuid`, `name`, `path`, `plan` (or null).
    List<Map<String, Object>> feedingPipelines(final DocRef index, final String indexName) {
        final String needle = LuceneIndexDoc.TYPE.equals(index.getType())
                ? index.getUuid()
                : NAME_VALUES.split(NullSafe.string(indexName), -1)[0];
        if (NullSafe.isEmptyString(needle)) {
            return List.of();
        }
        final List<Map<String, Object>> fed = new ArrayList<>();
        final List<DocRef> found = pipelineIndex.containing(PipelineDoc.TYPE, needle);
        for (final DocRef pipeline : found.subList(0, Math.min(FEEDING_LOOKUP, found.size()))) {
            final MergedPipeline merged;
            try {
                merged = pipelineShapes.merged(pipeline.getUuid());
            } catch (final RuntimeException e) {
                continue;
            }
            if (!IndexDocs.writesTo(merged.properties(), index.getType(), index.getUuid(), indexName)) {
                continue;
            }
            Object plan;
            try {
                plan = keptMappings.keptMapping(pipeline.getUuid())
                        .filter(kept -> "index".equals(kept.kind()))
                        .map(KeptMapping.Kept::payload)
                        .orElse(null);
            } catch (final RuntimeException e) {
                // Its plan unreadable: still a pipeline that feeds the index, without one.
                plan = null;
            }
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("uuid", pipeline.getUuid());
            entry.put("name", pipeline.getName());
            entry.put("path", PipelineIndex.path(documentAccess, pipeline));
            entry.put("plan", plan);
            fed.add(entry);
        }
        return fed;
    }

    private IndexDoc read(final DocRef index) {
        if (LuceneIndexDoc.TYPE.equals(index.getType())) {
            return documentAccess.read(index, LuceneIndexDoc.class)
                    .map(doc -> new IndexDoc(doc.getName(), null, doc.getTimeField()))
                    .orElseThrow(() -> unreadable(index));
        }
        return documentAccess.read(index, ElasticIndexDoc.class)
                .map(doc -> new IndexDoc(doc.getName(), doc.getIndexName(), doc.getTimeField()))
                .orElseThrow(() -> unreadable(index));
    }

    private static ToolRefusal unreadable(final DocRef index) {
        return new ToolRefusal(index.getType() + " " + index.getUuid() + " can't be read: it doesn't exist, or you "
                               + "don't have permission to view it");
    }

    private static Probe probe(final DocRef ref,
                               final String name,
                               final ExpressionOperator expression,
                               final List<?> columns,
                               final int rows) {
        final List<Column> table = columns.stream()
                .map(column -> column instanceof final Column given
                        ? given
                        : IndexChecks.column(String.valueOf(column)))
                .toList();
        return new Probe(ref, name, expression, table, rows);
    }

    private static Column column(final String name, final String expression) {
        return Column.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .expression(expression)
                .visible(true)
                .format(Format.GENERAL)
                .build();
    }

    private static ExpressionOperator between(final String field, final String span) {
        return ExpressionOperator.builder().addTerm(IndexChecks.term(field, Condition.BETWEEN, span)).build();
    }

    private static String key(final Map<String, String> row, final List<String> ids) {
        return String.join("\n", ids.stream().map(id -> String.valueOf(row.get(id))).toList());
    }

    private static long whole(final String value) {
        try {
            return NullSafe.isEmptyString(value)
                    ? 0
                    : (long) Double.parseDouble(value);
        } catch (final NumberFormatException e) {
            return 0;
        }
    }


    // --------------------------------------------------------------------------------


    private record IndexDoc(String name, String indexName, String timeField) {

    }

    private record Sample(Found found, boolean recent, boolean cutShort) {

    }
}
