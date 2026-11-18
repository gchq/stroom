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

import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.IndexSearches;
import stroom.ai.impl.tools.IndexSearches.Found;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.MisleadingSearches;
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.ToolArgs;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.IndexingHelpers;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.ai.shared.AiToolCategory;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.DashboardConfig;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.QueryComponentSettings;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.index.shared.LuceneIndexDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.Column;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

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

/// `verify_index` - verifies indexed events through Stroom, not by querying the backend: a workspace dashboard on the
/// index doc, made in the build once the user confirms its columns, then the test searches of the sample's streams
/// ([IndexChecks]), each hit traced back to its record with the indexing pipeline; a pass is recorded as verified for
/// the build's pipelines writing to the index.
class VerifyIndexTool extends AbstractBuiltInTool {

    static final String NAME = "verify_index";
    static final String BUILD_ARG = "build";
    static final String INDEX_UUID_ARG = "index_uuid";
    static final String BACKEND_ARG = "backend";
    static final String STREAM_IDS_ARG = "stream_ids";
    static final String EXPECTED_ARG = "expected_documents";
    static final String FIELDS_ARG = "fields";
    static final String EXACT_ARG = "exact";
    static final String TIME_RANGE_ARG = "time_range";
    static final String DASHBOARD_NAME_ARG = "dashboard_name";
    static final String RETRIES_ARG = "retries";
    static final String SEARCHES_ARG = "searches";
    static final String PIPELINE_UUID_ARG = "pipeline_uuid";
    static final String CHECKS_ARG = "checks";
    /// What a verification stopped by its client says.
    static final String CANCELLED = "Verification was stopped: the call was cancelled.";
    /// The role subfolder the dashboard is made in.
    static final String ROLE = "Indexing";

    private static final int DEFAULT_RETRIES = 6;
    private static final int MAX_RETRIES = 20;

    private final IndexChecks checks;
    private final IndexFields indexFields;
    private final IndexSearches indexSearches;
    private final IndexPlans plans;
    private final DocumentAccess documentAccess;
    private final WriteGuard guard;
    private final Consent consent;
    private final BuildPlan plan;
    private final BuildRecords records;
    private final PipelineShapes pipelineShapes;
    private final EventsExpectations expectations;
    private Provider<BuildTestIndexes> testIndexesProvider;

    @Inject
    VerifyIndexTool(final IndexChecks checks,
                    final IndexFields indexFields,
                    final IndexSearches indexSearches,
                    final IndexPlans plans,
                    final DocumentAccess documentAccess,
                    final WriteGuard guard,
                    final Consent consent,
                    final BuildPlan plan,
                    final BuildRecords records,
                    final PipelineShapes pipelineShapes,
                    final EventsExpectations expectations) {
        this.checks = checks;
        this.indexFields = indexFields;
        this.indexSearches = indexSearches;
        this.plans = plans;
        this.documentAccess = documentAccess;
        this.guard = guard;
        this.consent = consent;
        this.plan = plan;
        this.records = records;
        this.pipelineShapes = pipelineShapes;
        this.expectations = expectations;
    }

    /// @param testIndexes The build's test indexes, searched in place of the production indexes they stand in for.
    @Inject
    void setTestIndexes(final Provider<BuildTestIndexes> testIndexes) {
        this.testIndexesProvider = testIndexes;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.INDEXING;
    }

    @Override
    public boolean isReadOnly() {
        // It makes (or changes) the build's verification dashboard, and records the build's pipelines as verified.
        return false;
    }

    @Override
    public boolean confirmsItself() {
        // The user confirms the dashboard's columns in a form before it is made or changed.
        return true;
    }

