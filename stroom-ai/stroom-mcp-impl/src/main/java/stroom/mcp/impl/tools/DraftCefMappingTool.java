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

import stroom.ai.impl.AiChatRunManager;
import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.AgentInstructions;
import stroom.ai.impl.tools.CallProgress;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.InstructionRules;
import stroom.ai.impl.tools.InstructionRules.RequiredImport;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.SteppingWalks;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.ToolArgs;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.cef.CefDocs;
import stroom.ai.impl.tools.cef.CefDraft;
import stroom.ai.impl.tools.cef.CefEvents;
import stroom.ai.impl.tools.cef.CefField;
import stroom.ai.impl.tools.cef.CefInstructions;
import stroom.ai.impl.tools.cef.CefKeys;
import stroom.ai.impl.tools.cef.CefLines;
import stroom.ai.impl.tools.cef.CefOverride;
import stroom.ai.impl.tools.cef.CefPlan;
import stroom.ai.impl.tools.cef.CefSample;
import stroom.ai.impl.tools.cef.CefValue;
import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.MappingValidationException;
import stroom.ai.impl.tools.generation.XmlElement;
import stroom.ai.impl.tools.generation.XmlParser;
import stroom.ai.impl.tools.generation.XsltStyle;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.ai.impl.tools.validation.EventStreams;
import stroom.ai.impl.tools.validation.XsltCompileCheck;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.pipeline.shared.XsltDoc;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// `draft_cef_mapping` - CEF (ArcSight Common Event Format) output: drafts which Event value goes to which CEF key, one
/// CEF line per Event, from sample Events; saves its XSLT with the plan in a build; or reviews an existing CEF pipeline
/// by stepping it over Events streams. Built on the CEF core (`stroom.ai.impl.tools.cef`).
///
/// The user is asked first (a form where the session can show one, else the agent asks) whether keys outside ArcSight's
/// CEF dictionary may be sent, unless the standing instructions say. A save goes through [XsltSaves], the translation
/// tools' save path: it asks the user nothing itself, except about a hand edit its change would undo, so the call as a
/// whole is approved over `/mcp`. Streams are read and stepped as the user; it takes a run slot, says where it is,
/// stops at once when its thread is interrupted, and steps within the session's time limit for a long call.
class DraftCefMappingTool extends AbstractBuiltInTool {

    static final String NAME = "draft_cef_mapping";
    static final String STREAM_IDS_ARG = "stream_ids";
    static final String CUSTOM_KEYS_ARG = "custom_keys";
    static final String OVERRIDES_ARG = "overrides";
    static final String HEADER_ARG = "header";
    static final String OUTPUT_ARG = "output";
    static final String TOPIC_ARG = "topic";
    static final String KAFKA_KEY_ARG = "kafka_key";
    static final String PIPELINE_UUID_ARG = "pipeline_uuid";
    static final String BUILD_ARG = "build";
    static final String NAME_ARG = "name";
    static final String UUID_ARG = "uuid";
    static final String CHANGE_ARG = "change";
    static final String FEEDS_ARG = "feeds";
    static final String FOLDERS_ARG = "folders";
    static final String STYLE_ARG = "style";
    static final String DISCARD_HAND_EDIT_ARG = "discard_hand_edit";
    static final String HAND_EDIT_CHOICES_ARG = "hand_edit_choices";

    static final String CUSTOM_QUESTION = "May the CEF output use keys outside ArcSight's CEF dictionary (e.g. "
                                          + "myCustomField1)? ArcSight usually doesn't index those. Its built-in "
                                          + "custom slots (cs1-cs6, cn1-cn3 and the rest, each with a label) are "
                                          + "used either way; without keys outside the dictionary, values no key or "
                                          + "slot takes are not sent, and the documentation lists them.";
    static final List<String> CUSTOM_OPTIONS = List.of("No: only keys in ArcSight's CEF dictionary",
            "Yes: keys outside the dictionary may be added");

    private static final Pattern KAFKA_TOPIC = Pattern.compile("<(?:\\w+:)?kafkaRecord[^>]*topic=\"([^\"]*)\"");

    private final AgentInstructions agentInstructions;
    private final Consent consent;
    private final EventStreams eventStreams;
    private final PipelineStepping stepping;
    private final KeptMappings keptMappings;
    private final DocumentAccess documentAccess;
    private final CefSources sources;
    private final XsltSaves saves;
    private final BuildPlan plan;
    private final XsltCompileCheck compileCheck;
    private final CefChoices choices;

