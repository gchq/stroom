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

package stroom.mcp.impl.e2e;

import stroom.docref.DocRef;
import stroom.documentation.impl.DocumentationStore;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.shared.ExplorerNode;
import stroom.feed.shared.FeedDoc;
import stroom.mcp.impl.McpTerminal;
import stroom.mcp.impl.McpTestClient;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.xslt.XsltStore;
import stroom.util.shared.NullSafe;

import com.google.inject.Injector;
import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/// On the Lucene path, the onboarding plan followed as an agent would, through the REAL tools on a real Stroom. It
/// starts with start_onboarding and from then on does what `next` says: at every step it takes the call the server
/// names, fills in only its `<placeholders>` (the decisions an agent makes with the user: names, the mapping, the
/// dashboard's columns), and calls exactly that tool. It checks:
///
/// - the steps come in the expected order for the Lucene path;
/// - following `next` never leads into a refusal;
/// - each write's own `next` agrees with build_status, and the work is not `done` before promotion;
/// - wrong moves along the way are refused with the right call named, and leave `next` where it was: processing
///   before a clean step, and stepping a deleted namesake feed's stream (run before the Lucene walk);
/// - before promotion, documentation the build's own change left behind holds promotion up, naming the call, while a
///   hand edit of the XSLT only warns.
///
/// The samples step plays the user's terminal: the file goes, by the command upload_sample gave,
/// to the REAL upload servlet.
///
/// Left out: the Elasticsearch path (index template agreed, a live cluster, following another index),
/// which needs an Elasticsearch cluster.
@SuppressWarnings("unchecked")
class TestE2ePlanWalk extends AbstractE2eTest {

    private static final String STAMP = "1";
    private static final String FEED = "WALK-LUC-" + STAMP;
    private static final String INDEX_NAME = "WALK-" + STAMP + "-INDEX-V1";
    // The translation suite's CSV case (e2e_translation.CASES['csv']['sample']).
    private static final String SAMPLE = """
            time,user,host,ip,result
            2026-09-28T10:00:00,alice,ws01,10.0.0.1,ok
            2026-09-28T10:05:00,bob,ws02,10.0.0.2,fail
            2026-09-28T10:07:30,carol,ws03,10.0.0.3,ok
            """;
    private static final List<String> STAGE_1 = List.of("feed", "samples", "converter", "translation", "pipeline",
            "stepped", "processed", "validated", "documented");
    private static final List<String> LUCENE = concat(STAGE_1, List.of("index", "indexing_pipeline", "indexed",
            "index_documented", "promoted"));
    // The first call `next` names for each step; Lucene has no template step: the new indexing pipeline is stepped
    // as the first call of indexed.
    private static final Map<String, String> FIRST_CALL = Map.ofEntries(
            Map.entry("feed", "create_feed"),
            Map.entry("samples", "upload_sample"),
            Map.entry("converter", "build_data_splitter"),
            Map.entry("translation", "draft_translation_mapping"),
            Map.entry("pipeline", "find_pipeline_templates"),
            Map.entry("stepped", "step_sample"),
            Map.entry("processed", "create_processor_filter"),
            Map.entry("validated", "check_events"),
            Map.entry("documented", "write_documentation"),
            Map.entry("index", "get_field_conventions"),
            Map.entry("indexing_pipeline", "save_xslt"),
            Map.entry("indexed", "step_sample"),
            Map.entry("index_documented", "write_documentation"),
            Map.entry("promoted", "promote_build"));
    private static final Pattern DIGEST = Pattern.compile("(<!-- stroom-mcp field-mapping )[0-9a-f]{16}");

    @Inject
    private Injector injector;
    @Inject
    private XsltStore xsltStore;
    @Inject
    private DocumentationStore documentationStore;

    private String build;
    private Long oldStream;
    private List<Long> events;
    private Map<String, Object> plan;
    private Map<String, Object> index;

    @Override
    boolean enablesDisabledFilters() {
        return true;
    }