    @Override
    public boolean usesRunSlot() {
        // It searches the index many times, and steps the indexing pipeline to trace hits.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .unlockedBy(AppPermission.STEPPING_PERMISSION)
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.USE, DocumentPermission.EDIT)
                .note("Searched as the user, who needs Use on the index doc; the dashboard is made in the build "
                      + "folder as the user (Create there). Tracing hits back to their records steps the indexing "
                      + "pipeline, which needs the Stepping permission.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        final ObjectSchema exact = ObjectSchema.builder()
                .addStringProperty("field", "The index's field.")
                .addStringProperty("value", "A value from stepped documents.")
                .addIntegerProperty("expected", "Leave out: the tool counts the documents from the Events.")
                .required("field", "value")
                .build();
        final ObjectSchema search = ObjectSchema.builder()
                .addStringProperty("field", "The index's field.")
                .addStringProperty("condition", "One of " + String.join(", ", IndexChecks.CONDITIONS)
                                                + " (default EQUALS).")
                .addStringProperty("value", "BETWEEN: 'from,to'; IN: values separated by commas; EQUALS takes * "
                                            + "wildcards.")
                .addIntegerProperty("expected", "Leave out where the tool counts it from the Events (EQUALS, "
                                                + "NOT_EQUALS, IN and number ranges on a field the plan reads from "
                                                + "the Events). Otherwise rows it must return; none: at least one; "
                                                + "0: none.")
                .required("field")
                .build();
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Verify indexed events through Stroom, not by querying the backend: a workspace "
                             + "dashboard on the index doc, then the test searches: the sample stream ids, an exact "
                             + "match on each key field, a time range and any further searches, each of the "
                             + "sample's streams only (the rest of the index never answers for them); with the "
                             + "indexing pipeline, each hit traced back to the record it came from. Each exact "
                             + "match and search is counted from the Events themselves, by the Events path the "
                             + "plan kept with the indexing XSLT reads the field from (the streams' first 1,000 "
                             + "events each), and passes only when the index finds as many: never give expected "
                             + "counts of your own, nor ones read from the index. A check the Events can't count "
                             + "(a computed or analysed field, CONTAINS) is independent: false and doesn't count "
                             + "towards passing. findings names core fields of the convention empty in most "
                             + "events: tell the user. Passes when every check returns what it should. A stream "
                             + "of more than 10,000 events is checked by its first and last 1,000 and none past "
                             + "its last, not counted whole; a count Stroom hadn't finished in time is unproven, "
                             + "not a failure: call again. The dashboard "
                             + "(created once per build, for an index the build made, after the user confirms its "
                             + "columns) has a query on the time field from the sample's earliest event (rounded "
                             + "back to a 30-day boundary) through the end of today, a table of the user's fields "
                             + "newest first, and a text pane on the selected row's record with stepping. An index "
                             + "from elsewhere is searched without saving a dashboard, unless pipeline_uuid is the "
                             + "build's own pipeline writing to it: the dashboard is then the build's, promoted with "
                             + "that pipeline. checks=false, when the user asks to skip verification, makes the "
                             + "dashboard and gives its link, with no searches.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(BUILD_ARG, CreateFeedTool.BUILD_DESCRIPTION)
                        .addStringProperty(INDEX_UUID_ARG, "The index doc that was indexed into.")
                        .addStringProperty(BACKEND_ARG, "lucene or elasticsearch.")
                        .addProperty(STREAM_IDS_ARG, stringArraySchema("Events streams that were indexed."))
                        .addIntegerProperty(EXPECTED_ARG, "Events records in those streams.")
                        .addProperty(FIELDS_ARG, stringArraySchema("The dashboard table's columns, which the user "
                                                                   + "chose: the index's field names (as the plan "
                                                                   + "named them, e.g. user.name), not the Events "
                                                                   + "paths they come from (EventSource/User/Id). "
                                                                   + "Suggest the time field and the plan's key "
                                                                   + "fields (user, host, address, event type, "
                                                                   + "outcome) and confirm them with the user. "
                                                                   + "StreamId and EventId are kept as hidden "
                                                                   + "columns, for the text pane and tracing hits."))
                        .addProperty(EXACT_ARG, ArraySchema.builder()
                                .items(exact)
                                .description("Exact-match checks, each {'field': ..., 'value': ...} using values "
                                             + "from stepped documents; each must return at least one row.")
                                .build())
                        .addProperty(TIME_RANGE_ARG, ObjectSchema.builder()
                                .description("{'field': ..., 'from': ISO, 'to': ISO, 'expected': n}")
                                .build())
                        .addIntegerProperty(RETRIES_ARG, "Retries while the index catches up (0 to " + MAX_RETRIES
                                                         + "; default " + DEFAULT_RETRIES + ").")
                        .addProperty(SEARCHES_ARG, ArraySchema.builder()
                                .items(search)
                                .description("More searches, as people will make them: numeric ranges "
                                             + "(GREATER_THAN, BETWEEN), IN, wildcards (EQUALS 'adm*'), CONTAINS on "
                                             + "text, and a value in another case that must find nothing (expected "
                                             + "0). Use values from stepped documents.")
                                .build())
                        .addStringProperty(PIPELINE_UUID_ARG, "The indexing pipeline: each check's first hit is "
                                                              + "traced back to its record by stepping it at the "
                                                              + "hit's StreamId and EventId (record index EventId - "
                                                              + "1); a trace that fails says what it compared "
                                                              + "(trace.why).")
                        .addBooleanProperty(CHECKS_ARG, "False only when the user asks to skip verification: the "
                                                        + "build's -VERIFY dashboard is made (or its columns "
                                                        + "changed) as the user confirms, and its link returned, "
                                                        + "with no test searches. Nothing is marked verified, so "
                                                        + "promotion still waits for a verification that passes.")
                        .required(BUILD_ARG, INDEX_UUID_ARG, BACKEND_ARG, STREAM_IDS_ARG, EXPECTED_ARG, FIELDS_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            final Map<String, Object> result = verify(call, context);
            return JsonResults.of(result);
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        } catch (final ToolTimeoutException e) {
            return JsonResults.refused(Thread.currentThread().isInterrupted()
                    ? CANCELLED
                    : PipelineStepping.outOfTime(context, "verifying"));
        } catch (final RuntimeException e) {
            return JsonResults.refused(StreamSummaries.reason(e));
        }
    }

