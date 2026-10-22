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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.feed.shared.FeedDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/// The onboarding plan, with its state read from the build (ported from the Stroom MCP server's `tools/plan.py`).
///
/// A model that treats each request as one tool call stops when the tool returns. So the plan is not only in the
/// prompts: start_onboarding returns it, build_status derives each step's state from what the build holds (feed,
/// sample streams, converter, XSLT with its mapping, pipeline, a clean step, Events, documentation, index), and
/// every write tool's result carries `next`, the first unfinished step and the one call that does it.
///
/// A write tool ends with [#withNext] (or [#nextFor], for a document whose build it hasn't named) on its result.
@Singleton
public class BuildPlan {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(BuildPlan.class);

    /// Which parser element reads which profiled format.
    public static final Map<String, List<String>> PARSER_FOR_FORMAT = parsers();
    /// The formats a text converter turns into XML.
    public static final Set<String> TEXT_FORMATS = Set.of("delimited", "syslog rfc5424", "syslog rfc3164",
            "key=value", "unknown text", "cef", "multi-line text", "cri");
    /// With the plan when a build starts. Seen in testing: no task list kept, and the agent stopped at the end of
    /// each stage to report.
    public static final String TASK_LIST = "With a task-list tool (Stroom AI: task_list), write plan's steps into "
                                           + "it now, and keep it to each result's next.progress. Go on from stage "
                                           + "to stage without stopping to report.";
    /// What an agent is told of the tools named in `next`, hints and refusals.
    public static final String HIDDEN_TOOLS = "A tool this server names (in `next`, a hint or a refusal) may not be "
                                              + "in your tool list yet: some clients hide part of a server's tools "
                                              + "behind tools that enable a group of them (VS Code: activate_*). "
                                              + "Call the one in your tool list whose description names it, then "
                                              + "the named tool. Only names your list shows: the client makes them, "
                                              + "they differ from session to session, and a guessed one only fails "
                                              + "(seen: 360 calls to made-up activate_* names). If none names it, "
                                              + "ask the user to enable it. Never work around a hidden tool with "
                                              + "others.";
    /// The stage a step is in.
    public static final String STAGE_EVENTS = "1 events";
    /// The indexing stage.
    public static final String STAGE_INDEXING = "2 indexing";
    /// The output stage.
    public static final String STAGE_OUTPUT = "3 output";
    /// The last step.
    public static final String STAGE_FINISH = "finish";

    private static final List<List<String>> STAGE_1 = List.of(
            List.of("feed", "Create the feed in the build", "create_feed"),
            List.of("samples", "Upload every sample file as its own stream",
                    "upload_sample files=[their paths] (sample= only for text pasted into the chat)"),
            List.of("converter", "Text converter for the format: build_data_splitter infers the Data Splitter from "
                                 + "the sample text (for XML fragments: the wrapper, as the environment writes them)",
                    "build_data_splitter stream_ids=<the sample streams>, save_text_converter"),
            List.of("translation", "Translation XSLT from a mapping: draft it from the sample, decide the action "
                                   + "elements, generate and save it with the mapping",
                    "draft_translation_mapping, build_translation_xslt build=... name=... (uuid=... to replace)"),
            List.of("pipeline", "Events pipeline as a child of the right template, with its text converter and XSLT "
                                + "set (create_pipeline fills them from the build; update_pipeline sets a missing "
                                + "one)",
                    "find_pipeline_templates stage=translation, create_pipeline, update_pipeline"),
            List.of("stepped", "Every sample record stepped clean",
                    "step_sample over all sample streams (an existing feed's: step_records over the survey's "
                    + "locations when next gives them); fix the mapping and build_translation_xslt uuid=... in "
                    + "between"),
            List.of("covered", "An existing feed surveyed until covered: no new kind of event in a full batch, or "
                               + "every stream read; every location stepped clean",
                    "survey_feed build=..., step_records over every location"),
            List.of("processed", "Sample streams processed into Events",
                    "create_processor_filter, wait_for_processing"),
            List.of("validated", "Events validated against the schema and the quality rules", "check_events"),
            List.of("documented", "Events pipeline documented, with its Field mapping generated",
                    "write_documentation stream_ids=..."));
    private static final List<List<String>> STAGE_2 = List.of(
            List.of("index", "Index doc for the agreed backend, convention and name",
                    "get_field_conventions, draft_index_mapping, create_index_doc"),
            List.of("indexing_pipeline", "Indexing pipeline from the plan (XSLT saved with index_plan)",
                    "save_xslt index_plan=... (no code), create_indexing_pipeline"),
            List.of("index_template", "Elasticsearch: the index template agreed with the user, built from the example "
                                      + "index template they pasted (or confirmed without one), then committed to the "
                                      + "cluster by them",
                    "propose_index_template example_template=<theirs>, check_index_template"),
            List.of("indexed", "Sample Events indexed and found by verify_index's searches (stepping clean is not "
                               + "indexing)", "create_processor_filter, wait_for_processing, verify_index"),
            List.of("index_documented", "Indexing pipeline documented",
                    "write_documentation stream_ids=<Events streams>"));
    private static final List<List<String>> STAGE_3 = List.of(
            List.of("cef_mapping", "CEF mapping drafted from the Events and saved with its XSLT",
                    "draft_cef_mapping stream_ids=<Events streams> build=... name=..."),
            List.of("cef_pipeline", "CEF output pipeline with its XSLT and KafkaConfig set",
                    "create_pipeline (a forwarding template, or standalone=kafka), update_pipeline"),
            List.of("cef_stepped", "The Events stepped clean through it (stepping sends nothing)",
                    "step_sample over Events streams"),
            List.of("cef_documented", "CEF pipeline documented", "write_documentation stream_ids=<Events streams>"));
    private static final List<List<String>> FINISH = List.of(
            List.of("promoted", "Promoted beside sibling sources, filters handed over disabled",
                    "build_status, promote_build"));
    // The plan step each core tool implements, put first in its description so a client that picks tools by
    // similarity to the prompt pulls the whole onboarding chain for an "onboard these logs" request.
    private static final Map<String, String> STEP_OF = stepOf();
    // Parsers that read the raw data themselves: no text converter before them.
    private static final Set<String> NO_CONVERTER = Set.of("JSONParser", "XMLParser");
    // A sample's processor filter is created enabled once the user approves it in a form (asked for by the user);
    // one made disabled (before, or a feed's) is the user's to enable.
    private static final String APPROVE = "the user approves it in a form, and processing starts";
    private static final String ASK = "ask them to, on the pipeline's Processors tab, if they haven't said";
    private static final String DOCUMENTATION = "<the documentation, from the documentation guide>";
    // The steps whose call depends on how processing stands, as step:processing.
    private static final Set<String> CALLS = Set.of("stepped:surveyed", "processed:disabled", "processed:running",
            "index_template:unstepped", "indexed:disabled", "indexed:unstepped", "indexed:running",
            "indexed:no_index", "indexed:finished", "documented:edited");

    private final BuildFacts facts;
    private final WriteGuard guard;
    private final Consent consent;
    private final BuildMemory memory;

    /// @param facts   What the plan reads from Stroom about a build.
    /// @param guard   Finds the build a document is in.
    /// @param consent Gives what the user changed in the call's forms.
    /// @param memory  The build each session is working on.
    @Inject
    BuildPlan(final BuildFacts facts, final WriteGuard guard, final Consent consent, final BuildMemory memory) {
        this.facts = facts;
        this.guard = guard;
        this.consent = consent;
        this.memory = memory;
    }