    @Test
    void theLucenePathIsWalkedByFollowingNextAlone() {
        client.answeringWith(TestE2ePlanWalk::decide);
        deletedFeedBefore();
        final Map<String, Object> started = run("start_onboarding", args("source_name", "Walk lucene " + STAMP,
                "samples", Map.of("logons.csv", SAMPLE)));
        build = (String) started.get("build");
        assertThat(map(started, "next")).containsEntry("step", "feed");
        assertThat(map(map(started, "next"), "call")).containsEntry("tool", "create_feed");

        Map<String, Object> result = null;
        for (int i = 0; i < LUCENE.size(); i++) {
            final String step = LUCENE.get(i);
            final Map<String, Object> next = at(step, FIRST_CALL.get(step));
            detoursBefore(step, map(next, "call"));
            if ("promoted".equals(step)) {
                assertThat(states())
                        .as("every step done before promotion")
                        .allSatisfy((name, state) -> assertThat(state).isIn("done", "not needed", "not recorded"));
            }
            result = step(step, map(next, "call"));
            final String following = i + 1 < LUCENE.size()
                    ? LUCENE.get(i + 1)
                    : null;
            if (following != null && result.get("next") instanceof Map<?, ?>) {
                assertThat(map(result, "next")).as("the result's own next after %s", step)
                        .containsEntry("step", following);
                assertThat(Boolean.FALSE.equals(result.get("done")))
                        .as("not done after %s (%s)", step, result.get("done"))
                        .isEqualTo(!"promoted".equals(following));
            }
        }
        assertThat(strings(result, "promoted")).anyMatch(line -> line.startsWith("moved Pipeline"));
    }

    private Map<String, Object> step(final String step, final Map<String, Object> call) {
        return switch (step) {
            case "feed" -> feedStep(call);
            case "samples" -> samplesStep(call);
            case "converter" -> run("build_data_splitter", fill(call, "save_as", FEED + "-CSV"));
            case "translation" -> translationStep(call);
            case "pipeline" -> pipelineStep(call);
            case "stepped" -> steppedStep(call);
            case "processed" -> processedStep(call);
            case "validated" -> validatedStep(call);
            case "documented" -> documentedStep(call);
            case "index" -> indexStep();
            case "indexing_pipeline" -> indexingPipelineStep(call);
            case "indexed" -> indexedStep(call);
            case "index_documented" -> run("write_documentation", with(fill(call, "markdown",
                    "## Purpose and data\n\nLogons indexed into " + INDEX_NAME + ".\n"), "change", "Created"));
            case "promoted" -> promotedStep(call);
            default -> throw new AssertionError("No step " + step);
        };
    }

    // Stage 1 -------------------------------------------------------------------------------------------------

    private Map<String, Object> feedStep(final Map<String, Object> call) {
        final Map<String, Object> made = run("create_feed", fill(call, "name", FEED));
        if (oldStream != null) {
            assertThat(String.valueOf(made.get("note")))
                    .as("the earlier feed's streams, still under the name, are pointed out and not taken as samples")
                    .contains(String.valueOf(oldStream), "earlier feed");
        }
        return made;
    }

    private Map<String, Object> samplesStep(final Map<String, Object> call) {
        // As the plan has it: the sample files' paths, and a command per file for the user's terminal. The terminal
        // is played here: the file goes to the upload servlet, by the command.
        final String path = "/home/walker/walk-sample.csv";
        final Map<String, Object> given = run("upload_sample", fill(call, "files", List.of(path)));
        final Map<String, Object> command = list(given, "commands").getFirst();
        assertThat((String) command.get("powershell")).as("a command per file for the user's terminal")
                .startsWith("curl.exe ");
        assertThat((String) command.get("bash")).contains(path);
        final byte[] content = SAMPLE.getBytes(StandardCharsets.UTF_8);
        final McpTerminal.Posted posted = McpTerminal.run(injector, admin, (String) command.get("bash"), path,
                content);
        final Map<String, Object> sent = posted.body();
        assertThat(posted.status()).as("the whole file reached Stroom from the terminal: %s", sent).isEqualTo(200);
        assertThat(sent.get("stream_id")).isNotNull();
        assertThat(number(sent.get("bytes"))).isEqualTo(content.length);
        // As onboarding does next: the sample surveyed with the build. Its survey doc once made the plan take the
        // build for one on an existing feed, and indexing was skipped.
        client.json("survey_feed", args("stream_ids", List.of(sent.get("stream_id")), "build", build));
        final Map<String, Object> state = states();
        assertThat(state).as("surveyed with the build, still a new source: indexing to do")
                .containsEntry("index", "to do")
                .containsEntry("indexed", "to do")
                .containsEntry("covered", "not needed");
        return sent;
    }

    private Map<String, Object> translationStep(final Map<String, Object> call) {
        final Map<String, Object> draft = run("draft_translation_mapping", fill(call));
        assertThat(draft).as("drafted a mapping from the sample").containsKey("mapping");
        // The agent decides the actions with the user; here, the generator suite's logon mapping.
        return run("build_translation_xslt", args("mapping", csvMapping(),
                "stream_ids", map(call, "arguments").get("stream_ids"),
                "build", build, "name", FEED + "-Translation"));
    }