    private Map<String, Object> verify(final ToolCall call, final ToolCallContext context) {
        final String build = required(call, BUILD_ARG);
        final String askedUuid = required(call, INDEX_UUID_ARG);
        final String backend = backend(call);
        // A production index the build's pipeline indexed in place of: the build's test index is what it wrote to.
        final Optional<DocRef> testIndex = testIndexesProvider == null
                ? Optional.empty()
                : testIndexesProvider.get().searchedFor(build, askedUuid);
        final String indexUuid = testIndex.map(DocRef::getUuid).orElse(askedUuid);
        final List<Long> streamIds = StartOnboardingTool.streamIds(call, STREAM_IDS_ARG);
        if (streamIds.isEmpty()) {
            throw new ToolRefusal("Give stream_ids: the Events streams that were indexed.");
        }
        final long expected = whole(call, EXPECTED_ARG, null, null);
        final int retries = (int) whole(call, RETRIES_ARG, (long) DEFAULT_RETRIES, (long) MAX_RETRIES);
        final List<Map<String, String>> exact = exact(call);
        final Map<String, Object> timeRange = timeRange(call);
        final String pipelineUuid = stringArg(call, PIPELINE_UUID_ARG);
        final List<IndexChecks.SearchCheck> given = ToolArgs.oneOrMore(call, SEARCHES_ARG).stream()
                .map(IndexChecks.SearchCheck::of)
                .toList();

        final String docType = IndexingHelpers.INDEX_TYPE.get(backend);
        final DocRef asked = new DocRef(docType, indexUuid);
        final Index index = read(asked);
        final DocRef source = new DocRef(docType, indexUuid, index.name());
        // The fields' types, as IS_NOT_NULL is searched for a non-empty value on an Elasticsearch string field.
        final Map<String, String> typed = index.elastic() == null
                ? Map.of()
                : indexFields.elasticTypes(index.elastic());
        final List<IndexChecks.SearchCheck> searches = IndexChecks.asSearched(backend, given, typed);
        IndexChecks.searchable(backend, searches, Set.of(), List.of(), Map.of(), null, Map.of(), null);
        checkSearches(backend, searches, exact, typed, source);
        final List<String> searched = new ArrayList<>();
        searches.forEach(search -> searched.add(search.field()));
        exact.forEach(item -> {
            if (NullSafe.isNonEmptyString(item.get("field"))) {
                searched.add(item.get("field"));
            }
        });
        if (timeRange != null && timeRange.get("field") != null) {
            searched.add(String.valueOf(timeRange.get("field")));
        }
        final Map<String, String> unindexed = indexFields.unsearchable(source,
                new ArrayList<>(new LinkedHashSet<>(searched)), pipelineUuid);
        if (!unindexed.isEmpty()) {
            IndexChecks.searchable(backend, List.of(), Set.of(), List.of(), Map.of(), null, unindexed, null);
        }

        final List<String> fields = ToolArgs.strings(call, FIELDS_ARG).stream()
                .filter(field -> !IndexChecks.IDS.contains(field))
                .toList();
        final String timeField = index.timeField();
        indexFields.refuseColumnsNotInIndex(source, fields, pipelineUuid);
        final List<WriteGuard.BuildDoc> contents = guard.folderContents(build);
        // The build's index, or one from elsewhere that the build's own indexing pipeline writes to (a new version
        // writing through the existing index doc): the dashboard is the build's, and promoted with it (seen in
        // production: a new version's verification left no dashboard to promote).
        // A test index is searched through an unsaved dashboard: a saved one would be promoted searching the test
        // index, which stays in the workspace.
        final boolean ours = testIndex.isEmpty()
                             && (contents.stream().anyMatch(doc -> doc.uuid().equals(indexUuid))
                                 || contents.stream().anyMatch(doc -> PipelineDoc.TYPE.equals(doc.type())
                                                                      && doc.uuid().equals(pipelineUuid)));
        // The build's verification dashboard of this index, whatever it was named, changed rather than another made
        // (seen in chat 177: "<base> -VERIFY", named by the agent, then "<base> V1-VERIFY", by the index doc's name).
        final Optional<WriteGuard.BuildDoc> existing = verificationDashboard(contents, indexUuid,
                dashboardName(index.name()));
        final String name = existing.map(WriteGuard.BuildDoc::name).orElse(dashboardName(index.name()));
        // The window starts at the sample's earliest event: found by searching its streams first, oldest first,
        // saving nothing. Seen in testing: the dashboard's own table, newest first, read the 100 newest, and the
        // window hid a third of the sample's events.
        final IndexChecks.Dashboard probe = IndexChecks.Dashboard.unsaved(name,
                IndexChecks.dashboardConfig(source, fields, timeField, null)).oldestFirst();
        final Found sample = indexSearches.search(context, probe.probe(IndexChecks.byStream(streamIds)), 1,
                "Finding the earliest event of streams " + ToolText.repr(streamIds) + " in "
                + NullSafe.nonBlankStringElse(index.indexName(), index.name()));
        final String window = timeField == null
                ? null
                : IndexChecks.windowStart(sample.rows().stream()
                        .map(row -> row.get(timeField))
                        .filter(time -> time != null)
                        .toList());
        final Map<String, Object> design = IndexChecks.design(fields, timeField, window);
        final Map<String, Object> dashboard = new LinkedHashMap<>();
        final IndexChecks.Dashboard searchedWith;
        if (!ours) {
            // Another build's (or production's) index, not written by this build: searched through an unsaved
            // dashboard.
            dashboard.put("uuid", probe.uuid());
            dashboard.put("name", name);
            dashboard.put("saved", false);
            searchedWith = IndexChecks.Dashboard.of(probe.uuid(), name,
                    IndexChecks.dashboardConfig(source, fields, timeField, window));
        } else if (existing.isPresent()) {
            change(call, context, existing.get(), source, fields, timeField, window, design, name);
            final DashboardDoc doc = dashboardDoc(existing.get().docRef());
            dashboard.put("uuid", doc.getUuid());
            dashboard.put("name", name);
            dashboard.put("saved", true);
            searchedWith = IndexChecks.Dashboard.of(doc.getUuid(), name, doc.getDashboardConfig());
        } else {
            consent.require(call, context, Consent.Request.confirmation(
                    NAME, "Create the verification dashboard '" + name + "' on index '" + index.name() + "'",
                    design));
            final DashboardConfig config = IndexChecks.dashboardConfig(source, fields, timeField, window);
            final DashboardDoc made = guard.createFilled(DashboardDoc.TYPE, name, build, ref -> write(
                    dashboardDoc(ref).copy().dashboardConfig(config).build()), false, ROLE);
            dashboard.put("uuid", made.getUuid());
            dashboard.put("name", name);
            dashboard.put("saved", true);
            searchedWith = IndexChecks.Dashboard.of(made.getUuid(), name, config);
        }
        final DocRef dashboardRef = new DocRef(DashboardDoc.TYPE, String.valueOf(dashboard.get("uuid")), name);
        if (!JsonValues.truthy(checksArg(call))) {
            // Seen in testing: told to skip verification and give the dashboard, the agent went through making a
            // dashboard of its own (asking the user which kind), and the -VERIFY one was never finished.
            if (!Boolean.TRUE.equals(dashboard.get("saved"))) {
                throw new ToolRefusal("Index '" + index.name() + "' isn't the build's, nor written by its pipeline "
                                      + "(pipeline_uuid), so no dashboard is saved for it: there is nothing to give "
                                      + "without the checks");
            }
            final Map<String, Object> shown = new LinkedHashMap<>(dashboard);
            shown.put("link", context.links().documentTarget(dashboardRef));
            shown.putAll(design);
            final Map<String, Object> result = new LinkedHashMap<>();
            result.put("dashboard", shown);
            result.put("checks_skipped", "as the user asked");
            result.put("hint", "Give the user the dashboard's link. Verification was skipped at their request: "
                               + "nothing is marked verified, so promotion waits for verify_index with the checks "
                               + "to pass.");
            return result;
        }
        final Optional<FieldPlan> keptPlan = keptPlan(contents, pipelineUuid, docType, indexUuid, index.indexName());
        final Map<String, Object> searchedResult = PipelineStepping.withinTimeLimit(context, () -> {
            final EventsExpectations.Sample events = keptPlan.map(kept -> expectations.read(kept, streamIds))
                    .orElse(null);
            final Map<String, Object> found = checks.runTestSearches(context, searchedWith, streamIds, expected,
                    exact, timeRange, retries, searches, pipelineUuid, events);
            expectationsSaid(found, events, typed.isEmpty()
                    ? null
                    : typed.keySet());
            if (NullSafe.isNonBlankString(pipelineUuid)) {
                lacking(found, pipelineUuid, source, streamIds, index);
            }
            return found;
        });
        // The link to give the user (seen in VS Code: the user had to ask for it); an unsaved dashboard has none.
        final Map<String, Object> shown = new LinkedHashMap<>(dashboard);
        if (Boolean.TRUE.equals(dashboard.get("saved"))) {
            shown.put("link", context.links().documentTarget(dashboardRef));
        }
        shown.putAll(design);
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("dashboard", shown);
        ignoredName(call, name).ifPresent(note -> result.put("dashboard_name_ignored", note));
        result.putAll(searchedResult);
        testIndex.ifPresent(test -> result.put("test_index", "Searched the build's test index " + test.getName()
                                                             + ": the build's pipeline indexes into it in place of "
                                                             + "the production index, which the build leaves as "
                                                             + "it is (promotion adds the build's fields to it). "
                                                             + "No dashboard is saved for a test index."));
        if (Boolean.TRUE.equals(searchedResult.get("passed"))) {
            final List<String> recorded = recordVerified(contents, docType, indexUuid, index.indexName(), streamIds);
            if (!recorded.isEmpty()) {
                result.put("verified", recorded);
                return plan.withNext(context, build, result);
            }
        }
        return result;
    }