    /// @return Every step of the plan, in order: each `step`, `what`, `tools` and `stage`.
    public static List<Map<String, Object>> checklist() {
        final List<Map<String, Object>> steps = new ArrayList<>();
        addSteps(steps, STAGE_EVENTS, STAGE_1);
        addSteps(steps, STAGE_INDEXING, STAGE_2);
        addSteps(steps, STAGE_OUTPUT, STAGE_3);
        addSteps(steps, STAGE_FINISH, FINISH);
        return steps;
    }

    /// @return A new source's steps: without an existing feed's survey rounds or CEF output, which other builds
    /// take.
    public static List<Map<String, Object>> onboardingChecklist() {
        return checklist().stream()
                .filter(step -> !"covered".equals(step.get("step")) && !STAGE_OUTPUT.equals(step.get("stage")))
                .toList();
    }

    /// @param toolName A tool's name.
    /// @return What its description starts with, naming its onboarding plan step (e.g. `Onboarding step 5 of 15
    /// (pipeline): `, `Onboarding start: `, `Onboarding, any step: `), or an empty string for a tool that is no step.
    public static String onboardingLabel(final String toolName) {
        final String step = STEP_OF.get(toolName);
        if (step == null) {
            return "";
        }
        final List<Object> steps = onboardingChecklist().stream().map(item -> item.get("step")).toList();
        if (steps.contains(step)) {
            return "Onboarding step " + (steps.indexOf(step) + 1) + " of " + steps.size() + " (" + step + "): ";
        }
        return "any step".equals(step)
                ? "Onboarding, any step: "
                : "Onboarding start: ";
    }

    /// Remembers the build the user is working on, so tools called without `build` fall back to it (in this
    /// node's memory, for the session: a call that can't be helped is told to name the build).
    ///
    /// @param context The call's context.
    /// @param build   The build, or null (nothing is remembered).
    public void rememberBuild(final ToolCallContext context, final String build) {
        memory.remember(context == null
                ? null
                : context.session(), build);
    }

    /// @param context The call's context.
    /// @param build   The build the call names, or null.
    /// @param tool    The tool, to say which needs it.
    /// @return The build named (remembered for the session), or else the one the session last worked on.
    /// @throws ToolRefusal if neither is known.
    public String resolveBuild(final ToolCallContext context, final String build, final String tool) {
        if (NullSafe.isNonBlankString(build)) {
            rememberBuild(context, build);
            return build;
        }
        return memory.current(context == null
                        ? null
                        : context.session())
                .orElseThrow(() -> new ToolRefusal(tool + " needs build: the build this work belongs to, as "
                                                   + "start_onboarding or start_build named it (e.g. "
                                                   + "'onboard-acme-door'). build_status shows a build's state."));
    }

    /// @param ref A document.
    /// @return The build a managed document belongs to: the build folder it is in. Empty for any other document,
    /// or if it can't be told.
    public Optional<String> buildOf(final DocRef ref) {
        try {
            return guard.tags(ref).contains(WriteGuard.MANAGED)
                    ? guard.buildOf(ref)
                    : Optional.empty();
        } catch (final RuntimeException e) {
            LOGGER.debug("Unable to tell the build of {}", ref, e);
            return Optional.empty();
        }
    }

    /// @param build A build, or null.
    /// @return The existing feeds the build works from ([BuildFacts#sourceFeeds]), with its documents read here;
    /// none for no build.
    public List<String> worksFrom(final String build) {
        if (NullSafe.isBlankString(build)) {
            return List.of();
        }
        return facts.sourceFeeds(build, facts.buildDocs(build));
    }

    /// @param build A build.
    /// @return The profiled format of the build's newest raw sample stream (its head): `stream_id`, `feed`,
    /// `format`, `namespace`, `root`, `suggested_parser` and `needs_text_converter`; empty without one.
    public Optional<Map<String, Object>> sampleFormat(final String build) {
        final List<Map<String, Object>> docs = facts.buildDocs(build);
        return sampleFormat(facts.sampleStreams(build, docs));
    }

    /// @param build The build, or null.
    /// @param made  A document the calling tool has just created, which counts whether or not the build's listing
    ///              shows it yet, or null.
    /// @return The first unfinished step of the build's plan, or empty when the build is unknown or unreadable
    /// (the plan is advice: never fail a write over it).
    public Optional<Map<String, Object>> nextStep(final String build, final DocRef made) {
        if (NullSafe.isBlankString(build)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(JsonValues.map(status(build, made).get("next")));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No next step for build {}: {}", build, e.getMessage()), e);
            return Optional.empty();
        }
    }

    /// [#withNext] for the build the document is in, when it is in one: a save by uuid, or a copy, names no build
    /// (seen: such results carried no next, and the agent lost its place in the plan).
    ///
    /// @param context The call's context.
    /// @param ref     The document the tool wrote.
    /// @param result  The tool's result.
    /// @return The result, with `next` if the document is in a build.
    public Map<String, Object> nextFor(final ToolCallContext context,
                                       final DocRef ref,
                                       final Map<String, Object> result) {
        final Optional<String> build = buildOf(ref);
        return build.isPresent()
                ? withNext(context, build.get(), result)
                : result;
    }

    /// The tool result with `next`, the plan's next step, added (and a reminder that the work is not done:
    /// `done: false` until the next step is promotion). A confirmation or approval round (a result with `status`)
    /// passes through untouched. Where the user changed a proposed value in the call's form, the result says so
    /// (`changed_by_user`, and a note), so the agent doesn't carry on with its own value. The build is remembered for
    /// the session.
    ///
    /// @param context The call's context, or null.
    /// @param build   The build the result belongs to, or null.
    /// @param result  The tool's result.
    /// @return The result, with `next`.
    public Map<String, Object> withNext(final ToolCallContext context,
                                        final String build,
                                        final Map<String, Object> result) {
        if (result == null || result.containsKey("status")) {
            return result;
        }
        final Map<String, Object> out = new LinkedHashMap<>(result);
        final Map<String, Consent.Change> changes = context == null
                ? Map.of()
                : consent.changes(context);
        if (!changes.isEmpty()) {
            final Map<String, Object> changed = new LinkedHashMap<>();
            final List<String> said = new ArrayList<>();
            changes.forEach((key, change) -> {
                changed.put(key, change.theirs());
                said.add("the user changed " + key.replace('_', ' ') + " from '" + change.proposed() + "' to '"
                         + change.theirs() + "': use '" + change.theirs() + "' from here on");
            });
            out.put("changed_by_user", changed);
            final String text = String.join("; ", said);
            out.put("note", out.get("note") instanceof final String note && !note.isEmpty()
                    ? note + " " + Character.toUpperCase(text.charAt(0)) + text.substring(1) + "."
                    : text);
        }
        rememberBuild(context, build);
        final DocRef made = out.get("type") instanceof final String type && out.get("uuid") instanceof final String uuid
                            && out.get("name") instanceof final String name
                ? new DocRef(type, uuid, name)
                : null;
        nextStep(build, made).ifPresent(next -> {
            final Map<String, Object> shown = new LinkedHashMap<>(next);
            final Object tool = JsonValues.map(next.get("call")) == null
                    ? null
                    : JsonValues.map(next.get("call")).get("tool");
            if (tool != null) {
                // Seen: create_pipeline hidden in a VS Code tool group; the agent wrote a handoff note and stopped.
                // Seen (Qwen3 Coder Next): 360 calls to activate_* names it made up, each refused as not existing.
                shown.put("if_missing", tool + " not in your tool list? Call the activate_* tool in your list whose "
                                        + "description names it, then " + tool + ". Only a name your list shows: "
                                        + "the names are your client's and a guessed one only fails. If none names "
                                        + "it, ask the user to enable " + tool + ". Never work around it.");
            }
            out.put("next", shown);
            if (!"promoted".equals(next.get("step"))) {
                out.put("done", false);
            }
        });
        return out;
    }