    private Map<String, Object> pipelineStep(final Map<String, Object> call) {
        final Map<String, Object> found = run("find_pipeline_templates", fill(call));
        final Map<String, Object> template = list(found, "candidates").stream()
                .filter(candidate -> "Event Data (Text)".equals(candidate.get("name")))
                .findFirst()
                .orElseThrow();
        return run("create_pipeline", args("build", build, "name", FEED + "-Events",
                "template_uuid", template.get("uuid")));
    }

    private Map<String, Object> steppedStep(final Map<String, Object> call) {
        final Map<String, Object> result = run("step_sample", fill(call));
        assertThat(result).as("stepped clean").containsEntry("verdict", "clean");
        return result;
    }

    private Map<String, Object> processedStep(final Map<String, Object> call) {
        final Map<String, Object> arguments = fill(call);
        run("create_processor_filter", arguments);
        processAll();
        final Map<String, Object> done = run("wait_for_processing", args(
                "pipeline_uuid", arguments.get("pipeline_uuid"), "stream_ids", arguments.get("stream_ids")));
        assertThat(done).as("processed into Events").containsEntry("gate", "pass");
        events = new ArrayList<>();
        for (final Map<String, Object> stream : list(done, "streams")) {
            ((List<?>) stream.get("events")).forEach(id -> events.add(number(id)));
        }
        return done;
    }

    private Map<String, Object> validatedStep(final Map<String, Object> call) {
        // The server reads the Events itself, and records the check on the pipeline that wrote them, so the step is
        // done rather than "not recorded".
        final Map<String, Object> valid = run("check_events", fill(call));
        assertThat(valid).containsEntry("ok", true);
        assertThat(valid.get("read")).as("the Events read from their streams").isNotNull();
        assertThat(valid.get("recorded_for")).as("recorded for the pipeline that wrote them").isNotNull();
        assertThat(states()).as("build_status has the validated step done").containsEntry("validated", "done");
        return valid;
    }

    private Map<String, Object> documentedStep(final Map<String, Object> call) {
        // As an agent got it wrong: the Events stream instead of the raw sample, and no change line. The server
        // documents from the raw stream it was made from, and a new doc's change line is 'Created'.
        final Map<String, Object> written = run("write_documentation", fill(call,
                "markdown", "## Purpose and data\n\nLogons from the walk.\n", "stream_ids", events));
        assertThat(String.valueOf(written.get("note")))
                .as("documented from the raw sample, though given the Events stream, with no change line")
                .contains("which it was made from");
        assertThat(written.get("field_mapping")).isNotNull();
        return written;
    }

    // Stage 2 -------------------------------------------------------------------------------------------------

    private Map<String, Object> indexStep() {
        // next names get_field_conventions, which asks the user how fields are named; the drafts below are what its
        // reply leads to, over the Events streams.
        client.json("get_field_conventions", args());
        final Map<String, Object> draft = run("draft_index_mapping", args("events_stream_ids", events,
                "backend", "lucene", "index_name", INDEX_NAME, "convention", "stroom-flat"));
        plan = map(draft, "plan");
        index = run("create_index_doc", args("build", build, "backend", "lucene", "name", INDEX_NAME,
                "time_field", plan.get("time_field"), "plan", plan));
        return index;
    }

    private Map<String, Object> indexingPipelineStep(final Map<String, Object> call) {
        final Map<String, Object> xslt = run("save_xslt", fill(call, "name", INDEX_NAME + "-XSLT",
                "index_plan", plan));
        final Map<String, Object> template = list(run("find_pipeline_templates", args("stage", "indexing")),
                "candidates").stream()
                .filter(candidate -> "lucene".equals(candidate.get("backend"))
                                     && "Indexing".equals(candidate.get("name")))
                .findFirst()
                .orElseThrow();
        return run("create_indexing_pipeline", args("build", build, "name", INDEX_NAME + " - Indexing",
                "template_uuid", template.get("uuid"), "xslt_uuid", xslt.get("uuid"),
                "events_stream_ids", events, "index_uuid", index.get("uuid")));
    }