    /// @param indexDocName The index doc's name.
    /// @return The name of its verification dashboard: the index doc's name with `-VERIFY`, always (seen in chat
    /// 177: a name the agent made up, dropping part of the index doc's, beside the one the tool made).
    static String dashboardName(final String indexDocName) {
        return indexDocName + "-VERIFY";
    }

    /// The build's verification dashboard of an index: the one named as [#dashboardName] names it, else a
    /// `-VERIFY` dashboard whose query is on the index (one made under another name earlier).
    ///
    /// @param contents  The build's documents.
    /// @param indexUuid The index doc.
    /// @param name      The dashboard's name, as [#dashboardName] gives it.
    /// @return The dashboard, if the build has one.
    Optional<WriteGuard.BuildDoc> verificationDashboard(final List<WriteGuard.BuildDoc> contents,
                                                        final String indexUuid,
                                                        final String name) {
        final List<WriteGuard.BuildDoc> dashboards = contents.stream()
                .filter(doc -> DashboardDoc.TYPE.equals(doc.type()))
                .toList();
        final Optional<WriteGuard.BuildDoc> named = dashboards.stream()
                .filter(doc -> doc.name().equals(name))
                .findFirst();
        if (named.isPresent()) {
            return named;
        }
        return dashboards.stream()
                .filter(doc -> doc.name().endsWith("-VERIFY"))
                .filter(doc -> documentAccess.read(doc.docRef(), DashboardDoc.class)
                        .map(DashboardDoc::getDashboardConfig)
                        .filter(config -> queries(config, indexUuid))
                        .isPresent())
                .findFirst();
    }