    /// Each plan step's state, read from the build.
    ///
    /// @param build A build.
    /// @param made  A document the calling tool has just created, which counts whether or not the listing shows it
    ///              yet, or null.
    /// @return The build's state, as build_status gives it: `build`, `documents`, `sample`, `feeds`,
    /// `sample_streams`, `xslts`, `pipelines`, `events_streams`, `before_promotion`, `steps`, `works_from` (on
    /// existing data) and `next`.
    public Map<String, Object> status(final String build, final DocRef made) {
        final List<Map<String, Object>> docs = new ArrayList<>(facts.buildDocs(build));
        if (made != null && docs.stream().noneMatch(doc -> made.getUuid().equals(doc.get("uuid")))) {
            // Just created, the explorer can leave it out for a moment (seen in the plan walk: create_feed's next
            // named the feed step again, inviting a second feed).
            final Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("type", made.getType());
            doc.put("uuid", made.getUuid());
            doc.put("name", made.getName());
            doc.put("path", null);
            doc.put("working_copy_of", null);
            docs.add(doc);
        }
        final Map<String, List<Map<String, Object>>> byType = new LinkedHashMap<>();
        docs.forEach(doc -> byType.computeIfAbsent(String.valueOf(doc.get("type")), ignored -> new ArrayList<>())
                .add(doc));
        final Map<String, List<Map<String, Object>>> streams = facts.sampleStreams(build, docs);
        final List<Map<String, Object>> raw = NullSafe.list(streams.get("Raw Events"));
        Map<String, Object> fmt = raw.isEmpty()
                ? null
                : sampleFormat(streams).orElse(null);
        final List<Map<String, Object>> xslts = xslts(of(byType, XsltDoc.TYPE));
        final List<Map<String, Object>> pipelines = pipelines(of(byType, PipelineDoc.TYPE));
        final List<Map<String, Object>> translation = withStage(pipelines, Set.of("translation"));
        // A discovery build indexes its raw streams as they are: no events pipeline, its discovery pipeline indexes.
        // Known by its pipeline's raw parser, or by its plan: an XML discovery pipeline from an XMLParser indexing
        // template reads as indexing.
        final boolean discovery = (!withStage(pipelines, Set.of("discovery")).isEmpty()
                                   || xslts.stream().anyMatch(x -> Boolean.TRUE.equals(x.get("discovery"))))
                                  && translation.isEmpty();
        List<Map<String, Object>> indexing = withStage(pipelines, Set.of("indexing", "discovery"));
        // A CEF output build: Events sent on as CEF, a stage of its own (none of the events or indexing steps).
        final List<Map<String, Object>> forwarding = withStage(pipelines, Set.of("forwarding"));
        final boolean cefBuild = !forwarding.isEmpty() || hasMapping(xslts, "cef");
        final Set<String> documented = new HashSet<>(names(of(byType, DocumentationDoc.TYPE)));
        // A build on data already in Stroom (an existing feed, a production pipeline's new version or working copy)
        // has no feed of its own to create and nothing to upload: its feeds, if any, are test feeds. Seen in VS
        // Code: told to create_feed and upload_sample, the agent uploaded a workspace file though the user chose
        // production streams, and made a new feed to build an indexing pipeline's next version.
        final List<String> sources = facts.sourceFeeds(build, docs);
        final List<Map<String, Object>> copies = pipelines.stream()
                .filter(p -> p.get("working_copy_of") != null || p.get("version_of") != null)
                .toList();
        final List<Map<String, Object>> surveys = of(byType, DocumentationDoc.TYPE).stream()
                .filter(doc -> String.valueOf(doc.get("name")).endsWith(StroomBuildFacts.SURVEY))
                .toList();
        final boolean surveyed = !surveys.isEmpty();
        // A survey of the build's own feed (a new source's sample) leaves it a new source: only the feeds outside the
        // build make it one on existing data (sourceFeeds leaves the build's own out). Seen: onboarding surveyed its
        // sample with the build, and the plan skipped indexing as if the feed already existed.
        final boolean existing = !sources.isEmpty() || !copies.isEmpty();
        final boolean ownFeed = !of(byType, FeedDoc.TYPE).isEmpty();
        final Map<String, Object> survey = surveyed && existing
                ? facts.surveyState(surveys).orElse(null)
                : null;
        if (survey != null && fmt == null && (JsonValues.truthy(survey.get("sample"))
                                              || JsonValues.truthy(survey.get("format")))) {
            fmt = formatFromSurvey(survey);
        }
        // Which stages the build does: on existing data, those it has the pipelines (or survey, or index doc) for. A
        // CEF output build does neither, whatever it works from.
        final boolean cefOnly = cefBuild && translation.isEmpty() && indexing.isEmpty() && !surveyed;
        final boolean newSource = !existing && !cefOnly;
        final boolean stage1 = newSource || !translation.isEmpty() || surveyed;
        // Indexing too when the build onboards an existing feed (surveyed for its events pipeline), as for a new
        // source, or holds any indexing work (an indexing XSLT saved from its plan comes before the index doc and
        // pipeline). Seen in Ask Stroom AI: the user asked for an index, and after the events pipeline's
        // documentation, and again after save_xslt index_plan=, next said "step 9 of 9, promoted".
        final boolean hasIndexDoc = !of(byType, "Index").isEmpty() || !of(byType, "ElasticIndex").isEmpty();
        final boolean stage2 = newSource || surveyed || !indexing.isEmpty() || hasIndexDoc
                               || hasMapping(xslts, "index");
        for (final Map<String, Object> p : pipelines) {
            if (p.get("working_copy_of") != null) {
                // A working copy's documentation is the production pipeline's, under its name.
                facts.docName(PipelineDoc.TYPE, String.valueOf(p.get("working_copy_of")))
                        .filter(documented::contains)
                        .ifPresent(original -> documented.add(String.valueOf(p.get("name"))));
            }
        }
        final List<Long> events = new ArrayList<>();
        of(byType, FeedDoc.TYPE).forEach(feed -> events.addAll(facts.eventsStreams(feed)));
        // Elasticsearch: the index template agreed with the user for each indexing pipeline's current code. Not
        // needed for Lucene; to do for Elasticsearch until every such pipeline has one.
        final List<Optional<String>> problems = new ArrayList<>();
        for (final Map<String, Object> p : indexing) {
            facts.elasticDestination(uuid(p)).ifPresent(destination ->
                    problems.add(facts.agreementProblem(uuid(p), destination)));
        }
        // To do once the build is known to be Elasticsearch; otherwise (Lucene, or no index yet) not needed.
        final Boolean agreed = !problems.isEmpty()
                ? (Boolean) problems.stream().noneMatch(Optional::isPresent)
                : !of(byType, "ElasticIndex").isEmpty() || discovery
                        ? Boolean.FALSE
                        : null;
        final List<String> checks = facts.buildChecks(docs);
        final boolean stale = checks.stream().anyMatch(check -> check.contains("Field mapping")
                                                                || check.contains("differs"));

        final Map<String, Boolean> done = new LinkedHashMap<>();
        done.put("feed", ownFeed);
        done.put("samples", !raw.isEmpty());
        // Not needed either once the events pipeline reads the raw data with a JSON or XML parser: its clean step is
        // what proves the format (seen in testing: a JSON-lines feed's working pipeline, and step 1 still to do).
        done.put("converter", fmt != null && JsonValues.truthy(fmt.get("needs_text_converter"))
                              && translation.stream().noneMatch(p -> p.get("parser") != null
                                                                    && NO_CONVERTER.contains(p.get("parser")))
                ? (Boolean) !of(byType, TextConverterDoc.TYPE).isEmpty()
                : null);
        // A copied pipeline's XSLT is there already, with its mapping or written by hand.
        done.put("translation", hasMapping(xslts, "translation")
                                || (existing && !translation.isEmpty()
                                    && xslts.stream().anyMatch(x -> !"index".equals(x.get("mapping")))));
        done.put("pipeline", !translation.isEmpty() && translation.stream().noneMatch(BuildPlan::hasMissing));
        done.put("stepped", translation.stream().anyMatch(p -> Boolean.TRUE.equals(p.get("stepped"))));
        // Not recorded (not needed) unless the build surveys an existing feed.
        done.put("covered", survey == null
                ? null
                : (Boolean) JsonValues.truthy(survey.get("saturated")));
        done.put("processed", !events.isEmpty());
        done.put("validated", !translation.isEmpty() && translation.stream().allMatch(facts::validated));
        done.put("documented", !translation.isEmpty() && allNamed(translation, documented) && !stale);
        // A discovery index's doc is made at verification, once documents are in it: its pipeline comes first. A new
        // version of an indexing pipeline has its plan from the one it copies; its index doc comes once indexed.
        done.put("index", hasIndexDoc || (discovery && !indexing.isEmpty())
                          || indexing.stream().anyMatch(copies::contains));
        done.put("indexing_pipeline", !indexing.isEmpty());
        done.put("index_template", agreed);
        // A working copy of a production indexing pipeline is stepped clean in the build; indexing production Events
        // into its index, after promotion, is the user's.
        done.put("indexed", !indexing.isEmpty() && indexing.stream().allMatch(p ->
                (p.get("working_copy_of") != null && Boolean.TRUE.equals(p.get("stepped"))) || facts.verified(p)));
        done.put("index_documented", !indexing.isEmpty() && allNamed(indexing, documented));
        done.put("cef_mapping", hasMapping(xslts, "cef")
                                || forwarding.stream().anyMatch(p -> p.get("working_copy_of") != null));
        done.put("cef_pipeline", !forwarding.isEmpty() && forwarding.stream().noneMatch(BuildPlan::hasMissing));
        done.put("cef_stepped", !forwarding.isEmpty()
                                && forwarding.stream().allMatch(p -> Boolean.TRUE.equals(p.get("stepped"))));
        done.put("cef_documented", !forwarding.isEmpty() && allNamed(forwarding, documented) && !stale);
        done.put("promoted", false);

        final List<Map<String, Object>> steps = steps(done, skipped(discovery, survey != null, existing,
                !copies.isEmpty() && !ownFeed, stage1, stage2, cefBuild));
        final Map<String, Object> next = steps.stream()
                .filter(step -> "to do".equals(step.get("state")))
                .findFirst()
                .orElse(null);
        final String nextStep = next == null
                ? null
                : String.valueOf(next.get("step"));
        String processing = null;
        Map<String, Object> index = null;
        final List<Map<String, Object>> unstepped = indexing.stream()
                .filter(p -> !Boolean.TRUE.equals(p.get("stepped")))
                .toList();
        if (("index_template".equals(nextStep) || "indexed".equals(nextStep)) && !unstepped.isEmpty()) {
            // The template is checked against the documents a clean step writes, and processing needs one: step
            // first.
            indexing = first(indexing, unstepped);
            processing = "unstepped";
        } else if ("processed".equals(nextStep) && !translation.isEmpty()) {
            // Its filter made, disabled until the user enables it (asked for by the user): wait for them, then for it.
            final List<Map<String, Object>> filters = facts.processingFilters(uuid(translation.getFirst()));
            processing = filters.isEmpty()
                    ? null
                    : filters.stream().noneMatch(f -> Boolean.TRUE.equals(f.get("enabled")))
                            ? "disabled"
                            : "running";
        } else if ("indexed".equals(nextStep)) {
            // Which call indexing needs next: a processor filter, the wait for it, or the searches that verify it.
            final Map<String, Object> pendingIndexing = indexing.stream()
                    .filter(p -> !facts.verified(p))
                    .findFirst()
                    .orElse(indexing.getFirst());
            indexing = first(indexing, List.of(pendingIndexing));
            final List<Map<String, Object>> filters = facts.processingFilters(uuid(pendingIndexing));
            processing = filters.isEmpty()
                    ? null
                    : filters.stream().noneMatch(f -> Boolean.TRUE.equals(f.get("enabled")))
                            ? "disabled"
                            : filters.stream().allMatch(f -> Boolean.TRUE.equals(f.get("finished")))
                                    ? "finished"
                                    : "running";
            final Map<String, Object> found = !of(byType, "ElasticIndex").isEmpty()
                    ? of(byType, "ElasticIndex").getFirst()
                    : !of(byType, "Index").isEmpty()
                            ? of(byType, "Index").getFirst()
                            : null;
            if (found != null) {
                index = new LinkedHashMap<>();
                index.put("uuid", found.get("uuid"));
                index.put("backend", "ElasticIndex".equals(found.get("type"))
                        ? "elasticsearch"
                        : "lucene");
                index.put("columns", facts.suggestedColumns(uuid(pendingIndexing)));
            } else if ("finished".equals(processing)) {
                // Searched through an index doc: a discovery build makes it now.
                processing = "no_index";
            }
        }
        final String named = sources.isEmpty()
                ? "the feed it works from"
                : String.join(", ", sources);
        final Object rawIds = orElse(orElse(raw.stream().map(m -> m.get("id")).toList(),
                survey == null
                        ? null
                        : survey.get("streams")), existing
                ? "<recent raw streams of " + named + " (" + streamMetaCall(sources, null) + ")>"
                : List.of());
        Object eventsIds = orElse(discovery
                ? rawIds
                : events.subList(0, Math.min(20, events.size())), existing
                ? "<recent Events streams from " + named + " (" + streamMetaCall(sources, "Events") + ")>"
                : List.of());
        String edited = null;
        if ("documented".equals(nextStep) && !translation.isEmpty()
            && checks.stream().anyMatch(check -> check.contains("differs from what its mapping generates"))) {
            // Changed by hand (save_xslt, or in Stroom): documentation would stay stale until the mapping is read
            // back.
            processing = "edited";
            edited = facts.keptXsltUuid(uuid(translation.getFirst())).orElse(null);
        } else if ("stepped".equals(nextStep) && survey != null && JsonValues.truthy(survey.get("locations"))) {
            // The survey's locations, when there are few enough to give: else step_sample over its streams, as the
            // plan says (seen in Ask Stroom AI: 66 locations, and create_pipeline's next said step_records with a
            // placeholder).
            processing = "surveyed";
        }
        if (cefOnly || (cefBuild && raw.isEmpty())) {
            eventsIds = orElse(eventsIds, "<recent Events streams from " + named
                                          + " (" + streamMetaCall(sources, "Events") + ")>");
        }
        final CallArgs args = new CallArgs(build, names(of(byType, FeedDoc.TYPE)), rawIds, eventsIds,
                translation.isEmpty()
                        ? null
                        : uuid(translation.getFirst()),
                indexing.isEmpty()
                        ? null
                        : uuid(indexing.getFirst()), processing, index, sources, discovery,
                survey != null && JsonValues.truthy(survey.get("locations"))
                        ? survey.get("locations")
                        : "<every location survey_feed gave>",
                forwarding.isEmpty()
                        ? null
                        : uuid(forwarding.getFirst()), edited);
        final NextCall call = nextCall(nextStep == null
                ? "promoted"
                : nextStep, args);
        // On existing data, before any pipeline: which stage comes next is the work's (a survey, a copy, an index).
        final boolean onlyExistingData = existing && !stage1 && !stage2 && !cefBuild;
        final Map<String, Object> start = onlyExistingData
                ? existingDataStart(sources, named)
                : null;

        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("build", build);
        result.put("documents", docs);
        result.put("sample", fmt);
        result.put("feeds", names(of(byType, FeedDoc.TYPE)));
        final Map<String, Object> sampleStreams = new LinkedHashMap<>();
        streams.forEach((type, metas) -> sampleStreams.put(type, metas.stream().map(m -> m.get("id")).toList()));
        result.put("sample_streams", sampleStreams);
        result.put("xslts", xslts);
        result.put("pipelines", pipelines.stream().map(BuildPlan::shown).toList());
        result.put("events_streams", events.subList(0, Math.min(20, events.size())));
        result.put("before_promotion", checks);
        result.put("steps", steps);
        if (existing) {
            result.put("works_from", sources);
        }
        if (next != null && !onlyExistingData) {
            final Map<String, Object> shown = new LinkedHashMap<>();
            shown.put("step", nextStep);
            shown.put("do", next.get("what"));
            shown.put("call", call.call());
            shown.put("then", call.then());
            shown.put("progress", progress(steps, nextStep));
            result.put("next", shown);
        } else if (onlyExistingData) {
            result.put("next", start);
        } else {
            final Map<String, Object> shown = new LinkedHashMap<>();
            shown.put("step", "promoted");
            shown.put("do", "Everything is in place: promote_build with the user's approval");
            shown.put("call", call.call());
            shown.put("then", call.then());
            result.put("next", shown);
        }
        return result;
    }