    private Map<String, Object> indexedStep(final Map<String, Object> firstCall) {
        // The plan names each call in turn: a step, the filter, the wait while it runs, then verify_index.
        final List<String> tools = new ArrayList<>();
        Map<String, Object> call = firstCall;
        Map<String, Object> result = null;
        for (int i = 0; i < 8; i++) {
            final String tool = (String) call.get("tool");
            tools.add(tool);
            switch (tool) {
                case "step_sample" -> result = run("step_sample", fill(call));
                case "create_processor_filter" -> {
                    result = run("create_processor_filter", fill(call));
                    // Not verified yet: promotion's approval would say so.
                    assertThat(promotionApproval())
                            .as("promoting now: the approval warns the sample isn't indexed and verified")
                            .anyMatch(warning -> warning.contains("has not been indexed and verified"));
                    processAll();
                }
                case "wait_for_processing" -> result = run("wait_for_processing", fill(call));
                case "verify_index" -> {
                    assertThat(String.valueOf(map(call, "arguments").get("fields")))
                            .as("the plan suggests the dashboard's columns")
                            .startsWith("<the columns the user chose; suggest ");
                    result = run("verify_index", with(fill(call, "expected_documents", 3,
                                    "fields", List.of("EventTime", "UserId", "HostName")),
                            "exact", List.of(Map.of("field", "UserId", "value", "bob"))));
                    assertThat(result).as("verify_index passed").containsEntry("passed", true);
                }
                default -> throw new AssertionError("indexed: an unexpected call " + tool);
            }
            final Map<String, Object> status = client.json("build_status", args("build", build));
            if (!"indexed".equals(map(status, "next").get("step"))) {
                break;
            }
            call = map(map(status, "next"), "call");
        }
        assertThat(tools).as("indexed only once verify_index passed, after the filter: %s", tools)
                .endsWith("verify_index")
                .contains("create_processor_filter");
        return result;
    }

    private Map<String, Object> promotedStep(final Map<String, Object> call) {
        promotionAndStaleDocumentation();
        return run("promote_build", fill(call, "destinations", destinations()));
    }

    // Asked for by the user: promotion waits for documentation the build's own change left behind, but a hand edit
    // of the XSLT only warns, so a user's own edits never hold a promotion up. Both put back afterwards.
    private void promotionAndStaleDocumentation() {
        // In full: each document's type, name and uuid.
        final Map<String, Object> status = client.json("build_status", args("build", build, "full", true));
        final Map<String, Object> translation = list(status, "pipelines").stream()
                .filter(pipeline -> "translation".equals(pipeline.get("stage")))
                .findFirst()
                .orElseThrow();
        final String translationUuid = (String) translation.get("uuid");
        final DocRef documentation = buildDoc(status, DocumentationDoc.TYPE, (String) translation.get("name"));
        final DocumentationDoc doc = asUser(admin, () -> documentationStore.readDocument(documentation));
        final String written = doc.getData();
        // Written before the build's change: the XSLT is what its mapping generates, the doc's digest isn't its.
        final Matcher digest = DIGEST.matcher(written);
        assertThat(digest.find()).as("the doc's field mapping digest").isTrue();
        writeDocumentation(doc, digest.replaceFirst("$1" + "0".repeat(16)));
        final String refusal = replyText("promote_build", args("build", build, "destinations", destinations()));
        assertThat(refusal).as("promotion waits for the documentation, naming the call")
                .contains("Not promoted", "write_documentation", translationUuid);

        // A hand edit of the XSLT instead: the documentation is out of date too, but promotion only warns.
        final DocRef xsltRef = buildDoc(status, XsltDoc.TYPE, FEED + "-Translation");
        final XsltDoc xslt = asUser(admin, () -> xsltStore.readDocument(xsltRef));
        final String code = xslt.getData();
        final String edited = code.replaceFirst("<Environment>Dev</Environment>",
                "<Environment>Hand-edited</Environment>");
        assertThat(edited).as("the XSLT edited by hand in Stroom (its Environment constant)").isNotEqualTo(code);
        writeXslt(xsltRef, edited);
        writeDocumentation(asUser(admin, () -> documentationStore.readDocument(documentation)), written);
        try {
            final List<String> warnings = promotionApproval();
            dump("approval with the hand edit", warnings);
            assertThat(warnings).as("a hand edit: the approval is still offered, and the stale documentation is a "
                                    + "warning in it")
                    .anyMatch(warning -> warning.contains("predates") && !warning.contains("waits for this"));
        } finally {
            writeXslt(xsltRef, code);
        }
    }

    // ---------------------------------------------------------------------------------------------------------