    // A dashboard_name given that isn't the dashboard's: said, as the name always comes from the index doc.
    private Optional<String> ignoredName(final ToolCall call, final String name) {
        final String given = stringArg(call, DASHBOARD_NAME_ARG);
        if (NullSafe.isBlankString(given) || given.strip().equals(name)) {
            return Optional.empty();
        }
        return Optional.of("'" + given.strip() + "' not used: the verification dashboard is named after the index "
                           + "doc, and the build's one for this index is reused ('" + name + "')");
    }

    // Whether a dashboard's query searches the index.
    private static boolean queries(final DashboardConfig config, final String indexUuid) {
        for (final ComponentConfig component : NullSafe.list(config.getComponents())) {
            if (component.getSettings() instanceof final QueryComponentSettings query
                && query.getDataSource() != null && indexUuid.equals(query.getDataSource().getUuid())) {
                return true;
            }
        }
        return false;
    }

    // Which fields are addresses (Elasticsearch), or text (Lucene): a search that would mislead on them is
    // refused.
    private void checkSearches(final String backend,
                               final List<IndexChecks.SearchCheck> searches,
                               final List<Map<String, String>> exact,
                               final Map<String, String> typed,
                               final DocRef source) {
        if (MisleadingSearches.ELASTICSEARCH.equals(backend)) {
            final Set<String> ips = new LinkedHashSet<>();
            typed.forEach((field, type) -> {
                if ("ip".equals(type) || "ipv4_address".equals(type)) {
                    ips.add(field);
                }
            });
            IndexChecks.searchable(backend, searches, ips, exact, Map.of(), typed.keySet(), Map.of(), plans.ecs());
        } else if (searches.stream().anyMatch(search -> "CONTAINS".equals(search.condition())
                                                        || isRange(search.condition()))) {
            IndexChecks.searchable(backend, searches, Set.of(), List.of(), indexFields.luceneText(source), null,
                    Map.of(), null);
        }
    }