    /// The step's first call, with the arguments the build already gives and `<...>` for those still to decide, and
    /// what follows it. One call rather than a list of tools: a small model given several options deliberated
    /// between them until its context ran out.
    ///
    /// @param step A plan step.
    /// @param args What the build gives.
    /// @return The call and what follows it.
    public static NextCall nextCall(final String step, final CallArgs args) {
        final String build = args.build();
        final Object raw = args.raw();
        final Object events = args.events();
        final String tr = NullSafe.nonBlankStringElse(args.translation(), "<the events pipeline uuid>");
        final String ix = NullSafe.nonBlankStringElse(args.indexing(), "<the indexing pipeline uuid>");
        // Indexing Events from a pipeline outside the build (an existing feed's): the one that wrote them.
        final String source = NullSafe.nonBlankStringElse(args.translation(),
                "<the events pipeline that wrote those Events (processing_status on their feed names it)>");
        final String cx = NullSafe.nonBlankStringElse(args.cef(), "<the CEF pipeline uuid>");
        final Map<String, Object> index = NullSafe.map(args.index());
        final String key = step + ":" + args.processing();
        final String chosen = CALLS.contains(key)
                ? key
                : step;
        return switch (chosen) {
            case "feed" -> call("create_feed", args("build", build, "name", "<the feed name the user confirmed>"),
                    "then upload_sample once per sample file");
            case "samples" -> call("upload_sample", args("feed", args.feeds().isEmpty()
                            ? "<the feed>"
                            : args.feeds().getFirst(), "files",
                    List.of("<each sample file's path, as the user's terminal sees it>")),
                    "run each command it gives in the user's terminal (they approve it): each file goes from their "
                    + "disk to Stroom whole, and prints its stream id. sample= is only for text the user pasted into "
                    + "the chat. From then on give tools stream_ids, not the text");
            case "converter" -> call("build_data_splitter", args("stream_ids", raw, "build", build, "save_as",
                    "<converter name>"), "it saves the converter once every line parses");
            case "translation" -> call("draft_translation_mapping", args("stream_ids", raw),
                    "decide what its notes ask, then build_translation_xslt with the mapping, stream_ids, the "
                    + "splitter, build and name: it saves the XSLT with the mapping");
            case "pipeline" -> call("find_pipeline_templates", args("stage", "translation"),
                    "then create_pipeline from the best candidate: it takes the build's converter and XSLT");
            case "stepped" -> call("step_sample", args("pipeline_uuid", tr, "stream_ids", raw),
                    "until the verdict is clean: fix the mapping and build_translation_xslt uuid=... in between");
            case "stepped:surveyed" -> call("step_records", args("pipeline_uuid", tr, "locations", args.locations()),
                    "until clean: the feed's own records, stepped where they are; fix the mapping and "
                    + "build_translation_xslt uuid=... in between");
            case "covered" -> call("survey_feed", args("feed", args.sources().isEmpty()
                            ? "<the feed>"
                            : args.sources().getFirst(), "build", build),
                    "it carries on from the survey doc. New kinds of event: add rules (build_translation_xslt "
                    + "changes=), then step_records over every location so far; again until its coverage says "
                    + "covered. Kinds not worth translating: ask the user (set_shape_handling, a drop rule)");
            case "processed" -> call("create_processor_filter", args("pipeline_uuid", tr, "stream_ids", raw),
                    APPROVE + "; then wait_for_processing with its filter_id, and check_events");
            case "processed:disabled" -> call("wait_for_processing", args("pipeline_uuid", tr, "stream_ids", raw),
                    "only once the user says they enabled its filter (" + ASK + "); then check_events");
            case "processed:running" -> call("wait_for_processing", args("pipeline_uuid", tr, "stream_ids", raw),
                    "then check_events");
            case "validated" -> call("check_events", args("stream_ids", orElse(events,
                            "<the Events streams wait_for_processing gave>")),
                    "the server reads the Events itself: never read them back to send them");
            case "documented" -> call("write_documentation", args("build", build, "pipeline_uuid", tr, "stream_ids",
                    raw, "markdown", DOCUMENTATION), "the Field mapping section is generated");
            case "index" -> call("get_field_conventions", args(),
                    "go straight on, without asking first: it finds the backend and asks the user, in a form, how "
                    + "the index's fields are named; then draft_index_mapping as its reply says (index_name, "
                    + "events_stream_ids=" + pythonText(events) + "), and create_index_doc with plan= once the user "
                    + "confirms");
            case "indexing_pipeline" -> call("save_xslt", args("build", build, "name", "<index name>-XSLT",
                            "index_plan", "<the plan from draft_index_mapping>"),
                    "then create_indexing_pipeline with that XSLT and the index"
                    + (args.translation() != null || args.discovery()
                            ? ""
                            : ", and events_stream_ids=" + pythonText(events) + " (no events pipeline in this build)"));
            case "index_template" -> call("propose_index_template", args("pipeline_uuid", ix, "plan",
                            "<the index plan saved with the indexing XSLT>", "events_stream_ids", events,
                            "example_template", "<the user's example index template, exactly as they pasted it>"),
                    "the user confirms it in a form; give them its dev_tools to have it committed to the cluster, "
                    + "and wait for them to say it is. No example in this conversation: ask them to paste it, and "
                    + "end your turn");
            case "index_template:unstepped" -> call("step_sample", args("pipeline_uuid", ix, "stream_ids", events),
                    "until the verdict is clean (fix the index plan and save_xslt in between); then "
                    + "propose_index_template with the user's example");
            case "indexed" -> {
                final Map<String, Object> arguments = args("pipeline_uuid", ix, "stream_ids", events);
                if (!args.discovery()) {
                    arguments.put("source_pipeline_uuid", source);
                }
                yield call("create_processor_filter", arguments, APPROVE + " (Elasticsearch: once the agreed "
                        + "template is committed; an index this pipeline wrote to before is dropped first, from the "
                        + "drop_first reply's button); then wait_for_processing expect_events=false, and "
                        + "verify_index");
            }
            case "indexed:disabled" -> call("wait_for_processing", args("pipeline_uuid", ix, "stream_ids", events,
                            "expect_events", false),
                    "only once the user says they enabled its filter (" + ASK + "); then verify_index");
            case "indexed:unstepped" -> call("step_sample", args("pipeline_uuid", ix, "stream_ids", events),
                    "until the verdict is clean; then create_processor_filter");
            case "indexed:running" -> call("wait_for_processing", args("pipeline_uuid", ix, "stream_ids", events,
                    "expect_events", false), "then verify_index");
            case "indexed:no_index" -> call("create_index_doc", args("build", build, "backend", "elasticsearch",
                    "name", "<the index name>", "index_name", "<the index name>", "cluster_uuid",
                    "<the cluster the pipeline writes to>", "time_field", "@timestamp"),
                    "then verify_index through it");
            case "indexed:finished" -> {
                final List<String> columns = JsonValues.strings(index.get("columns"));
                yield call("verify_index", args("build", build,
                        "index_uuid", Objects.requireNonNullElse(index.get("uuid"), "<the index doc uuid>"),
                        "backend", Objects.requireNonNullElse(index.get("backend"), "<lucene or elasticsearch>"),
                        "stream_ids", events,
                        "expected_documents", "<the Events records in those streams>",
                        "fields", columns.isEmpty()
                                ? "<the columns the user chose>"
                                : "<the columns the user chose; suggest " + String.join(", ", columns)
                                  + " and confirm them>",
                        "pipeline_uuid", ix), "the step is done once its searches pass");
            }
            case "index_documented" -> call("write_documentation", args("build", build, "pipeline_uuid", ix,
                    "stream_ids", events, "markdown", DOCUMENTATION), "the Field mapping section is generated");
            case "documented:edited" -> call("rebuild_mapping", args("uuid", NullSafe.nonBlankStringElse(
                            args.editedXslt(), "<the translation XSLT>"), "pipeline_uuid", tr),
                    "its XSLT was changed by hand (save_xslt, or in Stroom): this reads the mapping back from it, "
                    + "proven on the sample, so the documentation can be generated; then write_documentation");
            case "cef_mapping" -> call("draft_cef_mapping", args("stream_ids", events, "feeds",
                            args.sources().isEmpty()
                                    ? "<the Events feed>"
                                    : List.copyOf(args.sources()), "build", build, "name", "<the CEF XSLT name>"),
                    "show the user its tables and not-sent list; their changes go back as overrides");
            case "cef_pipeline" -> call("create_pipeline", args("build", build, "name", "<the CEF pipeline name>",
                            "template_uuid", "<a forwarding template, or one existing CEF pipelines inherit from>"),
                    "or standalone='kafka' when there is no template; set its XSLT and "
                    + "standardKafkaProducer.kafkaConfig (update_pipeline for one left unset)");
            case "cef_stepped" -> call("step_sample", args("pipeline_uuid", cx, "stream_ids", events),
                    "stepping sends nothing; then draft_cef_mapping pipeline_uuid= reviews what it writes");
            case "cef_documented" -> call("write_documentation", args("build", build, "pipeline_uuid", cx,
                    "stream_ids", events, "markdown", DOCUMENTATION), "the CEF section is generated");
            case "promoted" -> call("promote_build", args("build", build, "destinations",
                    "<the folders sibling sources use, by type>"), "with the user's approval");
            default -> throw new IllegalArgumentException("No plan step '" + step + "'");
        };
    }