    // Wrong moves at this point, refused with the right call named; `next` stays where it was.
    private void detoursBefore(final String step, final Map<String, Object> call) {
        if ("stepped".equals(step)) {
            final Map<String, Object> arguments = map(call, "arguments");
            final String message = replyText("create_processor_filter", args(
                    "pipeline_uuid", arguments.get("pipeline_uuid"), "stream_ids", arguments.get("stream_ids")));
            assertThat(message).as("processing before a clean step is refused, naming step_sample")
                    .contains("no clean step", "step_sample");
            final List<Long> ids = ((List<?>) arguments.get("stream_ids")).stream()
                    .map(AbstractE2eTest::number)
                    .toList();
            assertThat(ids).as("the plan names only the feed's own streams").doesNotContain(oldStream);
            final String older = replyText("step_sample", args("pipeline_uuid", arguments.get("pipeline_uuid"),
                    "stream_ids", List.of(oldStream)));
            assertThat(older).as("stepping the deleted feed's stream is refused, naming the feed's own")
                    .contains("older than feed", String.valueOf(ids.getFirst()));
            at("stepped", "step_sample");
        }
        if (Set.of("index_template", "indexed").contains(step)) {
            final Map<String, Object> status = client.json("build_status", args("build", build));
            assertThat(map(status, "next").get("step"))
                    .as("not indexed yet: neither documentation nor promotion is next")
                    .isNotIn("index_documented", "promoted");
        }
    }

    // Seen in VS Code: a feed of the same name made and deleted before, its streams still in Stroom under the name.
    private void deletedFeedBefore() {
        final Map<String, Object> feed = client.json("create_feed", args("build", "walk-old-" + STAMP,
                "name", FEED));
        final Map<String, Object> upload = client.json("upload_sample", args("feed", FEED,
                "sample", "[{\"old\": true}]"));
        oldStream = number(upload.get("stream_id"));
        delete(new DocRef(FeedDoc.TYPE, (String) feed.get("uuid"), FEED));
        findFolder("System/MCP Workspace/walk-old-" + STAMP).map(ExplorerNode::getDocRef).ifPresent(this::delete);
    }

    // promote_build's approval, as after the user has seen the layout: its review, then the call with its id. The
    // approval is the form the user is shown (Stroom's endpoint changes nothing without one), read and declined, so
    // nothing is promoted. Its warnings are those of the review, shown in the chat (the form names the first, then
    // "the rest is in the chat").
    private List<String> promotionApproval() {
        final Map<String, Object> review = client.json("promote_build", args("build", build,
                "destinations", destinations()));
        final int shown = client.forms().size();
        client.answeringWith(McpTestClient::decline);
        try {
            client.call("promote_build", args("build", build, "destinations", destinations(),
                    "reviewed", review.get("review_id")));
        } finally {
            client.answeringWith(TestE2ePlanWalk::decide);
        }
        final List<ElicitFormRequest> forms = client.forms();
        assertThat(forms).as("the approval is offered, in a form").hasSize(shown + 1);
        assertThat(forms.getLast().message()).startsWith("Promote build '" + build + "'");
        return strings(review, "warnings");
    }

    private Map<String, Object> destinations() {
        final String folder = "System/Walk Promoted " + STAMP + "/lucene";
        // No destination for the verification dashboard: it goes where the index it searches goes.
        final Map<String, Object> destinations = new LinkedHashMap<>();
        for (final String type : List.of("Feed", "Pipeline", "XSLT", "TextConverter", "Documentation", "Index",
                "ElasticIndex")) {
            destinations.put(type, folder);
        }
        return destinations;
    }

    // build_status says the next step is this one, and its call this tool.
    private Map<String, Object> at(final String step, final String tool) {
        final Map<String, Object> next = map(client.json("build_status", args("build", build)), "next");
        assertThat(next.get("step") + " -> " + map(next, "call").get("tool"))
                .as("next")
                .isEqualTo(step + " -> " + tool);
        return next;
    }

    // A call the plan led to: it must not be refused. Confirmations and approvals are given, as the user would.
    private Map<String, Object> run(final String tool, final Map<String, Object> arguments) {
        final Map<String, Object> result = agreed(tool, arguments);
        dump(tool, result);
        return result;
    }

    // The call's arguments as `next` gave them, with the agent's decisions in place of every <placeholder>.
    private static Map<String, Object> fill(final Map<String, Object> call, final Object... decisions) {
        final Map<String, Object> arguments = new LinkedHashMap<>(map(call, "arguments"));
        arguments.putAll(args(decisions));
        assertThat(arguments.entrySet())
                .as("%s: every placeholder decided", call.get("tool"))
                .noneMatch(entry -> entry.getValue() instanceof final String text && text.startsWith("<"));
        return arguments;
    }