    // An existing dashboard: laid out again if made before dashboards were laid out as Stroom's UI needs, and its
    // columns changed once the user confirms.
    private void change(final ToolCall call,
                        final ToolCallContext context,
                        final WriteGuard.BuildDoc existing,
                        final DocRef source,
                        final List<String> fields,
                        final String timeField,
                        final String window,
                        final Map<String, Object> design,
                        final String name) {
        DashboardDoc doc = dashboardDoc(existing.docRef());
        final List<String> shown = shownColumns(doc.getDashboardConfig());
        if (doc.getDashboardConfig() == null || doc.getDashboardConfig().getLayoutConstraints() == null) {
            // Made before dashboards were laid out as Stroom's UI needs (it showed them empty): laid out again, its
            // columns kept, so it shows what it searches.
            doc = write(doc.copy().dashboardConfig(IndexChecks.dashboardConfig(source, shown, timeField, window))
                    .build());
        }
        if (!shown.equals(fields)) {
            consent.require(call, context, Consent.Request.confirmation(
                    NAME, "Change the columns of dashboard '" + name + "'", design));
            write(doc.copy().dashboardConfig(IndexChecks.dashboardConfig(source, fields, timeField, window)).build());
        }
    }

    // Where the checks' counts came from, and the core fields most events hold no value for (seen in chat 177:
    // event.action empty in 2,233 of 2,457 documents, and nothing said so), or that the index doesn't list (an
    // Elasticsearch index's fields as Stroom lists them; null when not known): a finding for the user, a failure
    // only for the time field, which every document needs.
    @SuppressWarnings("unchecked")
    private void expectationsSaid(final Map<String, Object> found,
                                  final EventsExpectations.Sample events,
                                  final Set<String> listed) {
        if (events == null || !events.any()) {
            found.put("expected_counts", "given, not counted from the Events: no plan is kept with the indexing "
                                         + "XSLT (pipeline_uuid names the indexing pipeline), or no events were read"
                                         + (events == null || events.unread().isEmpty()
                    ? ""
                    : " (" + String.join("; ", events.unread()) + ")"));
            return;
        }
        found.put("expected_counts", "from the Events: " + events.said()
                                     + (events.unread().isEmpty()
                ? ""
                : "; not read: " + String.join("; ", events.unread())));
        final List<Map<String, Object>> empty = events.mostlyEmpty(plans.ecs(), listed);
        if (empty.isEmpty()) {
            return;
        }
        found.put("findings", Map.of("core_fields_mostly_empty", empty, "next", "Tell the user which core fields "
                                     + "most events (or all: not_in_index, never searchable) leave empty, and the "
                                     + "Events path each is read from: the translation may not write it for most "
                                     + "kinds of event, or the plan reads it from the wrong path. Ask whether to "
                                     + "map it from another path, or leave it as it is."));
        empty.stream().filter(finding -> Boolean.TRUE.equals(finding.get("required"))).forEach(finding -> {
            final Map<String, Object> check = new LinkedHashMap<>();
            check.put("check", "the time field " + finding.get("field") + " has a value");
            check.put("pass", false);
            check.put("empty_in", finding.get("empty_in"));
            check.put("source", finding.get("source"));
            ((List<Map<String, Object>>) found.get("checks")).add(check);
            found.put("passed", false);
        });
    }