    /// Where the plan stands, in a line, for a task list the agent keeps (seen in testing: its checklist stayed on
    /// 'Process samples into Events' while the work went on): the steps done, and the one now.
    ///
    /// @param steps The plan's steps, each with its `state`.
    /// @param now   The step now.
    /// @return E.g. `step 3 of 4, validated; done: feed, processed`.
    public static String progress(final List<Map<String, Object>> steps, final String now) {
        final List<Map<String, Object>> counted = steps.stream()
                .filter(step -> !"not needed".equals(step.get("state")))
                .toList();
        final List<String> done = counted.stream()
                .filter(step -> "done".equals(step.get("state")))
                .map(step -> String.valueOf(step.get("step")))
                .toList();
        int at = done.size() + 1;
        for (int i = 0; i < counted.size(); i++) {
            if (Objects.equals(counted.get(i).get("step"), now)) {
                at = i + 1;
                break;
            }
        }
        return "step " + at + " of " + counted.size() + ", " + now + "; done: " + (done.isEmpty()
                ? "none yet"
                : String.join(", ", done));
    }

    /// @param profiled Sample text's profile ([SampleProfiling#profile]).
    /// @return Its format, and whether it needs a text converter: `format`, `namespace`, `root`,
    /// `suggested_parser` and `needs_text_converter`.
    public static Map<String, Object> textFormat(final Map<String, Object> profiled) {
        final String format = String.valueOf(profiled.get("format"));
        final Map<String, Object> result = new LinkedHashMap<>();
        if (JsonValues.truthy(profiled.get("malformed_lines")) && "json lines".equals(format)) {
            // Lines that aren't JSON: the Data Splitter route (one record a line, the JSON parsed in the XSLT), as the
            // JSONParser would stop at the first of them.
            result.put("format", "json lines (some not JSON)");
            result.put("suggested_parser", profiled.get("suggested_parser"));
            result.put("needs_text_converter", true);
            return result;
        }
        result.put("format", format);
        result.put("namespace", profiled.get("namespace"));
        result.put("root", profiled.get("root"));
        result.put("suggested_parser", profiled.get("suggested_parser"));
        result.put("needs_text_converter", TEXT_FORMATS.contains(format) || "xml fragments".equals(format));
        return result;
    }

