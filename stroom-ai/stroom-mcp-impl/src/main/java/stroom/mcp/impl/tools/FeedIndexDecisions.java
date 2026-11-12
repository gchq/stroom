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
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.ToolArgs;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.indexing.FeedIndexes;
import stroom.ai.impl.tools.indexing.FeedIndexes.Clash;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.PlannedField;
import stroom.ai.impl.tools.sample.SourceTypeRegistry;
import stroom.ai.impl.tools.sample.SurveyDoc;
import stroom.docref.DocRef;
import stroom.feed.shared.FeedDoc;
import stroom.index.shared.LuceneIndexDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/// An aggregated feed's indexes across its scoped builds ([FeedIndexes]): the indexing backend and field naming the
/// feed's builds share, made once with the user and kept in the registry's machine block; one index per output feed,
/// every group writing to that feed's Events sharing it; its fields the union of theirs, checked for clashes with the
/// fields the index has already before a build adds its own; and those fields recorded when the build is promoted.
/// Nothing here applies to a build with no source type registry.
class FeedIndexDecisions {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FeedIndexDecisions.class);

    /// The build record's indexing choice holding each plan's output feed, by index name.
    static final String OUTPUT_FEEDS = "output_feeds";
    /// The argument that says the user asked to change a shared decision.
    static final String CHANGE_SHARED_ARG = "change_shared";
    /// The share a path a source type group writes is planned with, when no Event of the sample has it.
    static final double WRITTEN_SHARE = 0.001;
    private static final String BACKEND = "backend";
    private static final String INDEX = "index";
    private static final Pattern NOT_A_NAME = Pattern.compile("[^a-z0-9]+");

    private final SourceTypeGroups groups;
    private final SourceTypeRegistries registries;
    private final StreamReader streamReader;
    private final BuildRecords records;
    private final WriteGuard guard;
    private final DocumentAccess documentAccess;
    private final Consent consent;
    private final IndexFields indexFields;
    private final Provider<BuildPlan> buildPlan;

    @Inject
    FeedIndexDecisions(final SourceTypeGroups groups,
                       final SourceTypeRegistries registries,
                       final StreamReader streamReader,
                       final BuildRecords records,
                       final WriteGuard guard,
                       final DocumentAccess documentAccess,
                       final Consent consent,
                       final IndexFields indexFields,
                       final Provider<BuildPlan> buildPlan) {
        this.groups = groups;
        this.registries = registries;
        this.streamReader = streamReader;
        this.records = records;
        this.guard = guard;
        this.documentAccess = documentAccess;
        this.consent = consent;
        this.indexFields = indexFields;
        this.buildPlan = buildPlan;
    }

    /// @param context A call's context.
    /// @return The session's build, or null.
    String currentBuild(final ToolCallContext context) {
        return buildPlan.get().currentBuild(context).orElse(null);
    }

    /// @param build     The build the Events are from, or null.
    /// @param streamIds Events streams of one of its aggregated feed's output feeds.
    /// @return The Events paths the source type groups writing to that output feed write, as their mappings say
    /// (kept with each group's XSLT); none for a build of no aggregated feed.
    List<String> writtenByGroups(final String build, final List<Long> streamIds) {
        final Optional<SourceTypeGroups.Registry> found = registry(build);
        if (found.isEmpty() || NullSafe.isEmptyCollection(streamIds)) {
            return List.of();
        }
        final String outputFeed = outputFeedIn(build, found.get(), streamIds.getFirst());
        final Map<String, Object> state = found.get().found().state();
        if (outputFeed == null) {
            return List.of();
        }
        final List<String> descriptions = new ArrayList<>();
        final Set<String> read = new LinkedHashSet<>();
        for (final Map<String, Object> row : JsonValues.maps(state.get("rows"))) {
            final String uuid = JsonValues.text(JsonValues.mapOrEmpty(row.get("group_xslt")).get("uuid"));
            if (SourceTypeRegistry.TRANSLATED.equals(row.get("status")) && !uuid.isBlank() && read.add(uuid)
                && outputFeed.equals(FeedIndexes.outputFeedOf(state, row))) {
                documentAccess.read(new DocRef(XsltDoc.TYPE, uuid), XsltDoc.class)
                        .map(XsltDoc::getDescription)
                        .ifPresent(descriptions::add);
            }
        }
        final Set<String> paths = new LinkedHashSet<>();
        IndexingInputs.mappingFields(descriptions).forEach(leaf -> {
            final String path = leaf.entry().path();
            if (!NullSafe.isBlankString(path)) {
                paths.add(path);
            }
        });
        return List.copyOf(paths);
    }

    // --------------------------------------------------------------------------------
    // The feed's shared backend and naming.
    // --------------------------------------------------------------------------------

    /// @param build A build, or null.
    /// @return The shared indexing decisions of the aggregated feed the build works on (`backend`, `naming`,
    /// `decided`, and `feed`), as its registry keeps them; empty for a build of no aggregated feed, or before the
    /// first scoped build made them.
    Optional<Map<String, Object>> sharedIndexing(final String build) {
        return registry(build).flatMap(registry -> {
            final Map<String, Object> indexing = FeedIndexes.indexing(registry.found().state());
            if (indexing.get(BACKEND) == null) {
                return Optional.empty();
            }
            final Map<String, Object> out = new LinkedHashMap<>(indexing);
            out.put("feed", registry.feed());
            return Optional.of(out);
        });
    }

    /// Records a choice the user made in a form as the feed's shared decision, in the build's registry, where the
    /// build works on an aggregated feed whose registry it holds.
    ///
    /// @param context The call's context.
    /// @param build   The build, or null.
    /// @param backend The backend chosen, or null.
    /// @param naming  How fields are named, or null.
    void settle(final ToolCallContext context,
                final String build,
                final String backend,
                final Map<String, Object> naming) {
        registry(build).filter(registry -> !registry.found().promoted()).ifPresent(registry -> {
            final Map<String, Object> state = JsonValues.deepCopy(registry.found().state());
            FeedIndexes.settleIndexing(state, backend, naming, decided(build));
            write(context, build, registry, state);
        });
    }

    /// Refuses a backend the feed's shared decision doesn't have, unless the user asked to change it: then they
    /// confirm it in a form, and it is recorded in its place.
    ///
    /// @param call    The call.
    /// @param context Its context.
    /// @param build   The build, or null.
    /// @param backend The backend given.
    /// @param tool    The tool asking.
    /// @return True when the backend was changed at the user's word.
    /// @throws ToolRefusal for another backend the user hasn't asked for.
    boolean checkBackend(final ToolCall call,
                         final ToolCallContext context,
                         final String build,
                         final String backend,
                         final String tool) {
        final Optional<Map<String, Object>> shared = sharedIndexing(build);
        if (backend == null || shared.isEmpty() || backend.equals(shared.get().get(BACKEND))) {
            return false;
        }
        final String feed = JsonValues.text(shared.get().get("feed"));
        if (!changeAsked(call)) {
            throw new ToolRefusal("Feed '" + feed + "' indexes into " + shared.get().get(BACKEND) + ": a decision "
                                  + "its scoped builds share, made with the user by an earlier build and kept in "
                                  + kept(feed) + ". Use " + shared.get().get(BACKEND) + ". Only if the user asks to "
                                  + "change it, call again with " + CHANGE_SHARED_ARG + "=true: they confirm it in "
                                  + "a form, and every later build of the feed follows the new choice.");
        }
        final Map<String, Object> details = new LinkedHashMap<>();
        details.put("now", String.valueOf(shared.get().get(BACKEND)));
        details.put("kept in", kept(feed));
        details.put("applies to", "every later build of the feed; indexes already made keep their backend");
        consent.require(call, context, Consent.Request.confirmation(tool, "Change feed '" + feed + "' to index into "
                                                                          + backend, details));
        settle(context, build, backend, null);
        return true;
    }

    // --------------------------------------------------------------------------------
    // A plan for an output feed's index.
    // --------------------------------------------------------------------------------

    /// A drafted or amended plan as the output feed's index has it: the index the feed's groups share (its name,
    /// refused otherwise unless the user asked for a new one), its fields the union of theirs, and each field of
    /// this build's the index has otherwise put to the user in one form (another type: a field of another name, or
    /// a new version of the index; another meaning: one field read from both, or a field of another name).
    ///
    /// @param call      The call.
    /// @param context   Its context.
    /// @param build     The build the Events are from, or null.
    /// @param streamIds The Events streams the plan is for.
    /// @param plan      The plan.
    /// @param tool      The tool asking.
    /// @return The plan as the index has it, and what was done, said; the plan as given for a build of no
    /// aggregated feed.
    /// @throws ToolRefusal for an index name another output feed's index has, or not the output feed's own unless
    ///                     the user asked for a new one.
    ForPlan forPlan(final ToolCall call,
                    final ToolCallContext context,
                    final String build,
                    final List<Long> streamIds,
                    final FieldPlan plan,
                    final String tool) {
        if (plan.discovery() != null || NullSafe.isEmptyCollection(streamIds)) {
            return new ForPlan(plan, Map.of());
        }
        final Optional<SourceTypeGroups.Registry> found = registry(build);
        final String outputFeed = found.map(registry -> outputFeedIn(build, registry, streamIds.getFirst()))
                .orElse(null);
        if (outputFeed == null) {
            return new ForPlan(plan, Map.of());
        }
        final Map<String, Object> state = found.get().found().state();
        final String feed = found.get().feed();
        refuseOtherFeedsIndex(build, state, outputFeed, plan.indexName());
        final Map<String, Object> entry = FeedIndexes.index(state, outputFeed);
        final Map<String, Object> said = new LinkedHashMap<>();
        said.put("output_feed", outputFeed);
        said.put("groups", FeedIndexes.groupsTo(state, outputFeed));
        FieldPlan out = plan;
        final boolean versionChosen = outputFeed.equals(JsonValues.text(outputFeeds(build).get(plan.indexName())));
        if (!entry.isEmpty() && !FeedIndexes.sameIndex(plan, entry) && versionChosen) {
            // A new version the user chose earlier in the build (for a clash): every group's fields, nothing asked.
            said.put("new_version_of", JsonValues.text(entry.get(INDEX)));
        } else if (!entry.isEmpty() && !FeedIndexes.sameIndex(plan, entry)) {
            final String index = JsonValues.text(entry.get(INDEX));
            if (!changeAsked(call)) {
                throw new ToolRefusal("Output feed '" + outputFeed + "' has index '" + index + "', which every "
                                      + "source type group writing to it shares (kept in " + kept(feed) + "): "
                                      + "draft with index_name='" + index + "'. A new index version only where "
                                      + "the user chooses one for a clash (this tool asks them), or asks for one: "
                                      + "then call again with " + CHANGE_SHARED_ARG + "=true and they confirm it.");
            }
            final Map<String, Object> details = new LinkedHashMap<>();
            details.put("now", index);
            details.put("kept in", kept(feed));
            details.put("history", "documents already in " + index + " stay there: forward only");
            consent.require(call, context, Consent.Request.confirmation(tool, "Index feed '" + outputFeed
                                                                              + "' into a new index '"
                                                                              + plan.indexName() + "'", details));
            said.put("new_version_of", index);
        }
        out = FeedIndexes.withInherited(out, entry);
        if (!entry.isEmpty()) {
            final List<String> inherited = out.fields().stream()
                    .map(PlannedField::name)
                    .filter(name -> plan.fields().stream().noneMatch(field -> field.name().equals(name)))
                    .toList();
            said.put("inherited_fields", inherited);
        }
        final List<Clash> clashes = FeedIndexes.clashes(out, entry);
        if (!clashes.isEmpty()) {
            out = resolve(call, context, out, entry, clashes, suffix(build, state, outputFeed), outputFeed, tool,
                    said);
        }
        said.putIfAbsent(INDEX, out.indexName());
        rememberOutputFeed(build, out.indexName(), outputFeed);
        said.put("kept", "The feed's shared indexing decisions and each output feed's index are kept in " + kept(feed)
                         + ": every group writing to '" + outputFeed + "' shares this index, and promotion adds "
                         + "this build's new fields to it (forward only: documents indexed before don't get them).");
        return new ForPlan(out, said);
    }

    // The clashes put to the user in one form, each answer applied; without a form, said for the agent to ask.
    private FieldPlan resolve(final ToolCall call,
                              final ToolCallContext context,
                              final FieldPlan plan,
                              final Map<String, Object> entry,
                              final List<Clash> clashes,
                              final String suffix,
                              final String outputFeed,
                              final String tool,
                              final Map<String, Object> said) {
        final Map<String, Consent.Question> questions = new LinkedHashMap<>();
        final List<String> lines = new ArrayList<>(List.of("Fields of this build clash with index "
                                                           + JsonValues.text(entry.get(INDEX)) + ", which every "
                                                           + "group writing to feed " + outputFeed + " shares. "
                                                           + "Choose for each."));
        for (final Clash clash : clashes) {
            questions.put(clash.ours().name(), new Consent.Question(clash.question(),
                    List.copyOf(clash.options(suffix).keySet())));
            lines.add(clash.said());
        }
        final Optional<Map<String, String>> answers = consent.chooseEach(call, context, tool,
                String.join("\n", lines), questions);
        if (answers.isEmpty()) {
            said.put("index_clashes", clashes.stream().map(Clash::said).toList());
            said.put("clash_hint", "Ask the user about each of index_clashes: for another type, a field of another "
                                   + "name or a new version of the index; for another meaning, one field read from "
                                   + "both or a field of another name. Apply it with changes= (a field renamed: "
                                   + "remove it and add it under the new name). Promotion is held until none is "
                                   + "left.");
            return plan;
        }
        FieldPlan out = plan;
        final List<String> resolved = new ArrayList<>();
        for (final Clash clash : clashes) {
            final String label = answers.get().get(clash.ours().name());
            final String choice = clash.options(suffix).get(label);
            if (choice == null) {
                continue;
            }
            if (!out.indexName().equals(plan.indexName()) && !FeedIndexes.SAME_MEANING.equals(choice)) {
                // A new version already chosen: the index is fresh, so nothing else in it clashes.
                resolved.add(clash.ours().name() + ": in the new version " + out.indexName());
                continue;
            }
            out = FeedIndexes.resolved(out, entry, clash, choice, suffix);
            resolved.add(clash.ours().name() + ": " + label);
        }
        said.put("clashes_resolved", resolved);
        if (!out.indexName().equals(plan.indexName())) {
            said.put("new_version_of", plan.indexName());
            said.put(INDEX, out.indexName());
            said.put("new_version_hint", "The user chose a new index version, " + out.indexName() + ", with every "
                                         + "group's fields: make it (create_index_doc, and a new version of the "
                                         + "indexing pipeline: copy_pipeline) and index from now on. Documents "
                                         + "already in " + plan.indexName() + " stay there: forward only.");
        }
        return out;
    }

    /// Refuses a second index doc for an output feed's index an earlier build made: promotion adds a later build's
    /// fields to it.
    ///
    /// @param build The build.
    /// @param index The index's name (a Lucene Index doc's, or an Elasticsearch index's).
    /// @throws ToolRefusal for an index recorded with its document beside the feed.
    void refuseIndexMade(final String build, final String index) {
        if (NullSafe.isBlankString(index)) {
            return;
        }
        registry(build).ifPresent(registry -> {
            final Map<String, Object> state = registry.found().state();
            FeedIndexes.outputFeedOfIndex(state, index).ifPresent(outputFeed -> {
                final Map<String, Object> doc = JsonValues.mapOrEmpty(FeedIndexes.index(state, outputFeed)
                        .get("index_doc"));
                if (!doc.isEmpty() && guard.buildOf(new DocRef(JsonValues.text(doc.get("type")),
                        JsonValues.text(doc.get("uuid")))).isEmpty()) {
                    throw new ToolRefusal("Index '" + index + "' is the index of output feed '" + outputFeed + "', "
                                          + "made by an earlier build (" + JsonValues.text(doc.get("type")) + " '"
                                          + JsonValues.text(doc.get("name")) + "', kept in "
                                          + kept(registry.feed()) + "): don't make another. Promotion adds this "
                                          + "build's new fields to it; its indexing pipeline changes through a "
                                          + "working copy (copy_pipeline working_copy=true), its XSLT saved from the "
                                          + "plan (save_xslt index_plan=).");
                }
            });
        });
    }

    // An index name is one output feed's: a group with an output feed of its own has an index of its own.
    private void refuseOtherFeedsIndex(final String build,
                                       final Map<String, Object> state,
                                       final String outputFeed,
                                       final String index) {
        final Optional<String> recorded = FeedIndexes.outputFeedOfIndex(state, index)
                .or(() -> Optional.ofNullable(JsonValues.text(outputFeeds(build).get(index)))
                        .filter(feed -> !feed.isBlank()));
        if (recorded.isPresent() && !recorded.get().equals(outputFeed)) {
            throw new ToolRefusal("Index '" + index + "' is the index of output feed '" + recorded.get() + "': one "
                                  + "index per output feed, so the Events of '" + outputFeed + "' (a group's own "
                                  + "output feed, or the feed's own) go in an index of their own. Give another "
                                  + "index_name.");
        }
    }

    // --------------------------------------------------------------------------------
    // Promotion.
    // --------------------------------------------------------------------------------

    /// What promoting a build changes in its aggregated feed's indexes: each indexing plan the build keeps (with its
    /// indexing XSLT), the output feed it is for, and the fields it adds.
    ///
    /// @param build A build.
    /// @param docs  Its documents, as the plan lists them.
    /// @return The changes; none for a build of no aggregated feed, or one whose registry isn't in it.
    /// @throws ToolRefusal while a plan has a field the index has otherwise: promotion is held until the user
    ///                     chooses.
    List<IndexChange> changes(final String build, final List<Map<String, Object>> docs) {
        final Optional<SourceTypeGroups.Registry> found = registry(build).filter(r -> !r.found().promoted());
        if (found.isEmpty()) {
            return List.of();
        }
        final Map<String, Object> state = found.get().found().state();
        final List<String> outputFeeds = FeedIndexes.outputFeeds(state);
        final Map<String, Object> recorded = outputFeeds(build);
        final Map<String, IndexChange> byFeed = new LinkedHashMap<>();
        final List<String> held = new ArrayList<>();
        for (final Map<String, Object> doc : docs) {
            if (!XsltDoc.TYPE.equals(doc.get("type"))) {
                continue;
            }
            final Optional<FieldPlan> kept = keptPlan(String.valueOf(doc.get("uuid")));
            if (kept.isEmpty()) {
                continue;
            }
            final FieldPlan plan = kept.get();
            final String outputFeed = JsonValues.text(recorded.get(plan.indexName())).isBlank()
                    ? FeedIndexes.outputFeedOfIndex(state, plan.indexName())
                            .orElse(outputFeeds.size() == 1
                                    ? outputFeeds.getFirst()
                                    : null)
                    : JsonValues.text(recorded.get(plan.indexName()));
            if (outputFeed == null) {
                continue;
            }
            final Map<String, Object> entry = FeedIndexes.index(state, outputFeed);
            FeedIndexes.clashes(plan, entry).forEach(clash -> held.add(clash.said()));
            final List<String> added = FeedIndexes.newFields(plan, entry).stream().map(PlannedField::name).toList();
            byFeed.put(outputFeed, new IndexChange(found.get().feed(), outputFeed, plan, added,
                    indexDoc(build, plan, entry), !FeedIndexes.sameIndex(plan, entry),
                    JsonValues.text(entry.get(INDEX))));
        }
        if (!held.isEmpty()) {
            throw new ToolRefusal("Not promoted: held until the user chooses for each field this build's index plan "
                                  + "has that its output feed's index has otherwise. " + String.join(" ", held)
                                  + " Call draft_index_mapping for that index again (with the Events streams): it "
                                  + "asks the user in a form (for another type, a field of another name or a new "
                                  + "index version; for another meaning, one field read from both or a field of "
                                  + "another name), then save_xslt index_plan= the plan it gives, then promote_build.");
        }
        return List.copyOf(byFeed.values());
    }

    /// Applies a promotion's index changes, once the user approved it and before the build's documents are moved:
    /// the registry records each output feed's index and its fields; a Lucene index doc beside the feed gains the
    /// fields it lacks (an Elasticsearch index's come from its template, the cluster admin's to commit).
    ///
    /// @param context The call's context.
    /// @param build   The build.
    /// @param changes The changes, as [#changes] gave them.
    /// @return What was done, said.
    List<String> apply(final ToolCallContext context, final String build, final List<IndexChange> changes) {
        if (changes.isEmpty()) {
            return List.of();
        }
        final SourceTypeGroups.Registry registry = registry(build)
                .filter(found -> !found.found().promoted())
                .orElseThrow(() -> new ToolRefusal("The build's source type registry is gone"));
        final Map<String, Object> state = JsonValues.deepCopy(registry.found().state());
        final List<String> done = new ArrayList<>();
        for (final IndexChange change : changes) {
            final DocRef doc = change.indexDoc();
            FeedIndexes.record(state, change.outputFeed(), change.plan(), doc == null
                    ? null
                    : Map.of("type", doc.getType(), "uuid", doc.getUuid(), "name", NullSafe.string(doc.getName())),
                    decided(build));
            if (doc != null && LuceneIndexDoc.TYPE.equals(doc.getType()) && !change.added().isEmpty()
                && guard.buildOf(doc).isEmpty()) {
                final Map<String, Object> result = indexFields.addPromotedLuceneFields(doc, change.plan());
                done.add("index doc " + result.get(INDEX) + " gained fields "
                         + String.join(", ", JsonValues.strings(result.get("added"))));
            }
            done.add(change.said());
        }
        write(context, build, registry, state);
        return done;
    }

    // --------------------------------------------------------------------------------
    // Helpers.
    // --------------------------------------------------------------------------------

    /// @param call A call.
    /// @return True when the agent says the user asked to change a shared decision.
    static boolean changeAsked(final ToolCall call) {
        return call != null && ToolArgs.flag(call, CHANGE_SHARED_ARG);
    }

    /// @param feed An aggregated feed's name.
    /// @return Where its shared decisions are kept, said.
    static String kept(final String feed) {
        return "the registry '" + SourceTypeRegistry.docName(feed) + "' (its machine block: the feed's shared "
               + "decisions)";
    }

    /// @param group A source type group's name.
    /// @return What a field of its own is named with: its name in lower case, words joined by `_`.
    static String suffixOf(final String group) {
        final String flat = NOT_A_NAME.matcher(NullSafe.string(group).toLowerCase(Locale.ROOT)).replaceAll("_");
        return flat.replaceAll("^_+|_+$", "");
    }

    // The registry of the aggregated feed the build works on, if it has one.
    private Optional<SourceTypeGroups.Registry> registry(final String build) {
        if (NullSafe.isBlankString(build)) {
            return Optional.empty();
        }
        try {
            return Optional.of(groups.registry(build, List.of()));
        } catch (final ToolRefusal e) {
            return Optional.empty();
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No registry read for build {}: {}", build, e.getMessage()));
            return Optional.empty();
        }
    }

    // The output feed of the registry's an Events stream is for: the feed it is in, or, for the build's test feed
    // (where a build on an existing feed writes its Events), the feed the test feed stands in for. Null for a
    // stream of none of the registry's output feeds.
    private String outputFeedIn(final String build, final SourceTypeGroups.Registry registry, final long streamId) {
        final String feed;
        try {
            feed = streamReader.meta(streamId).getFeedName();
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No feed read for stream {}: {}", streamId, e.getMessage()));
            return null;
        }
        final List<String> outputFeeds = FeedIndexes.outputFeeds(registry.found().state());
        final Optional<String> named = outputFeeds.stream().filter(feed::equalsIgnoreCase).findFirst();
        if (named.isPresent()) {
            return named.get();
        }
        final Map<String, Object> kept = BuildRecords.docs(records.load(build));
        final boolean testFeed = guard.folderContents(build).stream()
                .filter(doc -> FeedDoc.TYPE.equals(doc.type()) && feed.equalsIgnoreCase(doc.name()))
                .map(doc -> JsonValues.mapOrEmpty(kept.get(doc.uuid())).get(BuildFeeds.OUTPUT_FOR))
                .anyMatch(sources -> JsonValues.strings(sources).stream().anyMatch(registry.feed()::equalsIgnoreCase));
        return testFeed
                ? registry.feed()
                : null;
    }

    // What a field of this build's own is named with: the build's first group writing to the output feed.
    private String suffix(final String build, final Map<String, Object> state, final String outputFeed) {
        for (final Map<String, Object> row : JsonValues.maps(state.get("rows"))) {
            final String uuid = JsonValues.text(JsonValues.mapOrEmpty(row.get("group_xslt")).get("uuid"));
            if (outputFeed.equals(FeedIndexes.outputFeedOf(state, row)) && !uuid.isBlank()
                && guard.buildOf(new DocRef(XsltDoc.TYPE, uuid)).filter(build::equals).isPresent()) {
                return suffixOf(JsonValues.text(row.get("group")));
            }
        }
        return null;
    }

    private Map<String, Object> outputFeeds(final String build) {
        if (NullSafe.isBlankString(build)) {
            return Map.of();
        }
        try {
            return JsonValues.mapOrEmpty(records.indexingChoices(build).get(OUTPUT_FEEDS));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No output feeds read for build {}: {}", build, e.getMessage()));
            return Map.of();
        }
    }

    private void rememberOutputFeed(final String build, final String index, final String outputFeed) {
        if (NullSafe.isBlankString(build)) {
            return;
        }
        final Map<String, Object> feeds = new LinkedHashMap<>(outputFeeds(build));
        if (outputFeed.equals(feeds.get(index))) {
            return;
        }
        feeds.put(index, outputFeed);
        records.rememberIndexingChoice(build, OUTPUT_FEEDS, feeds);
    }

    private Optional<FieldPlan> keptPlan(final String xsltUuid) {
        try {
            return documentAccess.read(new DocRef(XsltDoc.TYPE, xsltUuid), XsltDoc.class)
                    .flatMap(doc -> KeptMapping.readMapping(doc.getDescription()))
                    .filter(kept -> "index".equals(kept.kind()))
                    .map(kept -> FieldPlan.fromJson(kept.payload()))
                    .filter(plan -> plan.discovery() == null);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No index plan read from XSLT {}: {}", xsltUuid, e.getMessage()));
            return Optional.empty();
        }
    }

    // The index's document: the build's (a Lucene Index doc of the plan's index name, or an Elastic Index doc
    // naming it), else the one recorded for the same index; null when neither is.
    private DocRef indexDoc(final String build, final FieldPlan plan, final Map<String, Object> entry) {
        for (final WriteGuard.BuildDoc doc : guard.folderContents(build)) {
            if (LuceneIndexDoc.TYPE.equals(doc.type()) && plan.indexName().equals(doc.name())) {
                return doc.docRef();
            }
            if (ElasticIndexDoc.TYPE.equals(doc.type()) && documentAccess.read(doc.docRef(), ElasticIndexDoc.class)
                    .map(ElasticIndexDoc::getIndexName)
                    .filter(plan.indexName()::equals)
                    .isPresent()) {
                return doc.docRef();
            }
        }
        final Map<String, Object> recorded = JsonValues.mapOrEmpty(entry.get("index_doc"));
        if (FeedIndexes.sameIndex(plan, entry) && !recorded.isEmpty()) {
            return new DocRef(JsonValues.text(recorded.get("type")), JsonValues.text(recorded.get("uuid")),
                    JsonValues.text(recorded.get("name")));
        }
        return null;
    }

    private void write(final ToolCallContext context,
                       final String build,
                       final SourceTypeGroups.Registry registry,
                       final Map<String, Object> state) {
        state.put("updated", SurveyDoc.at(Instant.now()));
        registries.writeRegistry(context.session(), build, registry.feed(), registry.found(),
                SourceTypeRegistry.render(state));
    }

    private static FeedIndexes.Decided decided(final String build) {
        return new FeedIndexes.Decided("user", SurveyDoc.at(Instant.now()), build);
    }


    // --------------------------------------------------------------------------------


    /// A plan as its output feed's index has it.
    ///
    /// @param plan The plan.
    /// @param said What was done: `output_feed`, `index`, `groups`, `inherited_fields`, `clashes_resolved`,
    ///             `index_clashes` (without a form), `kept`; none for a build of no aggregated feed.
    record ForPlan(FieldPlan plan, Map<String, Object> said) {

    }

    /// What promoting a build changes in one output feed's index.
    ///
    /// @param feed       The aggregated feed.
    /// @param outputFeed The output feed.
    /// @param plan       The build's plan for its index.
    /// @param added      The fields it adds.
    /// @param indexDoc   The index's document, or null when none is known.
    /// @param newIndex   True for an index not recorded yet, or a new version of the one that is.
    /// @param replaces   The index recorded before, or "".
    record IndexChange(String feed,
                       String outputFeed,
                       FieldPlan plan,
                       List<String> added,
                       DocRef indexDoc,
                       boolean newIndex,
                       String replaces) {

        /// @return The change, said for the approval.
        String said() {
            if (newIndex && !replaces.isBlank()) {
                return "index " + plan.indexName() + " (feed " + outputFeed + "): a new version of " + replaces
                       + ", with " + plan.fields().size() + " fields, from now on";
            }
            if (newIndex) {
                return "index " + plan.indexName() + " (feed " + outputFeed + "): recorded as its index, with "
                       + plan.fields().size() + " fields";
            }
            return "index " + plan.indexName() + " (feed " + outputFeed + "): " + (added.isEmpty()
                    ? "no new fields"
                    : "adds " + String.join(", ", added) + " (forward only: documents indexed before don't have them)");
        }
    }
}