    @Inject
    DraftCefMappingTool(final AgentInstructions agentInstructions,
                        final Consent consent,
                        final EventStreams eventStreams,
                        final PipelineStepping stepping,
                        final KeptMappings keptMappings,
                        final DocumentAccess documentAccess,
                        final CefSources sources,
                        final XsltSaves saves,
                        final BuildPlan plan,
                        final XsltCompileCheck compileCheck,
                        final CefChoices choices) {
        this.agentInstructions = agentInstructions;
        this.consent = consent;
        this.eventStreams = eventStreams;
        this.stepping = stepping;
        this.keptMappings = keptMappings;
        this.documentAccess = documentAccess;
        this.sources = sources;
        this.saves = saves;
        this.plan = plan;
        this.compileCheck = compileCheck;
        this.choices = choices;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "CEF (ArcSight Common Event Format) output: draft which Event value goes to which CEF "
                             + "key, as flattened text, one CEF line per Event. First asks the user whether keys "
                             + "outside ArcSight's CEF dictionary are allowed (unless the standing instructions "
                             + "say). Infers a mapping from the sample Events: the header (vendor, product, "
                             + "version, class id, name, severity), fields every event shares, and per kind of "
                             + "event (its action element) the rest: ArcSight's standard keys first, then its "
                             + "custom slots (cs1-cs6, cn1-cn3, ...) with labels, and lists what no key takes (not "
                             + "sent). Mappings in the standing instructions and in overrides take precedence. "
                             + "Returns the plan, its problems, the documentation's tables, and where the pipeline "
                             + "should come from: a template the standing instructions name, else the "
                             + "environment's forwarding templates or the template its CEF pipelines inherit from. "
                             + "With build and name (or uuid) it saves the XSLT with the plan: a Kafka record per "
                             + "Event (kafka-records:1, one Event a record) or lines of text. With pipeline_uuid it "
                             + "reviews an existing CEF pipeline instead.")
                .parameters(ObjectSchema.builder()
                        .addProperty(STREAM_IDS_ARG, stringArraySchema("Events streams (the events pipeline's "
                                                                       + "output): the sample the mapping is "
                                                                       + "drafted from, or that an existing CEF "
                                                                       + "pipeline is stepped over to review it."))
                        .addBooleanProperty(CUSTOM_KEYS_ARG, "Whether keys outside ArcSight's CEF dictionary may be "
                                                             + "sent: the user's answer. Left out, the standing "
                                                             + "instructions decide, or the user is asked.")
                        .addProperty(OVERRIDES_ARG, stringArraySchema("Mappings the user gives: {path, key (short "
                                                                      + "or full name), label (for a custom slot), "
                                                                      + "event_type (one kind of event)} or {path, "
                                                                      + "drop: true}. Mappings in the standing "
                                                                      + "instructions ('<Event path> -> <CEF key>') "
                                                                      + "are applied first."))
                        .addProperty(HEADER_ARG, ObjectSchema.builder()
                                .description("Header fields to set: vendor, product, version, signature (Device "
                                             + "Event Class ID), name, severity; each a constant, or {source: "
                                             + "<XPath on the Event>, map: {value: header value}, default}.")
                                .build())
                        .addEnumProperty(OUTPUT_ARG, CefPlan.OUTPUTS, "kafka (a Kafka record per Event, value the "
                                                                      + "CEF line; StandardKafkaProducer) or text "
                                                                      + "(lines; TextWriter). Left out: the pipeline "
                                                                      + "template's kind, else kafka.")
                        .addStringProperty(TOPIC_ARG, "kafka: the topic ArcSight reads (the user, or the standing "
                                                      + "instructions, say which).")
                        .addStringProperty(KAFKA_KEY_ARG, "kafka: an XPath on the Event for the record key.")
                        .addStringProperty(PIPELINE_UUID_ARG, "Review an existing CEF pipeline instead of drafting: "
                                                              + "its output is stepped over stream_ids, parsed and "
                                                              + "checked against the CEF dictionary, with what each "
                                                              + "Event sends and doesn't; its plan (kept with an "
                                                              + "XSLT the tools saved) or the mapping its lines "
                                                              + "imply comes back to change.")
                        .addStringProperty(BUILD_ARG, "With name: save the XSLT in this build with its plan.")
                        .addStringProperty(NAME_ARG, "With build: the new XSLT's name.")
                        .addStringProperty(UUID_ARG, "An XSLT the tools saved with a CEF plan: start from that plan "
                                                     + "(with the overrides applied) and save it again in place.")
                        .addStringProperty(CHANGE_ARG, "What this change is and why, in a line: recorded in the "
                                                       + "XSLT's version history (and the documentation's version "
                                                       + "control when it is written), e.g. 'Rule for Secret "
                                                       + "Checkout events, seen once the whole feed was processed'.")
                        .addProperty(FEEDS_ARG, stringArraySchema("The Events feed: its folder's standing "
                                                                  + "instructions apply."))
                        .addProperty(FOLDERS_ARG, stringArraySchema("Folders the pipeline will live in."))
                        .addProperty(STYLE_ARG, ObjectSchema.builder()
                                .description("How the CEF XSLT is written, as an Events translation's is (naming, "
                                             + "variables, layout): set it from an XSLT style section in the "
                                             + "standing instructions (AGENTS docs). Kept with the plan.")
                                .build())
                        .addBooleanProperty(DISCARD_HAND_EDIT_ARG, "Save over an edit made by hand in Stroom's "
                                                                   + "editor since the tools saved the XSLT, though "
                                                                   + "the new code undoes it. Only when the user has "
                                                                   + "said to drop their edit: a save that would "
                                                                   + "undo one is refused, saying what it changed, "
                                                                   + "so it can be carried into the plan (overrides) "
                                                                   + "instead.")
                        .addProperty(HAND_EDIT_CHOICES_ARG, ObjectSchema.builder()
                                .description("Only from a needs_guidance reply about a hand edit, with the user's "
                                             + "answers: {'*': 'overwrite'} to overwrite the XSLT with the change "
                                             + "(every hand edit dropped), or for each field it names 'keep' (their "
                                             + "hand edit) or 'overwrite' (with the proposed field). Never chosen "
                                             + "for them.")
                                .build())
                        .required(STREAM_IDS_ARG)
                        .build())
                .build();
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public boolean isReadOnly() {
        // With build and name, or uuid, it saves the XSLT.
        return false;
    }

    @Override
    public boolean changesOnlyItsBuild() {
        // Through WriteGuard, only what is in the build's folder.
        return true;
    }

    @Override
    public boolean isReadOnly(final ToolCall call) {
        // A draft or a review that saves nothing only reads.
        return !savesXslt(call);
    }

    @Override
    public boolean hasReadOnlyCalls() {
        return true;
    }