    // The plan kept with the indexing XSLT: the pipeline's given, else the build's own pipeline writing to the
    // index. None for a discovery plan, which reads raw data, not Events.
    private Optional<FieldPlan> keptPlan(final List<WriteGuard.BuildDoc> contents,
                                         final String pipelineUuid,
                                         final String docType,
                                         final String indexUuid,
                                         final String indexName) {
        final List<String> candidates = new ArrayList<>();
        if (NullSafe.isNonBlankString(pipelineUuid)) {
            candidates.add(pipelineUuid);
        } else {
            for (final WriteGuard.BuildDoc doc : contents) {
                if (!PipelineDoc.TYPE.equals(doc.type())) {
                    continue;
                }
                try {
                    if (IndexDocs.writesTo(pipelineShapes.merged(doc.uuid()).properties(), docType, indexUuid,
                            indexName)) {
                        candidates.add(doc.uuid());
                    }
                } catch (final RuntimeException e) {
                    // One Stroom can't load writes nowhere.
                }
            }
        }
        return candidates.stream()
                .map(plans::kept)
                .flatMap(Optional::stream)
                .filter(kept -> kept.discovery() == null)
                .findFirst();
    }

    // Every field the pipeline writes for these Events must be in the index (asked for by the user: an index that
    // lacks one is an error to put to them, not something its documentation leaves out unsaid).
    @SuppressWarnings("unchecked")
    private void lacking(final Map<String, Object> found,
                         final String pipelineUuid,
                         final DocRef source,
                         final List<Long> streamIds,
                         final Index index) {
        final Map<String, List<String>> lacking = indexFields.writtenNotIndexed(pipelineUuid, source, streamIds);
        if (lacking.isEmpty()) {
            return;
        }
        final Map<String, Object> check = new LinkedHashMap<>();
        check.put("check", "every field the pipeline writes for these Events is in the index");
        check.put("missing", lacking);
        check.put("pass", false);
        check.put("next", IndexFields.missingFieldsSaid(lacking, NullSafe.nonBlankStringElse(index.indexName(),
                index.name())));
        ((List<Map<String, Object>>) found.get("checks")).add(check);
        found.put("passed", false);
    }

    // The plan's 'indexed' step is done for the build's pipelines that write to this index, while the streams
    // searched are still the build's current Events (an Events reprocess makes new ones, which the index lacks).
    private List<String> recordVerified(final List<WriteGuard.BuildDoc> contents,
                                        final String docType,
                                        final String indexUuid,
                                        final String indexName,
                                        final List<Long> streamIds) {
        final Map<String, Object> indexed = Map.of(BuildRecords.INDEXED_STREAMS,
                List.copyOf(new TreeSet<>(streamIds)));
        final List<String> recorded = new ArrayList<>();
        for (final WriteGuard.BuildDoc doc : contents) {
            if (!PipelineDoc.TYPE.equals(doc.type())) {
                continue;
            }
            final MergedPipeline merged;
            try {
                merged = pipelineShapes.merged(doc.uuid());
            } catch (final RuntimeException e) {
                // One Stroom can't load: not recorded as verified, the others still are.
                continue;
            }
            if (IndexDocs.writesTo(merged.properties(), docType, indexUuid, indexName)
                && records.recordPassed(new DocRef(PipelineDoc.TYPE, doc.uuid(), doc.name()), BuildRecords.VERIFIED,
                    null, indexed).filter("recorded"::equals).isPresent()) {
                recorded.add(doc.name());
            }
        }
        return recorded;
    }

    private Index read(final DocRef ref) {
        if (LuceneIndexDoc.TYPE.equals(ref.getType())) {
            return documentAccess.read(ref, LuceneIndexDoc.class)
                    .map(doc -> new Index(doc.getName(), null, doc.getTimeField(), null))
                    .orElseThrow(() -> unreadable(ref));
        }
        return documentAccess.read(ref, ElasticIndexDoc.class)
                .map(doc -> new Index(doc.getName(), doc.getIndexName(), doc.getTimeField(), doc))
                .orElseThrow(() -> unreadable(ref));
    }

    private static ToolRefusal unreadable(final DocRef ref) {
        return new ToolRefusal(ref.getType() + " " + ref.getUuid() + " can't be read: it doesn't exist, or you "
                               + "don't have permission to view it");
    }