    // The format of the newest raw sample stream.
    private Optional<Map<String, Object>> sampleFormat(final Map<String, List<Map<String, Object>>> streams) {
        final List<Map<String, Object>> metas = NullSafe.list(streams.get("Raw Events"));
        if (metas.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> newest = metas.getFirst();
        for (final Map<String, Object> meta : metas) {
            if (number(meta.get("createMs")) > number(newest.get("createMs"))) {
                newest = meta;
            }
        }
        final long streamId = number(newest.get("id"));
        final Map<String, Object> profiled;
        try {
            profiled = facts.profile(facts.streamHead(streamId));
        } catch (final ToolRefusal e) {
            // A sample that can't be profiled (or no profiling in this Stroom yet): the plan goes on without its
            // format, as it does before any sample is uploaded.
            LOGGER.debug(() -> LogUtil.message(
                    "The sample in stream {} wasn't profiled: {}",
                    streamId,
                    e.getMessage()));
            return Optional.empty();
        }
        final Map<String, Object> format = new LinkedHashMap<>();
        format.put("stream_id", streamId);
        format.put("feed", newest.get("feed"));
        format.putAll(textFormat(profiled));
        return Optional.of(format);
    }

    // An existing feed's format from its survey: its examples profiled, or, when there are none whole or they don't
    // profile, the format the survey split the streams by (read from whole text).
    private Map<String, Object> formatFromSurvey(final Map<String, Object> survey) {
        Map<String, Object> fmt = null;
        if (JsonValues.truthy(survey.get("sample"))) {
            try {
                fmt = textFormat(facts.profile(String.valueOf(survey.get("sample"))));
            } catch (final ToolRefusal e) {
                // Examples that don't profile together: the survey's own reading stands.
                LOGGER.debug(() -> LogUtil.message("A survey's examples didn't profile: {}", e.getMessage()));
            }
        }
        if (fmt == null) {
            final String format = NullSafe.nonBlankStringElse(NullSafe.get(survey.get("format"), String::valueOf),
                    "unknown text");
            fmt = new LinkedHashMap<>();
            fmt.put("format", format);
            fmt.put("suggested_parser", PARSER_FOR_FORMAT.getOrDefault(format, List.of("DSParser")).getFirst());
            fmt.put("needs_text_converter", TEXT_FORMATS.contains(format) || "xml fragments".equals(format));
        }
        final List<Long> streams = JsonValues.strings(survey.get("streams")).stream()
                .map(Long::parseLong)
                .toList();
        if ("xml".equals(survey.get("format")) && !"xml".equals(fmt.get("format")) && !streams.isEmpty()) {
            // The examples are one document's records without its root: joined, they read as XML fragments (seen in
            // production: a <tweets> feed, and the plan said XMLFragmentParser). A surveyed stream's head is the
            // document.
            try {
                final Map<String, Object> head = textFormat(facts.profile(facts.streamHead(streams.getLast())));
                if ("xml".equals(head.get("format"))) {
                    fmt = head;
                }
            } catch (final RuntimeException e) {
                // That stream gone under retention: the examples' reading stands.
                LOGGER.debug(() -> LogUtil.message("A surveyed stream's head couldn't be read: {}", e.getMessage()));
            }
        }
        return fmt;
    }

    private List<Map<String, Object>> xslts(final List<Map<String, Object>> docs) {
        final List<Map<String, Object>> xslts = new ArrayList<>();
        for (final Map<String, Object> doc : docs) {
            final Optional<KeptMapping.Kept> kept = facts.xsltMapping(uuid(doc));
            final Map<String, Object> xslt = new LinkedHashMap<>();
            xslt.put("name", doc.get("name"));
            xslt.put("uuid", doc.get("uuid"));
            xslt.put("mapping", kept.map(KeptMapping.Kept::kind).orElse(null));
            // A discovery plan: raw records indexed as they are, whatever parser reads them (XML too).
            xslt.put("discovery", kept.filter(found -> "index".equals(found.kind()))
                    .map(found -> JsonValues.truthy(found.payload().get("discovery")))
                    .orElse(false));
            xslts.add(xslt);
        }
        return xslts;
    }

    private List<Map<String, Object>> pipelines(final List<Map<String, Object>> docs) {
        final List<Map<String, Object>> pipelines = new ArrayList<>();
        for (final Map<String, Object> doc : docs) {
            final Map<String, Object> pipeline = new LinkedHashMap<>(doc);
            try {
                final PipelineShapes.Shape shape = facts.pipelineShape(uuid(doc));
                final List<String> missing = facts.openSlots(uuid(doc));
                pipeline.put("stage", shape.stage());
                pipeline.put("parser", shape.parser());
                pipeline.put("stepped", facts.steppedClean(doc));
                pipeline.put("missing", missing);
            } catch (final PipelineUnreadableException e) {
                // A pipeline Stroom can't load (a working copy of a production one with a property its element type
                // lost, or one whose template can't be loaded): listed as unreadable with why, the build's status
                // still given.
                pipeline.put("stage", "unreadable");
                pipeline.put("parser", null);
                pipeline.put("stepped", false);
                pipeline.put("missing", List.of("Stroom can't load it: " + e.getMessage()));
            }
            pipelines.add(pipeline);
        }
        return pipelines;
    }

    private Map<String, Object> existingDataStart(final List<String> sources, final String named) {
        final Map<String, Object> call = new LinkedHashMap<>();
        call.put("tool", "stream_meta");
        call.put("arguments", args("feeds", List.of(sources.isEmpty()
                ? "<the feed>"
                : sources.getFirst())));
        String then = "this build works from data already in Stroom: create no feed and upload nothing. Then, for "
                      + "what the user asked: an events pipeline for the feed, survey_feed build=... (its kinds of "
                      + "event); a production pipeline's new version or working copy, copy_pipeline; indexing its "
                      + "Events, get_field_conventions and the index; sending its Events on as CEF, "
                      + "draft_cef_mapping";
        final List<Map<String, Object>> current = facts.translating(sources);
        if (!current.isEmpty()) {
            // Seen in testing: asked to onboard a feed "using the current pipeline as a reference", the agent
            // surveyed and drafted anew, lost the current XSLT's lookups, and had a 50 KB mapping to send in one
            // call.
            final String shown = current.stream()
                    .map(p -> "'" + p.get("name") + "' (" + p.get("uuid") + ")")
                    .collect(Collectors.joining(", "));
            then = sources.getFirst() + " is translated now by " + shown + ". For its next version (V1.2 to V1.3, "
                   + "say), or a change to it: copy_pipeline source_uuid=<it> rename={'V1.2': 'V1.3'} "
                   + "(working_copy=true for a change in place), then build_translation_xslt uuid=<the copy's XSLT> "
                   + "changes= only what changes (rebuild_mapping uuid=<it> first, if its XSLT keeps no mapping): "
                   + "what it does, its lookups and all, comes with the copy. survey_feed and "
                   + "draft_translation_mapping are for an events pipeline written anew, when the user wants that. "
                   + Character.toUpperCase(then.charAt(0)) + then.substring(1);
        }
        final Map<String, Object> start = new LinkedHashMap<>();
        start.put("step", "existing data");
        start.put("do", "Work from " + named + ", already in Stroom");
        start.put("call", call);
        start.put("then", then);
        if (!current.isEmpty()) {
            start.put("translated_by", current);
        }
        return start;
    }

    private static List<Map<String, Object>> steps(final Map<String, Boolean> done, final Set<String> skipped) {
        // Not needed when their state is null: a converter for a format that needs none, a template for Lucene.
        final Set<String> notNeeded = Set.of("converter", "index_template", "covered");
        final List<Map<String, Object>> steps = new ArrayList<>();
        for (final Map<String, Object> item : checklist()) {
            final String step = String.valueOf(item.get("step"));
            final Boolean state = done.get(step);
            final Map<String, Object> shown = new LinkedHashMap<>(item);
            shown.put("state", skipped.contains(step)
                    ? "not needed"
                    : Boolean.TRUE.equals(state)
                            ? "done"
                            : state == null && notNeeded.contains(step)
                                    ? "not needed"
                                    : state == null
                                            ? "not recorded"
                                            : "to do");
            steps.add(shown);
        }
        return steps;
    }

    private static Set<String> skipped(final boolean discovery,
                                       final boolean surveyKnown,
                                       final boolean existing,
                                       final boolean copiesWithoutOwnFeed,
                                       final boolean stage1,
                                       final boolean stage2,
                                       final boolean cefBuild) {
        // A discovery build indexes its raw streams: no events pipeline at all (a converter only for delimited text).
        final Set<String> skipped = new HashSet<>(discovery
                ? Set.of("translation", "pipeline", "stepped", "processed", "validated", "documented")
                : Set.of());
        if (!surveyKnown) {
            skipped.add("covered");
        }
        if (existing) {
            // No feed to create and nothing to upload: the feed's streams are stepped where they are. An existing
            // feed's are processed where they are too, their Events written to a test feed the server makes in the
            // build (asked for by the user). A copy of a production pipeline is proven by stepping, unless the build
            // processes into a test feed of its own.
            skipped.addAll(Set.of("feed", "samples"));
            if (copiesWithoutOwnFeed) {
                skipped.addAll(Set.of("processed", "validated"));
            }
        }
        if (!stage1) {
            skipped.addAll(stepsOf(STAGE_EVENTS));
        }
        if (!stage2) {
            skipped.addAll(stepsOf(STAGE_INDEXING));
        }
        if (!cefBuild) {
            skipped.addAll(stepsOf(STAGE_OUTPUT));
        }
        return skipped;
    }

    private static Set<String> stepsOf(final String stage) {
        return checklist().stream()
                .filter(step -> stage.equals(step.get("stage")))
                .map(step -> String.valueOf(step.get("step")))
                .collect(Collectors.toSet());
    }

    private static void addSteps(final List<Map<String, Object>> steps,
                                 final String stage,
                                 final List<List<String>> items) {
        for (final List<String> item : items) {
            final Map<String, Object> step = new LinkedHashMap<>();
            step.put("step", item.get(0));
            step.put("what", item.get(1));
            step.put("tools", item.get(2));
            step.put("stage", stage);
            steps.add(step);
        }
    }

    private static List<Map<String, Object>> of(final Map<String, List<Map<String, Object>>> byType,
                                                final String type) {
        return NullSafe.list(byType.get(type));
    }

    private static List<Map<String, Object>> withStage(final List<Map<String, Object>> pipelines,
                                                       final Set<String> stages) {
        return pipelines.stream()
                .filter(p -> stages.contains(String.valueOf(p.get("stage"))))
                .toList();
    }

    private static boolean hasMapping(final List<Map<String, Object>> xslts, final String kind) {
        return xslts.stream().anyMatch(x -> kind.equals(x.get("mapping")));
    }

    private static boolean hasMissing(final Map<String, Object> pipeline) {
        return JsonValues.truthy(pipeline.get("missing"));
    }

    private static boolean allNamed(final List<Map<String, Object>> pipelines, final Set<String> documented) {
        return pipelines.stream().allMatch(p -> documented.contains(String.valueOf(p.get("name"))));
    }

    private static List<String> names(final List<Map<String, Object>> docs) {
        return docs.stream().map(doc -> String.valueOf(doc.get("name"))).toList();
    }

    private static List<Map<String, Object>> first(final List<Map<String, Object>> all,
                                                   final List<Map<String, Object>> first) {
        final List<Map<String, Object>> ordered = new ArrayList<>(first);
        all.stream().filter(p -> !first.contains(p)).forEach(ordered::add);
        return ordered;
    }

    private static Map<String, Object> shown(final Map<String, Object> pipeline) {
        final Map<String, Object> shown = new LinkedHashMap<>();
        for (final String key : List.of("name", "uuid", "stage", "parser", "stepped", "missing")) {
            shown.put(key, pipeline.get(key));
        }
        return shown;
    }

    private static String uuid(final Map<String, Object> doc) {
        return String.valueOf(doc.get("uuid"));
    }

    private static NextCall call(final String tool, final Map<String, Object> arguments, final String then) {
        final Map<String, Object> call = new LinkedHashMap<>();
        call.put("tool", tool);
        call.put("arguments", arguments);
        return new NextCall(call, then);
    }

    // The stream_meta call that finds a build's recent streams (of a type, or of any when null), as a
    // placeholder for the stream ids it gives, e.g. `stream_meta feeds=['ACME'] type='Events'`.
    private static String streamMetaCall(final List<String> sources, final String type) {
        final String feeds = sources.isEmpty()
                ? "'<the feed>'"
                : sources.stream().map(source -> "'" + source + "'").collect(Collectors.joining(", "));
        return "stream_meta feeds=[" + feeds + "]" + (type == null
                ? ""
                : " type='" + type + "'");
    }

    // Arguments in the order given, as name, value, name, value, ...
    private static Map<String, Object> args(final Object... namesAndValues) {
        final Map<String, Object> arguments = new LinkedHashMap<>();
        for (int i = 0; i + 1 < namesAndValues.length; i += 2) {
            arguments.put(String.valueOf(namesAndValues[i]), namesAndValues[i + 1]);
        }
        return arguments;
    }

    // Python's `value or other`: an empty (or absent) list, string or map gives other.
    private static Object orElse(final Object value, final Object other) {
        return JsonValues.truthy(value)
                ? value
                : other;
    }

    // A value as Python writes it into text: a list as [1, 2], anything else as it is.
    private static String pythonText(final Object value) {
        return value instanceof final Collection<?> collection
                ? collection.stream().map(String::valueOf).collect(Collectors.joining(", ", "[", "]"))
                : String.valueOf(value);
    }

    private static long number(final Object value) {
        if (value instanceof final Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (final NumberFormatException e) {
            return 0;
        }
    }

    private static Map<String, List<String>> parsers() {
        final Map<String, List<String>> map = new LinkedHashMap<>();
        final List<String> json = List.of("JSONParser", "CombinedParser");
        final List<String> text = List.of("DSParser", "CombinedParser");
        map.put("json array", json);
        map.put("json lines", json);
        map.put("xml", List.of("XMLParser", "CombinedParser"));
        map.put("xml fragments", List.of("XMLFragmentParser", "CombinedParser"));
        for (final String format : List.of("delimited", "syslog rfc5424", "syslog rfc3164", "key=value",
                "unknown text", "cef", "multi-line text", "json lines (some not JSON)", "cri")) {
            map.put(format, text);
        }
        map.put("json object", json);
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, String> stepOf() {
        final Map<String, String> map = new LinkedHashMap<>();
        map.put("start_onboarding", "start");
        map.put("build_status", "any step");
        map.put("start_build", "start");
        map.put("create_feed", "feed");
        map.put("upload_sample", "samples");
        map.put("profile_sample", "start");
        map.put("build_data_splitter", "converter");
        map.put("save_text_converter", "converter");
        map.put("draft_translation_mapping", "translation");
        map.put("build_translation_xslt", "translation");
        map.put("save_xslt", "translation");
        map.put("find_pipeline_templates", "pipeline");
        map.put("describe_template", "pipeline");
        map.put("create_pipeline", "pipeline");
        map.put("step_sample", "stepped");
        map.put("step_pipeline", "stepped");
        map.put("step_records", "stepped");
        map.put("create_processor_filter", "processed");
        map.put("wait_for_processing", "processed");
        map.put("check_events", "validated");
        map.put("write_documentation", "documented");
        map.put("get_field_conventions", "index");
        map.put("draft_index_mapping", "index");
        map.put("create_index_doc", "index");
        map.put("create_indexing_pipeline", "indexing_pipeline");
        map.put("propose_index_template", "index_template");
        map.put("check_index_template", "index_template");
        map.put("rename_index", "index_template");
        map.put("verify_index", "indexed");
        map.put("promote_build", "promoted");
        return Collections.unmodifiableMap(map);
    }


    // --------------------------------------------------------------------------------


    /// A step's first call, and what follows it.
    ///
    /// @param call The call: `tool` and `arguments`.
    /// @param then What follows it.
    public record NextCall(Map<String, Object> call, String then) {

    }

    /// What a build gives a step's call.
    ///
    /// @param build       The build.
    /// @param feeds       Its feeds' names.
    /// @param raw         Its raw sample streams' ids, or a placeholder.
    /// @param events      Its Events streams' ids, or a placeholder.
    /// @param translation Its events pipeline's UUID, or null.
    /// @param indexing    Its indexing pipeline's UUID, or null.
    /// @param processing  How processing stands for the step (e.g. `running`, `disabled`, `finished`), or null.
    /// @param index       Its index doc (`uuid`, `backend`, `columns`), or null.
    /// @param sources     The existing feeds it works from.
    /// @param discovery   True for a discovery build.
    /// @param locations   The survey's locations, or a placeholder.
    /// @param cef         Its CEF pipeline's UUID, or null.
    /// @param editedXslt  Its translation XSLT changed by hand, or null.
    public record CallArgs(String build,
                           List<String> feeds,
                           Object raw,
                           Object events,
                           String translation,
                           String indexing,
                           String processing,
                           Map<String, Object> index,
                           List<String> sources,
                           boolean discovery,
                           Object locations,
                           String cef,
                           String editedXslt) {

        /// @param build       The build.
        /// @param feeds       Its feeds' names.
        /// @param raw         Its raw sample streams' ids, or a placeholder.
        /// @param events      Its Events streams' ids, or a placeholder.
        /// @param translation Its events pipeline's UUID, or null.
        /// @param indexing    Its indexing pipeline's UUID, or null.
        /// @return What the build gives, with nothing else known.
        public static CallArgs of(final String build,
                                  final List<String> feeds,
                                  final Object raw,
                                  final Object events,
                                  final String translation,
                                  final String indexing) {
            return new CallArgs(build, feeds, raw, events, translation, indexing, null, null, List.of(), false,
                    null, null, null);
        }
    }
}