    @Override
    public boolean usesRunSlot() {
        // It reads up to 500 Events, or steps a pipeline over them.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .requires(AppPermission.VIEW_DATA_PERMISSION)
                .unlockedBy(AppPermission.STEPPING_PERMISSION)
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.EDIT)
                .note("The Events streams are read (or, with pipeline_uuid, stepped: the Stepping permission) as "
                      + "the user; a save creates or changes the XSLT in the build's folder as the user.")
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            return JsonResults.of(draft(call, context, request(call)));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        } catch (final StreamReader.StreamReadException | PipelineUnreadableException e) {
            return JsonResults.refused(e.getMessage());
        } catch (final ToolTimeoutException e) {
            return JsonResults.refused(PipelineStepping.CANCELLED);
        } catch (final AiChatRunManager.WaitingForUserException e) {
            // A form waits for the user: the call is made again with the answer.
            throw e;
        } catch (final RuntimeException e) {
            return JsonResults.refused(StreamSummaries.reason(e));
        }
    }

    /// The draft, review or save.
    ///
    /// @param call    The call, for the user's forms.
    /// @param context The call's context.
    /// @param request What is asked.
    /// @return `needs_guidance` with the question to ask the user; or the plan (`custom_keys`, `applied`, `plan`,
    /// `problems`, `not_sent`, `documentation`, `kafka_configs`, ...), saved or reviewed, and a `hint`.
    /// @throws ToolRefusal if there is nothing to draft from, or the save is refused.
    Map<String, Object> draft(final ToolCall call, final ToolCallContext context, final Request request) {
        final Standing standing = instructions(context, request.folders(), request.feeds());
        final Map<String, Object> said = CefInstructions.fromInstructions(standing.texts());
        final Optional<Boolean> asked = customKeys(call, context, request, said);
        if (asked.isEmpty()) {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", "needs_guidance");
            out.put("question", CUSTOM_QUESTION);
            out.put("options", CUSTOM_OPTIONS);
            out.put("hint", "Ask the user exactly this, offering these options, then call again with "
                            + "custom_keys=false (the first) or true (the second). Don't choose for them.");
            return out;
        }
        final boolean allowed = asked.get();
        final int cap = PipelineStepping.MAX_SAMPLE_RECORDS;
        final List<CefOverride> applied = new ArrayList<>(saidOverrides(said));
        applied.addAll(request.overrides());
        final Map<String, Object> result = new LinkedHashMap<>();
        CefPlan drafted;
        final List<String> done;
        final Map<CefDocs.KindKey, String> examples;
        final Optional<KeptMappings.KeptXslt> reviewedKept;
        // The sample Events, for what the documentation says of them.
        final List<XmlElement> sampled;
        if (request.pipelineUuid() != null) {
            reviewedKept = keptMappings.keptXslt(request.pipelineUuid());
            final Stepped stepped = stepped(context, request.pipelineUuid(), request.streamIds(),
                    reviewedKept.map(KeptMappings.KeptXslt::element).orElse(null), cap);
            if (stepped.lines().isEmpty()) {
                throw new ToolRefusal("Stepping the pipeline over streams " + request.streamIds() + " gave no CEF "
                                      + "lines at its element '" + stepped.element() + "': check stream_ids are "
                                      + "Events streams it reads, and step_sample it");
            }
            final List<XmlElement> events = stepped.events().stream().filter(e -> e != null).toList();
            sampled = events;
            final Map<String, Object> reviewed = CefLines.review(stepped.lines(), events, allowed);
            result.put("review", reviewed);
            if (stepped.stopped()) {
                result.put("stopped_early", true);
            }
            if (reviewedKept.isPresent() && XsltSaves.CEF.equals(reviewedKept.get().kept().kind())) {
                drafted = CefPlan.fromJson(reviewedKept.get().kept().payload());
                result.put("plan_from", "the plan kept with XSLT '" + reviewedKept.get().xslt().getName() + "'");
            } else {
                drafted = implied(stepped, reviewed, allowed, request.header());
                result.put("plan_from", "the mapping its lines imply (its XSLT keeps no plan; header fields as the "
                                        + "first line had them): check it before saving a new XSLT from it");
            }
            final Applied overridden = apply(drafted, applied);
            drafted = overridden.plan();
            done = overridden.done();
            examples = CefLines.examplesFrom(drafted, stepped.lines(), events);
        } else {
            reviewedKept = Optional.empty();
            CallProgress.of(context).say("Reading the Events of " + request.streamIds().size() + " stream"
                                         + (request.streamIds().size() == 1
                    ? ""
                    : "s"));
            final List<XmlElement> events = CefEvents.eventsOf(eventStreams.read(request.streamIds(), cap)
                    .eventsXml());
            sampled = events;
            if (request.uuid() != null) {
                final Applied overridden = apply(keptPlan(request.uuid()), applied);
                drafted = overridden.plan();
                done = overridden.done();
                for (final Map.Entry<String, CefValue> head : request.header().entrySet()) {
                    drafted = drafted.withHeader(head.getKey(), head.getValue());
                }
            } else {
                String choice = request.output();
                if (choice == null) {
                    final Map<String, Object> templates = sources.templates(said, request.folders());
                    final Set<Object> kinds = new LinkedHashSet<>();
                    NullSafe.list((List<?>) templates.get("forwarding_templates")).forEach(t ->
                            kinds.add(((Map<?, ?>) t).get("backend")));
                    choice = kinds.equals(Set.of(CefPlan.TEXT))
                            ? CefPlan.TEXT
                            : CefPlan.KAFKA;
                    result.put("templates", templates);
                }
                final CefDraft.Drafted made = CefDraft.draft(events, allowed, applied, request.header(), choice,
                        orElse(request.topic(), (String) said.get(CefInstructions.TOPIC)),
                        request.kafkaKey());
                drafted = made.plan();
                done = applied.stream().map(DraftCefMappingTool::doneLine).toList();
                if (!made.notes().isEmpty()) {
                    result.put("notes", made.notes());
                }
            }
            if (request.topic() != null) {
                drafted = drafted.withTopic(request.topic());
            }
            if (request.kafkaKey() != null) {
                drafted = drafted.withKafkaKey(request.kafkaKey());
            }
            if (request.output() != null) {
                drafted = drafted.withOutput(request.output());
            }
            examples = new LinkedHashMap<>();
            if (!events.isEmpty()) {
                sampleCheck(drafted, events, allowed, cap, examples, result);
            }
        }
        if (!result.containsKey("templates") && request.pipelineUuid() == null) {
            result.put("templates", sources.templates(said, request.folders()));
        }
        if (request.style() != null) {
            drafted = drafted.withStyle(request.style());
        }
        final List<String> problems = drafted.problems();
        result.put("custom_keys", allowed);
        if (!done.isEmpty()) {
            result.put("applied", done);
        }
        // The not-sent list once, by reason (seen in testing: 125 entries, each saying the same why, given in the plan
        // and again beside it, which the agent then read back from a result over 100 KB).
        final Map<String, Object> shown = drafted.toJson(DumpMode.COMPACT);
        shown.remove("not_sent");
        result.put("plan", shown);
        result.put("problems", problems);
        result.put("not_sent", groupedNotSent(drafted.notSent()));
        result.put("documentation", "## Field mapping\n\n" + drafted.markdown(examples, sampled, null, null));
        fromStandingInstructions(said).ifPresent(from -> result.put("from_standing_instructions", from));
        if (!standing.instructions().isEmpty()) {
            result.put("standing_instructions", standing.instructions());
        }
        result.put("kafka_configs", sources.kafkaConfigs());
        if ((request.build() != null && request.name() != null) || request.uuid() != null) {
            return save(call, context, request, drafted, done, problems, reviewedKept, result);
        }
        result.put("hint", "Show the user the plan's tables (documentation; its 'Fields not included' tables are "
                           + "the not-sent list); with their "
                           + "changes as overrides, call again with build and name to save the XSLT. "
                           + next(drafted, null, result));
        return result;
    }

    // Saves the XSLT with its plan, unless the plan has problems.
    private Map<String, Object> save(final ToolCall call,
                                     final ToolCallContext context,
                                     final Request request,
                                     final CefPlan drafted,
                                     final List<String> done,
                                     final List<String> problems,
                                     final Optional<KeptMappings.KeptXslt> reviewedKept,
                                     final Map<String, Object> result) {
        if (!problems.isEmpty()) {
            result.put("saved", null);
            result.put("hint", "Not saved: fix the problems (overrides, header, topic) and call again.");
            return result;
        }
        CefPlan toSave = drafted;
        final String target = request.uuid() != null
                ? request.uuid()
                : reviewedKept.filter(kept -> XsltSaves.CEF.equals(kept.kept().kind()))
                        .map(kept -> kept.xslt().getUuid())
                        .orElse(null);
        // The imports standing instructions require of CEF XSLTs, added as the plan writes it.
        final String where = request.build() != null
                ? request.build()
                : plan.buildOf(new DocRef(XsltDoc.TYPE, target)).orElse(null);
        final List<String> required = saves.requiredFor(context.session(), InstructionRules.CEF, where, List.of())
                .stream()
                .map(RequiredImport::href)
                .filter(href -> !drafted.imports().contains(href))
                .distinct()
                .toList();
        if (!required.isEmpty()) {
            final List<String> imports = new ArrayList<>(toSave.imports());
            imports.addAll(required);
            toSave = toSave.withImports(imports);
            result.put("required_imports", required);
        }
        final String change = request.change() != null
                ? request.change()
                : target != null && !done.isEmpty()
                        ? "CEF plan changes: " + String.join("; ", done)
                        : target != null
                                ? "Saved again from its CEF plan"
                                : "Created from its CEF plan";
        // Compiled as Stroom compiles it, so one that wouldn't run isn't saved (seen in testing: a record key's
        // undeclared prefix found only by Stroom's log when the pipeline ran).
        final List<String> errors = compileCheck.compile(toSave.xslt()).errors();
        if (!errors.isEmpty()) {
            result.put("problems", errors.stream().map(error -> "the XSLT doesn't compile: " + error).toList());
            result.put("saved", null);
            result.put("hint", "Not saved: the XSLT the plan writes doesn't compile. Fix what it reads (kafka_key, "
                               + "an override's path) and call again.");
            return result;
        }
        final TranslationDocs.Source source = TranslationDocs.Source.of(toSave);
        final Map<String, Object> saved = target != null
                ? saves.update(call, context, target, toSave.xslt(), null, source, change, request.discardHandEdit(),
                request.handEditChoices())
                : saves.create(call, context, request.build(), request.name(), toSave.xslt(), source, change);
        if (!(saved.get("uuid") instanceof final String uuid)) {
            // Asked about fields edited by hand.
            final Map<String, Object> out = new LinkedHashMap<>(result);
            out.putAll(saved);
            return out;
        }
        final Map<String, Object> shown = new LinkedHashMap<>();
        for (final String key : List.of("type", "uuid", "name", "version")) {
            shown.put(key, saved.get(key));
        }
        result.put("saved", shown);
        if (saved.get("next") != null) {
            result.put("next", saved.get("next"));
        }
        result.put("hint", next(toSave, uuid, result));
        return result;
    }

    // The plan kept with an XSLT the tools saved.
    private CefPlan keptPlan(final String uuid) {
        final XsltDoc doc = documentAccess.read(new DocRef(XsltDoc.TYPE, uuid), XsltDoc.class)
                .orElseThrow(() -> new ToolRefusal("XSLT " + uuid + " can't be read: it doesn't exist, or you "
                                                   + "don't have permission to view it"));
        final Optional<KeptMapping.Kept> kept = KeptMapping.readMapping(doc.getDescription());
        if (kept.isEmpty()) {
            saves.lostMapping(new DocRef(XsltDoc.TYPE, uuid, doc.getName()), doc.getDescription())
                    .ifPresent(lost -> {
                        throw new ToolRefusal(lost + " For a CEF XSLT: draft_cef_mapping pipeline_uuid=<its "
                                              + "pipeline> reviews what it writes, and a draft without uuid saves "
                                              + "a new plan.");
                    });
        }
        if (kept.isEmpty() || !XsltSaves.CEF.equals(kept.get().kind())) {
            throw new ToolRefusal("XSLT " + uuid + " keeps no CEF plan: draft without uuid, or review its pipeline "
                                  + "(pipeline_uuid)");
        }
        return CefPlan.fromJson(kept.get().payload());
    }

    // The lines the plan writes for the sample, for the documentation's examples and a review of its own. The check
    // is advice; the plan stands on its own problems.
    private static void sampleCheck(final CefPlan drafted,
                                    final List<XmlElement> events,
                                    final boolean allowed,
                                    final int cap,
                                    final Map<CefDocs.KindKey, String> examples,
                                    final Map<String, Object> result) {
        try {
            final List<String> lines = CefSample.lines(drafted, events, cap);
            examples.putAll(CefLines.examplesFrom(drafted, lines, events));
            final Map<String, Object> checked = CefLines.review(lines, events, allowed);
            if (!NullSafe.list((List<?>) checked.get("problems")).isEmpty()) {
                result.put("sample_check", checked.get("problems"));
            }
            result.put("example_line", lines.isEmpty()
                    ? null
                    : lines.getFirst());
        } catch (final RuntimeException e) {
            result.put("sample_check_error", head(StreamSummaries.reason(e), 300));
        }
    }

    // The plan the lines a pipeline writes imply: the header as the first line has it.
    private static CefPlan implied(final Stepped stepped,
                                   final Map<String, Object> reviewed,
                                   final boolean allowed,
                                   final Map<String, CefValue> heads) {
        final List<String> sample = CefLines.parse(stepped.lines().getFirst()).header();
        final Map<String, CefValue> header = new LinkedHashMap<>();
        for (int i = 0; i < CefKeys.HEADER.size() && i + 1 < sample.size(); i++) {
            header.put(CefKeys.HEADER.get(i).name(), CefValue.constant(sample.get(i + 1)));
        }
        header.putAll(heads);
        final Map<String, List<CefField>> events = new LinkedHashMap<>();
        ((Map<?, ?>) reviewed.get("implied_mapping")).forEach((kind, maps) -> {
            final List<CefField> fields = new ArrayList<>();
            ((List<?>) maps).forEach(m -> {
                final Map<?, ?> map = (Map<?, ?>) m;
                fields.add(new CefField((String) map.get("path"), (String) map.get("key"), (String) map.get("label"),
                        null));
            });
            events.put(String.valueOf(kind), fields);
        });
        final List<Map<String, String>> notSent = new ArrayList<>();
        ((List<?>) reviewed.get("not_sent")).forEach(n -> {
            final Map<?, ?> map = (Map<?, ?>) n;
            final Map<String, String> entry = new LinkedHashMap<>();
            entry.put("path", (String) map.get("path"));
            entry.put("event_type", (String) map.get("event_type"));
            entry.put("why", "not in its lines");
            notSent.add(entry);
        });
        return new CefPlan(stepped.sent().get("output"), stepped.sent().get("topic"), null, allowed,
                header.get("vendor"), header.get("product"), header.get("version"), header.get("signature"),
                header.get("name"), header.get("severity"), List.of(), events, notSent, List.of(), null);
    }

    /// The CEF lines a pipeline writes from these Events streams, the Events they came from (in order, null where not
    /// known), the element read, and how it sends them (`output`: kafka or text, `topic`).
    ///
    /// @throws ToolRefusal if the user may not step, or the pipeline can't be read.
    Stepped stepped(final ToolCallContext context,
                    final String pipelineUuid,
                    final List<Long> streamIds,
                    final String element,
                    final int cap) {
        stepping.checkCanStep();
        final SteppedPipeline pipeline = stepping.load(pipelineUuid);
        final String at = element != null
                ? element
                : pipeline.defaultOutputs().getFirst();
        final List<String> lines = new ArrayList<>();
        final List<XmlElement> events = new ArrayList<>();
        final Map<String, String> sent = new LinkedHashMap<>();
        sent.put("output", CefPlan.TEXT);
        final PipelineStepping.Spread spread;
        try {
            // One Event a record: the records spread over the streams (step_spread).
            spread = PipelineStepping.withinTimeLimit(context, () -> stepping.stepSpread(context, pipeline,
                    streamIds, null, cap));
        } finally {
            stepping.audit(context, streamIds, "Stepped stream(s) through " + pipeline.name() + " with the " + NAME
                                               + " tool");
        }
        for (final PipelineStepping.Spread.Step step : spread.steps()) {
            final String output = SteppingWalks.output(step.result(), at);
            final List<String> made = CefLines.linesIn(output);
            if (output.contains("<kafkaRecord")) {
                final Matcher topic = KAFKA_TOPIC.matcher(output);
                sent.clear();
                sent.put("output", CefPlan.KAFKA);
                if (topic.find()) {
                    sent.put("topic", topic.group(1));
                }
            }
            final String input = SteppingWalks.input(step.result(), at);
            List<XmlElement> given;
            try {
                given = NullSafe.isEmptyString(input)
                        ? List.of()
                        : CefEvents.eventsOf(input);
            } catch (final XmlParser.XmlParseException e) {
                given = List.of();
            }
            lines.addAll(made);
            final List<XmlElement> taken = given.subList(0, Math.min(given.size(), made.size()));
            events.addAll(taken);
            for (int i = taken.size(); i < made.size(); i++) {
                events.add(null);
            }
        }
        return new Stepped(lines.subList(0, Math.min(cap, lines.size())),
                events.subList(0, Math.min(cap, events.size())), at, sent, spread.stopped());
    }

    /// Overrides on a plan that exists: a path moved to another key (or label), or left out.
    ///
    /// @param plan      The plan.
    /// @param overrides The overrides, in order.
    /// @return The plan with them, and what each did.
    @SuppressWarnings("unchecked")
    static Applied apply(final CefPlan plan, final List<CefOverride> overrides) {
        final Map<String, Object> data = mutable(plan.toJson(DumpMode.ALL));
        final List<Object> common = (List<Object>) data.get("common");
        final Map<String, Object> events = (Map<String, Object>) data.get("events");
        final List<Object> notSent = (List<Object>) data.get("not_sent");
        final List<String> done = new ArrayList<>();
        for (final CefOverride o : overrides) {
            final List<Map.Entry<String, List<Object>>> lists = new ArrayList<>();
            lists.add(Map.entry("common", common));
            events.forEach((kind, fields) -> {
                if (o.eventType() == null || o.eventType().equals(kind)) {
                    lists.add(Map.entry(kind, (List<Object>) fields));
                }
            });
            boolean hit = false;
            for (final Map.Entry<String, List<Object>> entry : lists) {
                final String kind = entry.getKey();
                final List<Object> fields = entry.getValue();
                for (final Object field : new ArrayList<>(fields)) {
                    final Map<String, Object> f = (Map<String, Object>) field;
                    final String path = (String) f.get("path");
                    if (!CefDraft.matches(o.path(), path)) {
                        continue;
                    }
                    hit = true;
                    if (o.drop()) {
                        fields.remove(f);
                        final Map<String, Object> left = new LinkedHashMap<>();
                        left.put("path", path);
                        left.put("event_type", kind);
                        left.put("why", "left out as instructed");
                        notSent.add(left);
                        done.add("[" + kind + "] " + path + " left out");
                    } else {
                        final String key = CefKeys.keyOf(orElse(o.key(), (String) f.get("key")));
                        f.put("key", key);
                        final boolean slot = CefKeys.SLOT_KEYS.contains(key);
                        final String label = NullSafe.isNonEmptyString(o.label())
                                ? o.label()
                                : slot && NullSafe.isNonEmptyString((String) f.get("label"))
                                        ? (String) f.get("label")
                                        : slot
                                                ? CefDraft.labelFor(path)
                                                : null;
                        f.put("label", label);
                        done.add("[" + kind + "] " + path + " -> " + key);
                    }
                }
            }
            if (!hit && NullSafe.isNonEmptyString(o.key())) {
                final List<Object> target = o.eventType() != null
                        ? (List<Object>) events.computeIfAbsent(o.eventType(), k -> new ArrayList<>())
                        : common;
                final Map<String, Object> added = new LinkedHashMap<>();
                added.put("path", o.path());
                added.put("key", CefKeys.keyOf(o.key()));
                added.put("label", o.label());
                target.add(added);
                notSent.removeIf(n -> CefDraft.matches(o.path(), (String) ((Map<String, Object>) n).get("path")));
                done.add("[" + NullSafe.nonBlankStringElse(o.eventType(), CefDraft.EVERY_EVENT) + "] " + o.path()
                         + " -> " + CefKeys.keyOf(o.key()) + " (added)");
            }
        }
        return new Applied(CefPlan.fromJson(data), done);
    }

    /// What to do next with the plan.
    ///
    /// @param drafted  The plan.
    /// @param xsltUuid The saved XSLT, or null.
    /// @param result   The result so far, with its `templates`.
    /// @return The next steps: the pipeline, stepping it, reviewing it and its documentation.
    static String next(final CefPlan drafted, final String xsltUuid, final Map<String, Object> result) {
        final Map<?, ?> templates = result.get("templates") instanceof final Map<?, ?> map
                ? map
                : Map.of();
        final String where = templates.get("from_instructions") != null
                ? "the template the standing instructions name"
                : JsonValues.truthy(templates.get("forwarding_templates"))
                  || JsonValues.truthy(templates.get("existing_cef_pipelines"))
                        ? "a forwarding template (find_pipeline_templates stage=forwarding), or the one existing CEF "
                          + "pipelines inherit from"
                        : "no forwarding template exists: create_pipeline standalone='kafka' (the user confirms the "
                          + "chain)";
        final String xslt = xsltUuid != null
                ? "'" + xsltUuid + "'"
                : "the saved XSLT";
        return "Pipeline: create_pipeline from " + where + ", setting the XSLT step's xslt to " + xslt
               + (CefPlan.KAFKA.equals(drafted.output())
                ? " and standardKafkaProducer.kafkaConfig to the KafkaConfig the user picks (kafka_configs)"
                : "")
               + ". Then step_sample over the Events streams (stepping sends nothing), draft_cef_mapping "
               + "pipeline_uuid=<it> stream_ids=... to review what it writes, and write_documentation with the "
               + "Events stream_ids: its Field mapping section is the plan's tables.";
    }

    // The user's answer: given, from the standing instructions, the plan kept with the XSLT the call works on, the
    // user's answer for the build earlier, or asked now (a form; else the agent asks), and then kept for the build.
    // Seen in testing, when every call not given it asked: asked again to review a pipeline whose kept plan said, and
    // again to save after that.
    private Optional<Boolean> customKeys(final ToolCall call,
                                         final ToolCallContext context,
                                         final Request request,
                                         final Map<String, Object> said) {
        if (request.customKeys() != null) {
            choices.chooseCustomKeys(request.build(), request.customKeys());
            return Optional.of(request.customKeys());
        }
        if (said.get(CefInstructions.CUSTOM_KEYS) instanceof final Boolean instructed) {
            return Optional.of(instructed);
        }
        final Optional<Boolean> kept = keptCustomKeys(request);
        if (kept.isPresent()) {
            return kept;
        }
        final Optional<Boolean> earlier = choices.customKeys(request.build());
        if (earlier.isPresent()) {
            return earlier;
        }
        final Optional<Boolean> chosen = consent.choose(call, context, NAME, CUSTOM_QUESTION, CUSTOM_OPTIONS, null)
                .map(CUSTOM_OPTIONS.get(1)::equals);
        chosen.ifPresent(allowed -> choices.chooseCustomKeys(request.build(), allowed));
        return chosen;
    }

    // What the plan kept with the XSLT the call works on says: one given by uuid, or the one a pipeline runs.
    private Optional<Boolean> keptCustomKeys(final Request request) {
        try {
            if (request.uuid() != null) {
                return Optional.of(keptPlan(request.uuid()).customKeys());
            }
            if (request.pipelineUuid() != null) {
                return keptMappings.keptXslt(request.pipelineUuid())
                        .filter(kept -> XsltSaves.CEF.equals(kept.kept().kind()))
                        .map(kept -> CefPlan.fromJson(kept.kept().payload()).customKeys());
            }
        } catch (final RuntimeException e) {
            // No plan to go by: asked, as without one.
        }
        return Optional.empty();
    }

    // The standing instructions where the pipeline will be: their texts, and the docs as get_instructions gives
    // them.
    private Standing instructions(final ToolCallContext context, final List<String> folders, final List<String> feeds) {
        final Map<String, Object> found = agentInstructions.applicableInstructions(context.session(),
                "Standing instructions read by the " + NAME + " tool",
                agentInstructions.targets(folders, List.of(), feeds, List.of()));
        final List<?> instructions = NullSafe.list((List<?>) found.get("instructions"));
        final List<String> texts = instructions.stream()
                .map(i -> i instanceof final Map<?, ?> map && map.get("instructions") instanceof final String text
                        ? text
                        : "")
                .toList();
        return new Standing(texts, instructions);
    }

    @SuppressWarnings("unchecked")
    private static List<CefOverride> saidOverrides(final Map<String, Object> said) {
        return NullSafe.list((List<CefOverride>) said.get(CefInstructions.OVERRIDES));
    }

    private static Optional<Map<String, Object>> fromStandingInstructions(final Map<String, Object> said) {
        final List<CefOverride> overrides = saidOverrides(said);
        if (overrides.isEmpty() && !said.containsKey(CefInstructions.CUSTOM_KEYS)
            && !JsonValues.truthy(said.get(CefInstructions.TOPIC))
            && !JsonValues.truthy(said.get(CefInstructions.TEMPLATE))) {
            return Optional.empty();
        }
        final Map<String, Object> from = new LinkedHashMap<>();
        if (!overrides.isEmpty()) {
            from.put("mappings", overrides.stream().map(o -> o.path() + " -> " + o.key()).toList());
        }
        for (final String key : List.of(CefInstructions.CUSTOM_KEYS, CefInstructions.TOPIC, CefInstructions.VENDOR,
                CefInstructions.TEMPLATE)) {
            if (said.containsKey(key)) {
                from.put(key, said.get(key));
            }
        }
        return Optional.of(from);
    }

    private static String doneLine(final CefOverride o) {
        if (NullSafe.isEmptyString(o.key())) {
            return o.path() + " -> not sent";
        }
        final String key = CefKeys.keyOf(o.key());
        return o.path() + " -> " + key + (key.equals(o.key())
                ? ""
                : " (" + o.key() + ")");
    }

    // --------------------------------------------------------------------------------
    // Arguments.
    // --------------------------------------------------------------------------------

    /// @param call A call.
    /// @return What it asks.
    /// @throws ToolRefusal if an argument can't be read.
    Request request(final ToolCall call) {
        final Map<?, ?> args = argsMap(call);
        final String output = stringArg(call, OUTPUT_ARG);
        if (output != null && !CefPlan.OUTPUTS.contains(output)) {
            throw new ToolRefusal("output should be 'kafka' or 'text'");
        }
        final List<CefOverride> overrides;
        final Map<String, CefValue> header;
        try {
            overrides = overrides(args.get(OVERRIDES_ARG));
            header = header(args.get(HEADER_ARG));
        } catch (final MappingValidationException e) {
            throw new ToolRefusal("overrides or header: " + e.errors().getFirst().message());
        }
        final Set<String> names = new LinkedHashSet<>();
        CefKeys.HEADER.forEach(field -> names.add(field.name()));
        final Set<String> unknown = new TreeSet<>(header.keySet());
        unknown.removeAll(names);
        if (!unknown.isEmpty()) {
            throw new ToolRefusal("header has no " + ToolText.list(unknown) + ": its fields are "
                                  + ToolText.list(names));
        }
        return new Request(StartOnboardingTool.streamIds(call, STREAM_IDS_ARG),
                bool(args.get(CUSTOM_KEYS_ARG)),
                overrides,
                header,
                output,
                stringArg(call, TOPIC_ARG),
                stringArg(call, KAFKA_KEY_ARG),
                stringArg(call, PIPELINE_UUID_ARG),
                stringArg(call, BUILD_ARG),
                stringArg(call, NAME_ARG),
                stringArg(call, UUID_ARG),
                stringArg(call, CHANGE_ARG),
                stringListArg(call, FEEDS_ARG),
                stringListArg(call, FOLDERS_ARG),
                style(args.get(STYLE_ARG)),
                ToolArgs.flag(call, DISCARD_HAND_EDIT_ARG),
                choices(args.get(HAND_EDIT_CHOICES_ARG)));
    }

    @SuppressWarnings("unchecked")
    private static List<CefOverride> overrides(final Object value) {
        Object given = value;
        if (given instanceof final String text) {
            try {
                given = ToolJson.loads(text);
            } catch (final ToolJson.JsonDecodeException e) {
                throw ToolArgs.notJson(OVERRIDES_ARG, e.msg(), "pass a list of objects");
            }
        }
        if (given == null) {
            return List.of();
        }
        final List<?> items = given instanceof final List<?> list
                ? list
                : List.of(given);
        final List<CefOverride> out = new ArrayList<>();
        for (final Object item : items) {
            if (!(item instanceof final Map<?, ?> map)) {
                throw new ToolRefusal("overrides or header: Input should be a valid dictionary or instance of "
                                      + "Override");
            }
            out.add(CefOverride.fromJson((Map<String, Object>) map));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, CefValue> header(final Object value) {
        Object given = value;
        if (given instanceof final String text) {
            given = text.isBlank()
                    ? Map.of()
                    : readJson(text, HEADER_ARG);
        }
        final Map<String, CefValue> out = new LinkedHashMap<>();
        if (given instanceof final Map<?, ?> map) {
            map.forEach((name, v) -> out.put(String.valueOf(name), v instanceof final String text
                    ? CefValue.constant(text)
                    : CefValue.fromJson(v instanceof final Map<?, ?> m
                            ? (Map<String, Object>) m
                            : null)));
        } else if (given != null) {
            throw new ToolRefusal("header is header fields by name, e.g. {'vendor': 'Acme'}");
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static XsltStyle style(final Object value) {
        Object given = value;
        if (given instanceof final String text) {
            given = text.isBlank()
                    ? null
                    : readJson(text, STYLE_ARG);
        }
        if (given == null) {
            return null;
        }
        if (!(given instanceof final Map<?, ?> map)) {
            throw new ToolRefusal("style is how the XSLT is written, e.g. {'naming': 'snake_case'}");
        }
        try {
            return XsltStyle.fromJson((Map<String, Object>) map);
        } catch (final MappingValidationException e) {
            throw new ToolRefusal("style: " + e.errors().getFirst().message());
        }
    }

    private static Map<String, String> choices(final Object value) {
        Object given = value;
        if (given instanceof final String text) {
            given = text.isBlank()
                    ? null
                    : readJson(text, HAND_EDIT_CHOICES_ARG);
        }
        if (!(given instanceof final Map<?, ?> map)) {
            return null;
        }
        final Map<String, String> out = new LinkedHashMap<>();
        map.forEach((k, v) -> out.put(String.valueOf(k), String.valueOf(v)));
        return out;
    }

    private static Boolean bool(final Object value) {
        if (value instanceof final Boolean given) {
            return given;
        }
        if (value instanceof final String text && !text.isBlank()) {
            return Boolean.parseBoolean(text.strip());
        }
        return null;
    }

    private static Object readJson(final String text, final String name) {
        try {
            return ToolJson.loads(text);
        } catch (final ToolJson.JsonDecodeException e) {
            throw ToolArgs.notJson(name, ToolArgs.where(e), null);
        }
    }

    private boolean savesXslt(final ToolCall call) {
        return (stringArg(call, BUILD_ARG) != null && stringArg(call, NAME_ARG) != null)
               || stringArg(call, UUID_ARG) != null;
    }

    // The first if it has text, else the second (which may be null).
    private static String orElse(final String first, final String second) {
        return NullSafe.isNonBlankString(first)
                ? first
                : second;
    }

    /// The fields a plan doesn't send, by why, then by kind of event: each path as the plan has it.
    ///
    /// @param notSent The plan's not-sent list (`path`, `event_type`, `why`).
    /// @return For each why, in the order first given: `why`, `count`, and `paths` by event type (`common` for
    /// every event's).
    static List<Map<String, Object>> groupedNotSent(final List<Map<String, String>> notSent) {
        final Map<String, Map<String, List<String>>> byWhy = new LinkedHashMap<>();
        for (final Map<String, String> entry : notSent) {
            byWhy.computeIfAbsent(NullSafe.string(entry.get("why")), why -> new LinkedHashMap<>())
                    .computeIfAbsent(NullSafe.nonBlankStringElse(entry.get("event_type"), "common"),
                            kind -> new ArrayList<>())
                    .add(entry.get("path"));
        }
        final List<Map<String, Object>> out = new ArrayList<>();
        byWhy.forEach((why, paths) -> {
            final Map<String, Object> group = new LinkedHashMap<>();
            group.put("why", why);
            group.put("count", paths.values().stream().mapToInt(List::size).sum());
            group.put("paths", paths);
            out.add(group);
        });
        return out;
    }

    private static String head(final String text, final int n) {
        return text.length() <= n
                ? text
                : text.substring(0, n);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mutable(final Map<String, Object> json) {
        final Map<String, Object> out = new LinkedHashMap<>();
        json.forEach((k, v) -> out.put(k, mutableValue(v)));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object mutableValue(final Object value) {
        if (value instanceof final Map<?, ?> map) {
            return mutable((Map<String, Object>) map);
        }
        if (value instanceof final List<?> list) {
            final List<Object> out = new ArrayList<>();
            list.forEach(item -> out.add(mutableValue(item)));
            return out;
        }
        return value;
    }


    // --------------------------------------------------------------------------------


    /// What a call asks.
    ///
    /// @param streamIds       The Events streams.
    /// @param customKeys      The user's answer about keys outside the dictionary, or null.
    /// @param overrides       The user's mappings.
    /// @param header          Header fields to set, by name.
    /// @param output          `kafka` or `text`, or null.
    /// @param topic           The Kafka topic, or null.
    /// @param kafkaKey        An XPath for each Kafka record's key, or null.
    /// @param pipelineUuid    A CEF pipeline to review, or null.
    /// @param build           The build to save in, or null.
    /// @param name            The new XSLT's name, or null.
    /// @param uuid            An XSLT saved with a CEF plan, or null.
    /// @param change          What the change is, or null.
    /// @param feeds           The Events feeds.
    /// @param folders         Folders the pipeline will live in.
    /// @param style           How the XSLT is written, or null.
    /// @param discardHandEdit True if the user said to drop their hand edit.
    /// @param handEditChoices The user's answers about hand edits, or null.
    record Request(List<Long> streamIds,
                   Boolean customKeys,
                   List<CefOverride> overrides,
                   Map<String, CefValue> header,
                   String output,
                   String topic,
                   String kafkaKey,
                   String pipelineUuid,
                   String build,
                   String name,
                   String uuid,
                   String change,
                   List<String> feeds,
                   List<String> folders,
                   XsltStyle style,
                   boolean discardHandEdit,
                   Map<String, String> handEditChoices) {

    }

    /// What stepping a CEF pipeline gave.
    ///
    /// @param lines   The CEF lines, in order.
    /// @param events  The Event each came from, or null where not known.
    /// @param element The element read.
    /// @param sent    How it sends them: `output` (kafka or text) and `topic`.
    /// @param stopped True if the call's time ran out before every record was stepped.
    record Stepped(List<String> lines, List<XmlElement> events, String element, Map<String, String> sent,
                   boolean stopped) {

    }

    /// Overrides applied to a plan.
    ///
    /// @param plan The plan with them.
    /// @param done What each did.
    record Applied(CefPlan plan, List<String> done) {

    }

    // The standing instructions' texts, and the docs as get_instructions gives them.
    private record Standing(List<String> texts, List<?> instructions) {

    }
}