    private DashboardDoc dashboardDoc(final DocRef ref) {
        return documentAccess.read(ref, DashboardDoc.class)
                .orElseThrow(() -> new ToolRefusal("Dashboard " + ref.getUuid() + " can't be read"));
    }

    @SuppressWarnings("unchecked")
    private DashboardDoc write(final DashboardDoc doc) {
        final DocumentActionHandler<DashboardDoc> handler = documentAccess.handler(DashboardDoc.TYPE)
                .orElseThrow(() -> new ToolRefusal("Stroom has no Dashboard store"));
        return handler.writeDocument(doc);
    }

    private static List<String> shownColumns(final DashboardConfig config) {
        if (config == null) {
            return List.of();
        }
        for (final ComponentConfig component : NullSafe.list(config.getComponents())) {
            if (component.getSettings() instanceof final TableComponentSettings table) {
                return NullSafe.list(table.getColumns()).stream()
                        .filter(column -> !Boolean.FALSE.equals(column.isVisible()))
                        .map(Column::getName)
                        .toList();
            }
        }
        return List.of();
    }

    private String backend(final ToolCall call) {
        final String backend = required(call, BACKEND_ARG);
        if (!IndexingHelpers.INDEX_TYPE.containsKey(backend)) {
            throw new ToolRefusal("backend is lucene or elasticsearch, not '" + backend + "'");
        }
        return backend;
    }

    private static List<Map<String, String>> exact(final ToolCall call) {
        final List<Map<String, String>> exact = new ArrayList<>();
        for (final Object item : ToolArgs.oneOrMore(call, EXACT_ARG)) {
            if (!(item instanceof final Map<?, ?> map) || map.get("field") == null) {
                throw new ToolRefusal("exact are checks, each {'field': ..., 'value': ...}");
            }
            final Map<String, String> check = new LinkedHashMap<>();
            check.put("field", String.valueOf(map.get("field")));
            check.put("value", map.get("value") == null
                    ? ""
                    : String.valueOf(map.get("value")));
            if (map.get("expected") != null) {
                // Compared with the Events' count, which decides.
                check.put("expected", String.valueOf(map.get("expected")));
            }
            exact.add(check);
        }
        return exact;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> timeRange(final ToolCall call) {
        final Object value = ToolArgs.object(call, TIME_RANGE_ARG);
        if (value == null) {
            return null;
        }
        if (!(value instanceof final Map<?, ?> map) || map.get("field") == null || map.get("from") == null
            || map.get("to") == null) {
            throw new ToolRefusal("time_range is {'field': ..., 'from': ISO, 'to': ISO, 'expected': n}");
        }
        return new LinkedHashMap<>((Map<String, Object>) map);
    }

    private long whole(final ToolCall call, final String name, final Long otherwise, final Long most) {
        final Object value = ToolArgs.raw(call, name);
        if (value == null || (value instanceof final String text && text.isBlank())) {
            if (otherwise == null) {
                throw new ToolRefusal("Give " + name);
            }
            return otherwise;
        }
        final long number;
        try {
            number = value instanceof final Number given
                    ? given.longValue()
                    : Long.parseLong(String.valueOf(value).strip());
        } catch (final NumberFormatException e) {
            throw new ToolRefusal(name + " must be a whole number, not '" + value + "'");
        }
        if (number < 0 || (most != null && number > most)) {
            throw new ToolRefusal(name + " must be a whole number from 0" + (most == null
                    ? ""
                    : " to " + most) + ", not '" + value + "'");
        }
        return number;
    }

    private static Object checksArg(final ToolCall call) {
        final Object value = ToolArgs.raw(call, CHECKS_ARG);
        if (value == null) {
            return true;
        }
        return value instanceof final String text
                ? Boolean.parseBoolean(text.strip())
                : value;
    }

    private static boolean isRange(final String condition) {
        return List.of(Condition.BETWEEN, Condition.GREATER_THAN, Condition.GREATER_THAN_OR_EQUAL_TO,
                Condition.LESS_THAN, Condition.LESS_THAN_OR_EQUAL_TO).contains(Condition.valueOf(condition));
    }

    private String required(final ToolCall call, final String name) {
        final String value = stringArg(call, name);
        if (value == null) {
            throw new ToolRefusal("Give " + name);
        }
        return value;
    }


    // --------------------------------------------------------------------------------


    private record Index(String name, String indexName, String timeField, ElasticIndexDoc elastic) {

    }
}