    private static Map<String, Object> with(final Map<String, Object> arguments, final Object... more) {
        final Map<String, Object> all = new LinkedHashMap<>(arguments);
        all.putAll(args(more));
        return all;
    }

    // Each step's state, as build_status gives it briefly (by default), but promotion's own.
    private Map<String, Object> states() {
        final Map<String, Object> state = new LinkedHashMap<>(map(client.json("build_status", args("build", build)),
                "steps"));
        state.remove("promoted");
        return state;
    }

    private static DocRef buildDoc(final Map<String, Object> status, final String type, final String name) {
        return list(status, "documents").stream()
                .filter(doc -> type.equals(doc.get("type")) && name.equals(doc.get("name")))
                .map(doc -> new DocRef(type, (String) doc.get("uuid"), name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No " + type + " " + name + " in " + status.get("documents")));
    }

    private void writeDocumentation(final DocumentationDoc doc, final String data) {
        asUser(admin, () -> documentationStore.writeDocument(doc.copy().data(data).build()));
    }

    private void writeXslt(final DocRef xsltRef, final String code) {
        asUser(admin, () -> xsltStore.writeDocument(xsltStore.readDocument(xsltRef).copy().data(code).build()));
    }

    // The user's answers to the forms the walk meets: the event-logging version the translation suites write
    // (4.1.0), Lucene and the Stroom flat naming for the index, and otherwise what is proposed.
    private static ElicitResult decide(final ElicitFormRequest form) {
        final ElicitResult agreed = McpTestClient.agree(form);
        final Map<String, Object> content = new LinkedHashMap<>(NullSafe.map(agreed.content()));
        final Object properties = NullSafe.map(form.requestedSchema()).get("properties");
        if (properties instanceof final Map<?, ?> fields) {
            fields.forEach((name, field) -> {
                if (field instanceof final Map<?, ?> schema && schema.get("enum") instanceof final List<?> options) {
                    options.stream()
                            .map(String::valueOf)
                            .filter(option -> option.contains("4.1.0") || option.startsWith("Lucene")
                                              || option.contains("stroom-flat") || option.contains("Stroom flat"))
                            .findFirst()
                            .ifPresent(option -> content.put(String.valueOf(name), option));
                }
            });
        }
        return new ElicitResult(agreed.action(), content, null);
    }

    // The generator suite's CSV mapping (e2e_generator.MAPPINGS['csv']).
    static Map<String, Object> csvMapping() {
        final List<Object> common = new ArrayList<>(List.of(
                Map.of("path", "EventTime/TimeCreated", "field", "time", "time_format", "yyyy-MM-dd'T'HH:mm:ss"),
                Map.of("path", "EventSource/System/Name", "value", "E2E"),
                Map.of("path", "EventSource/System/Environment", "value", "Dev"),
                Map.of("path", "EventSource/Generator", "value", "e2e-generator"),
                Map.of("path", "EventSource/Device/HostName", "field", "host"),
                Map.of("path", "EventSource/Device/IPAddress", "field", "ip"),
                Map.of("path", "EventSource/User/Id", "field", "user")));
        final List<Object> failed = new ArrayList<>(logon(Map.of("value", "false")));
        failed.add(Map.of("path", "EventDetail/Description", "value", "Failed logon"));
        failed.add(Map.of("path", "EventDetail/Authenticate/Data", "data_name", "result", "field", "result"));
        return args("input", "data_splitter",
                "common", common,
                "events", List.of(
                        args("name", "failed logon",
                                "when", List.of(Map.of("field", "result", "equals", "fail")),
                                "fields", failed),
                        args("name", "logon",
                                "fields", logon(Map.of("field", "result", "map", Map.of("ok", "true"))))));
    }

    private static List<Object> logon(final Map<String, Object> success) {
        final Map<String, Object> outcome = new LinkedHashMap<>(Map.of("path",
                "EventDetail/Authenticate/Outcome/Success"));
        outcome.putAll(success);
        return List.of(
                Map.of("path", "EventDetail/TypeId", "value", "Logon"),
                Map.of("path", "EventDetail/Authenticate/Action", "value", "Logon"),
                Map.of("path", "EventDetail/Authenticate/User/Id", "field", "user"),
                outcome);
    }

    private static List<String> concat(final List<String> first, final List<String> second) {
        final List<String> all = new ArrayList<>(first);
        all.addAll(second);
        return List.copyOf(all);
    }
}
