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

import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.shared.AiToolAccess;
import stroom.docref.DocRef;
import stroom.documentation.impl.DocumentationStore;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;
import stroom.index.impl.IndexShardManager;
import stroom.index.impl.IndexShardManager.IndexShardAction;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.mcp.impl.McpTestClient;
import stroom.mcp.impl.McpTestClients;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.meta.shared.Status;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.pipeline.xslt.XsltStore;
import stroom.processor.api.ProcessorFilterService;
import stroom.security.api.SecurityContext;
import stroom.test.AbstractAiIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.test.CommonTranslationTestHelper;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.DocPath;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import static org.assertj.core.api.Assertions.assertThat;

/// What the end-to-end suites share: an administrator's client of the REAL tools ([McpTestClient], by way of
/// [McpTestClients]) granted every tool, on a real Stroom (the integration test database and stream store, the
/// standard content packs' template pipelines and event-logging schemas).
///
/// The user's part is played by the test: the client agrees to every form unless told otherwise, a review
/// (`needs_review`) is confirmed and each field described (`needs_descriptions`) by [#agreed], and the processor
/// tasks and index shard flushes Stroom's jobs would run are run with [#processAll()] and [#flushIndexShards()]. The
/// suites' shared helpers are here too: the onboarding of a sample ([#onboard]), the reads of Stroom that check what
/// the tools did (streams, documents, folders, tags), and the fixtures made directly in Stroom (shared XSLTs and
/// template pipelines outside any build, documents edited by hand).
@SuppressWarnings("unchecked")
abstract class AbstractE2eTest extends AbstractAiIntegrationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractE2eTest.class);

    /// The event-logging version the content pack holds.
    static final String VERSION = "4.1.0";
    /// The event-logging namespace.
    static final String EVENT_NS = "event-logging:3";
    /// The CSV case's sample.
    static final String CSV_SAMPLE = """
            time,user,host,ip,result
            2026-09-28T10:00:00,alice,ws01,10.0.0.1,ok
            2026-09-28T10:05:00,bob,ws02,10.0.0.2,fail
            2026-09-28T10:07:30,carol,ws03,10.0.0.3,ok
            """;
    /// The Data Splitter for a CSV with a header line.
    static final String CSV_SPLITTER = textResource("csv_splitter.xml");
    /// The hand-written translation for the CSV case, at [#VERSION].
    static final String CSV_XSLT = textResource("e2e_translation-csv.xsl");
    /// The CSV case.
    static final Case CSV_CASE = new Case("csv", "Event Data (Text)", CSV_SAMPLE, CSV_SPLITTER, CSV_XSLT);

    // The next calls [#leadsOn] makes: those that only read or step, so following them leaves the suite's state
    // as it was.
    private static final Set<String> FOLLOWED = Set.of("step_sample", "step_records", "build_status",
            "processing_status", "describe_template", "find_pipeline_templates", "get_instructions");

    @Inject
    private McpTestClients clients;
    @Inject
    private SecurityContext securityContext;
    @Inject
    private CommonTestControl commonTestControl;
    @Inject
    private CommonTranslationTestHelper translationTestHelper;
    @Inject
    private ExplorerNodeService explorerNodeService;
    @Inject
    private ExplorerService explorerService;
    @Inject
    private MetaService metaService;
    @Inject
    private XsltStore xsltStore;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private DocumentationStore documentationStore;
    @Inject
    private ProcessorFilterService processorFilterService;
    @Inject
    private IndexShardManager indexShardManager;

    /// The administrator's client, which shows forms: the user answers them (see [McpTestClient#agree]).
    McpTestClient client;
    /// The same user's client with no forms: gates come back as a `status` (`needs_confirmation`, `needs_approval`,
    /// `needs_guidance`) for the agent to act on.
    McpTestClient plainClient;
    /// The administrator the suite works as.
    UserRef admin;

    // False while [#agreedNotFollowed] makes its call.
    private boolean following = true;

    @BeforeEach
    void setUpClients() {
        admin = administrator();
        // The template pipelines (Event Data (Text), Indexing, ...) and the event-logging schemas.
        asUser(admin, () -> commonTestControl.createRequiredXMLSchemas());
        toolPolicy("Everything", AiToolAccess.READ_WRITE, admin);
        client = clients.client(admin, true);
        plainClient = clients.client(admin, false);
    }

    /// Whether [#agreed] follows each reply's `next.call`, as an agent working unprompted would: the tool it
    /// names must be one the session is offered and, where it only reads or steps, making it as given must not be
    /// refused. Off unless a suite turns it on.
    ///
    /// @return True to follow.
    boolean followsNext() {
        return false;
    }

    /// Whether [#agreed] enables a processor filter its call made disabled, as the user would in Stroom (filters
    /// made disabled are the user's to enable). Off unless a suite turns it on.
    ///
    /// @return True to enable them.
    boolean enablesDisabledFilters() {
        return false;
    }

    // --------------------------------------------------------------------------------
    // Calls

    /// Calls a tool that is expected to answer in JSON, failing if it refuses.
    ///
    /// @param tool      The tool.
    /// @param arguments Its arguments.
    /// @return Its answer.
    Map<String, Object> json(final String tool, final Map<String, Object> arguments) {
        return client.json(tool, arguments);
    }

    /// A gated call made as a user agreeing would: the forms are agreed by the client; a `needs_review` reply (what to
    /// show the user in the chat) is called again with its review id, and a `needs_descriptions` one with a description
    /// of each field ([#described]), as the agent would. The answer must not be left waiting on anything else. A
    /// disabled filter is then enabled if the suite [enables them][#enablesDisabledFilters], and the answer's next call
    /// followed if the suite [follows them][#followsNext].
    ///
    /// @param tool      The tool.
    /// @param arguments Its arguments.
    /// @return The answer once nothing more is asked.
    Map<String, Object> agreed(final String tool, final Map<String, Object> arguments) {
        final Map<String, Object> call = new LinkedHashMap<>(arguments);
        for (int round = 0; round < 6; round++) {
            final Map<String, Object> result = json(tool, call);
            final String status = String.valueOf(result.get("status"));
            if ("needs_review".equals(status)) {
                // Shown to the user in the chat, then confirmed in a form: the id of what was shown.
                dump(tool + " needs_review", result);
                call.put("reviewed", result.get("review_id"));
            } else if ("needs_descriptions".equals(status)) {
                final Map<String, Object> descriptions = new LinkedHashMap<>(
                        (Map<String, Object>) call.getOrDefault("field_descriptions", Map.of()));
                descriptions.putAll(described(list(result, "fields")));
                call.put("field_descriptions", descriptions);
                if (result.get("confirmation_id") != null) {
                    call.put("confirmation_id", result.get("confirmation_id"));
                }
            } else {
                assertThat(status).as("%s left waiting: %s", tool, result).doesNotStartWith("needs_");
                if (enablesDisabledFilters()) {
                    enableIfDisabled(result);
                }
                if (followsNext() && following) {
                    leadsOn(tool, result);
                }
                return result;
            }
        }
        throw new AssertionError(tool + " still asked after six rounds: " + call);
    }

    /// As [#agreed], without following the reply's `next`: for a suite's check of the state before the agent
    /// would follow it (e.g. the processing gate before the replaced code is stepped).
    ///
    /// @param tool      The tool.
    /// @param arguments Its arguments.
    /// @return The answer once nothing more is asked.
    Map<String, Object> agreedNotFollowed(final String tool, final Map<String, Object> arguments) {
        following = false;
        try {
            return agreed(tool, arguments);
        } finally {
            following = true;
        }
    }

    /// What an agent answers a `needs_descriptions` round with: a stand-in description of each field, from its
    /// values.
    ///
    /// @param fields The fields asked about.
    /// @return A description of each.
    static Map<String, Object> described(final List<Map<String, Object>> fields) {
        final Map<String, Object> descriptions = new LinkedHashMap<>();
        for (final Map<String, Object> field : fields) {
            final List<?> values = (List<?>) field.getOrDefault("values", List.of());
            descriptions.put((String) field.get("field"), "E2E: " + field.get("field") + ", seen as "
                                                          + (values.isEmpty()
                    ? "nothing"
                    : values.stream().map(String::valueOf).collect(Collectors.joining(", "))));
        }
        return descriptions;
    }

    /// Calls a tool that is expected to refuse, failing if it doesn't.
    ///
    /// @param tool      The tool.
    /// @param arguments Its arguments.
    /// @return The refusal's text.
    String refusal(final String tool, final Map<String, Object> arguments) {
        final CallToolResult result = client.call(tool, arguments);
        final String text = McpTestClient.text(result);
        assertThat(result.isError()).as("%s was expected to refuse, but answered %s", tool, text).isTrue();
        return text;
    }

    /// Calls a tool whose answer may or may not be a refusal, for its text either way.
    ///
    /// @param tool      The tool.
    /// @param arguments Its arguments.
    /// @return The refusal's text, or the answer's JSON when it wasn't refused.
    String replyText(final String tool, final Map<String, Object> arguments) {
        return McpTestClient.text(client.call(tool, arguments));
    }

    /// Checks that a result's `next` names a tool the session is offered, and returns it: following `next` is what
    /// keeps an agent working unprompted.
    ///
    /// @param result A tool's result.
    /// @return Its next step.
    Map<String, Object> next(final Map<String, Object> result) {
        final Map<String, Object> next = map(result, "next");
        final Map<String, Object> call = (Map<String, Object>) next.get("call");
        if (call != null && call.get("tool") != null) {
            assertThat(client.toolNames()).as("the tool next names, in %s", next).contains((String) call.get("tool"));
        }
        return next;
    }

    /// @param result A tool's result.
    /// @return The tool its `next` says to call (see [#next]).
    String nextTool(final Map<String, Object> result) {
        final Map<String, Object> call = (Map<String, Object>) next(result).get("call");
        assertThat(call).as("next's call in %s", result).isNotNull();
        return (String) call.get("tool");
    }

    /// @param result A tool's result.
    /// @return Its `next.step`, or null when it has no next.
    static String nextStep(final Map<String, Object> result) {
        return result.get("next") instanceof final Map<?, ?> next
                ? (String) next.get("step")
                : null;
    }

    // --------------------------------------------------------------------------------
    // The tools the suites call most

    /// `create_feed` in a build, as the user agrees to its name.
    ///
    /// @param build The build.
    /// @param name  The feed's name.
    /// @return The answer.
    Map<String, Object> createFeed(final String build, final String name) {
        final Map<String, Object> feed = agreed("create_feed", args("build", build, "name", name));
        assertThat(feed).containsEntry("name", name);
        return feed;
    }

    /// `upload_sample` of a text.
    ///
    /// @param feed The feed.
    /// @param text The sample.
    /// @return The raw stream's id.
    long upload(final String feed, final String text) {
        return upload(feed, text, null);
    }

    /// `upload_sample` of a text, with receipt headers.
    ///
    /// @param feed    The feed.
    /// @param text    The sample.
    /// @param headers Extra receipt headers, or null for none.
    /// @return The raw stream's id.
    long upload(final String feed, final String text, final Map<String, String> headers) {
        final Map<String, Object> upload = json("upload_sample", args("feed", feed, "sample", text,
                "headers", headers));
        assertThat(upload.get("stream_id")).as("upload_sample's stream of %s", upload).isNotNull();
        return number(upload.get("stream_id"));
    }

    /// The candidate template pipelines for a stage (`find_pipeline_templates`).
    ///
    /// @param stage The stage (translation, indexing, ...).
    /// @return Its candidates.
    List<Map<String, Object>> templates(final String stage) {
        return list(json("find_pipeline_templates", args("stage", stage)), "candidates");
    }

    /// @param stage The stage (translation, indexing, ...).
    /// @param name  The template pipeline's name, e.g. `Event Data (JSON)`.
    /// @return Its UUID, as find_pipeline_templates offers it.
    String templateUuid(final String stage, final String name) {
        final List<Map<String, Object>> candidates = templates(stage);
        return candidates.stream()
                .filter(candidate -> name.equals(candidate.get("name")))
                .map(candidate -> (String) candidate.get("uuid"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No " + stage + " template " + name + " in " + candidates));
    }

    /// `save_text_converter` of a Data Splitter in a build (`translation.create_text_converter`).
    ///
    /// @param build The build.
    /// @param name  The converter's name.
    /// @param code  The Data Splitter.
    /// @return Its UUID.
    String saveTextConverter(final String build, final String name, final String code) {
        return uuidOf(agreed("save_text_converter", args("build", build, "name", name, "code", code,
                "converter_type", "DATA_SPLITTER")));
    }

    /// `save_xslt` of hand-written code in a build (`translation.create_xslt`).
    ///
    /// @param build The build.
    /// @param name  The XSLT's name.
    /// @param code  The code.
    /// @return Its UUID.
    String saveXslt(final String build, final String name, final String code) {
        return uuidOf(agreed("save_xslt", args("build", build, "name", name, "code", code)));
    }

    /// `create_pipeline` from a template, its forms agreed.
    ///
    /// @param build      The build.
    /// @param name       The pipeline's name.
    /// @param template   The template's UUID.
    /// @param properties Its set_properties ([#docProperty] and [#valueProperty]), none to leave them to the
    ///                   build.
    /// @return The answer.
    Map<String, Object> createPipeline(final String build,
                                       final String name,
                                       final String template,
                                       final Map<?, ?>... properties) {
        return agreed("create_pipeline", args("build", build, "name", name, "template_uuid", template,
                "set_properties", properties.length == 0
                        ? null
                        : List.of(properties)));
    }

    /// A property naming a document, as create_pipeline's `set_properties` takes it.
    ///
    /// @param element The element.
    /// @param name    The property.
    /// @param uuid    The document's UUID.
    /// @param type    The document's type.
    /// @return The property.
    static Map<String, Object> docProperty(final String element,
                                           final String name,
                                           final String uuid,
                                           final String type) {
        return Map.of("element", element, "name", name, "doc_uuid", uuid, "doc_type", type);
    }

    /// A property with a plain value, as create_pipeline's `set_properties` takes it.
    ///
    /// @param element The element.
    /// @param name    The property.
    /// @param value   Its value.
    /// @return The property.
    static Map<String, Object> valueProperty(final String element, final String name, final Object value) {
        return Map.of("element", element, "name", name, "value", value);
    }

    /// `step_sample` over streams.
    ///
    /// @param pipeline The pipeline's UUID.
    /// @param streams  The streams.
    /// @return The answer.
    Map<String, Object> stepSample(final String pipeline, final List<Long> streams) {
        return json("step_sample", args("pipeline_uuid", pipeline, "stream_ids", streams));
    }

    /// The output of one element for one record, as step_pipeline gives it.
    ///
    /// @param pipeline The pipeline's UUID.
    /// @param stream   The stream.
    /// @param record   The record, from 0.
    /// @param element  The element, e.g. `translationFilter`.
    /// @return Its output.
    String stepOutput(final String pipeline, final long stream, final int record, final String element) {
        return stepOutput(pipeline, stream, record, null, element);
    }

    /// One record's output of the translation (the translationFilter), as step_pipeline gives it.
    ///
    /// @param pipeline The pipeline's UUID.
    /// @param stream   The stream.
    /// @param record   The record, from 0.
    /// @return Its output.
    String translationOutput(final String pipeline, final long stream, final int record) {
        return stepOutput(pipeline, stream, record, null, "translationFilter");
    }

    /// One record's output of the translation (the translationFilter), from a part of a multi-part stream, as
    /// step_pipeline gives it.
    ///
    /// @param pipeline The pipeline's UUID.
    /// @param stream   The stream.
    /// @param record   The record, from 0.
    /// @param part     The part, from 0.
    /// @return Its output.
    String translationOutput(final String pipeline, final long stream, final int record, final int part) {
        return stepOutput(pipeline, stream, record, part, "translationFilter");
    }

    /// Each record's Events, as Stroom writes them, canonical.
    ///
    /// @param pipeline The pipeline's UUID.
    /// @param stream   The raw stream.
    /// @param records  The records, from 0.
    /// @return Their events.
    List<String> eventsOf(final String pipeline, final long stream, final List<Integer> records) {
        final List<String> out = new ArrayList<>();
        for (final int record : records) {
            final Element root = parse(translationOutput(pipeline, stream, record)).getDocumentElement();
            for (final Element event : children(root, "Event")) {
                out.add(canonical(event));
            }
        }
        return out;
    }

    /// `create_processor_filter` for sample streams, Stroom's processing run as its job would ([#processAll]), and
    /// `wait_for_processing`.
    ///
    /// @param pipeline The pipeline's UUID.
    /// @param streams  The streams.
    /// @return The wait's answer.
    Map<String, Object> process(final String pipeline, final List<Long> streams) {
        agreed("create_processor_filter", args("pipeline_uuid", pipeline, "stream_ids", streams));
        processAll();
        return json("wait_for_processing", args("pipeline_uuid", pipeline, "stream_ids", streams,
                "timeout_seconds", 10));
    }

    /// Onboards a source from its sample to valid, documented Events (feed, upload, template, converter and XSLT
    /// written by hand, pipeline, stepping, processing, validation, documentation).
    ///
    /// @param sample The case: its format names the build (`e2e-<format>`) and feed (`E2E-<FORMAT>`).
    /// @return What was made.
    Onboarded onboard(final Case sample) {
        final String build = "e2e-" + sample.format();
        final String feed = "E2E-" + sample.format().toUpperCase();
        final Map<String, Object> profile = json("profile_sample", args("sample", sample.sample()));
        LOGGER.info(() -> LogUtil.message("profile: {}; parser: {}", profile.get("format"),
                profile.get("suggested_parser")));
        assertThat(profile.get("format")).as("profiled").isNotNull();
        createFeed(build, feed);
        final long raw = upload(feed, sample.sample());
        final String templateUuid = templateUuid("translation", sample.template());
        final List<Map<String, Object>> properties = new ArrayList<>();
        if (sample.converter() != null) {
            final Map<String, Object> converter = json("save_text_converter", args("build", build, "name", feed,
                    "converter_type", "DATA_SPLITTER", "code", sample.converter()));
            properties.add(docProperty("dsParser", "textConverter", uuidOf(converter), "TextConverter"));
        }
        final String pipelineName = feed + "-Events";
        final Map<String, Object> xslt = json("save_xslt", args("build", build, "name", pipelineName,
                "code", sample.xslt()));
        properties.add(docProperty("translationFilter", "xslt", uuidOf(xslt), "XSLT"));
        if ("json".equals(sample.format())) {
            properties.add(valueProperty("jsonParser", "addRootObject", false));
        }
        final Map<String, Object> pipeline = agreed("create_pipeline", args("build", build, "name", pipelineName,
                "template_uuid", templateUuid, "set_properties", properties));
        final String pipelineUuid = uuidOf(pipeline);
        final Map<String, Object> stepped = stepSample(pipelineUuid, List.of(raw));
        assertThat(stepped).as("stepped clean: %s", stepped.get("groups")).containsEntry("verdict", "clean");
        // A hand-written XSLT keeps no mapping, so the plan's translation step (an XSLT saved from a mapping) stays
        // to do, and next names draft_translation_mapping whatever follows.
        assertThat(nextTool(stepped)).isEqualTo("draft_translation_mapping");
        final Map<String, Object> filter = agreed("create_processor_filter", args("pipeline_uuid", pipelineUuid,
                "stream_ids", List.of(raw)));
        assertThat(filter).containsEntry("enabled", true);
        processAll();
        final Map<String, Object> gate = json("wait_for_processing", args("pipeline_uuid", pipelineUuid,
                "stream_ids", List.of(raw), "timeout_seconds", 10));
        assertThat(gate).as("one Events stream per raw stream: %s", gate.get("streams"))
                .containsEntry("gate", "pass");
        final long eventsId = number(((List<?>) list(gate, "streams").getFirst().get("events")).getFirst());
        // validate_events and check_event_quality on the first record: check_events does both.
        final Map<String, Object> checked = json("check_events", args("stream_ids", List.of(eventsId)));
        assertThat(map(checked, "schema")).as("Events valid against %s", map(checked, "schema").get("schema"))
                .containsEntry("valid", true);
        assertThat(checked).as("event quality checks pass: %s", checked).containsEntry("ok", true);
        final Map<String, Object> doc = agreed("write_documentation", args("build", build,
                "pipeline_uuid", pipelineUuid,
                "markdown", "# " + pipelineName + "\n\n## Purpose and data\n\n" + sample.format()
                            + " sample for the translation e2e test.\n\n"
                            // Written by hand (no mapping kept with the XSLT), so the section is too.
                            + "## Field mapping\n\n| XPath | From |\n| --- | --- |\n"
                            + "| `EventSource/User/Id` | the user field |\n",
                "change", "Created"));
        final String docUuid = uuidOf(doc);
        final String text = documentation(docUuid);
        assertThat(text).as("documentation written to the doc's body: %s chars", text.length())
                .contains("translation e2e test", "## Version control");
        return new Onboarded(build, feed, raw, pipelineUuid, pipelineName, uuidOf(xslt), docUuid, eventsId);
    }

    /// The documentation as Stroom keeps it goes down to the field.
    ///
    /// @param docUuid The Documentation doc.
    /// @param fields  The fields that must each have a row.
    /// @param values  Values expected in their field's row.
    void documentedToTheField(final String docUuid, final List<String> fields, final Map<String, String> values) {
        final String text = documentation(docUuid);
        final String section = text.contains("## Field mapping")
                ? text.split("## Field mapping", 2)[1].split("\n## ", 2)[0]
                : "";
        final Map<String, List<String>> rows = fieldRows(section);
        assertThat(section).as("the documentation has a Field mapping section").isNotEmpty();
        assertThat(rows.keySet()).as("a row for each of its %s fields", fields.size()).containsAll(fields);
        assertThat(rows).as("every row (%s) says what the sample gave", rows.size())
                .allSatisfy((field, cells) -> assertThat(cells).as(field).isNotEmpty().last()
                        .asString().isNotEmpty());
        assertThat(section).contains("Sample values", "| Description |");
        rows.forEach((field, cells) -> {
            assertThat(cells.getFirst().strip()).as("%s described", field).isNotEmpty();
            for (final String repeat : List.of("sampled document", "in the sample", "distinct value",
                    "document that has it")) {
                assertThat(cells.getFirst()).as("%s's description repeats the sample", field)
                        .doesNotContain(repeat);
            }
        });
        values.forEach((field, value) -> assertThat(rows.getOrDefault(field, List.of("")).getLast())
                .as("%s's row has the sample's value %s", field, value)
                .contains("`" + value));
        if (section.contains("In sample")) {
            int counted = 0;
            String header = "";
            final List<String> unsaid = new ArrayList<>();
            for (final String line : section.split("\n")) {
                if (line.startsWith("| ") && !line.startsWith("| `") && !line.contains("---")) {
                    header = line;
                } else if (line.startsWith("| `") && header.contains("In sample")) {
                    counted++;
                    if (List.of("always", "% of", "not in the sample", "not stored", "not read", "not surveyed")
                            .stream().noneMatch(line::contains)) {
                        unsaid.add(line.split(" \\| ")[0]);
                    }
                }
            }
            assertThat(counted).as("rows with how often each was populated").isPositive();
            assertThat(unsaid).as("how often each was populated in the sample").isEmpty();
        }
    }

    /// The Field mapping tables' rows, by field (the cells after its name).
    ///
    /// @param section The Field mapping section.
    /// @return Each field's cells.
    static Map<String, List<String>> fieldRows(final String section) {
        final Map<String, List<String>> rows = new LinkedHashMap<>();
        for (final String line : section.split("\n")) {
            if (line.startsWith("| `")) {
                final String inner = stripped(line.strip(), '|');
                final List<String> cells = new ArrayList<>();
                for (final String cell : inner.split(" \\| ", -1)) {
                    cells.add(cell.strip());
                }
                rows.putIfAbsent(stripped(cells.getFirst(), '`'), List.copyOf(cells.subList(1, cells.size())));
            }
        }
        return rows;
    }

    // --------------------------------------------------------------------------------
    // Stroom's jobs

    /// Stroom's processing job: every processor task the filters make is created and run, one at a time.
    void processAll() {
        securityContext.asProcessingUser(() -> translationTestHelper.processAll());
    }

    /// Stroom's index writer job: every Lucene index shard is flushed, so searches and shard counts see what was
    /// indexed.
    void flushIndexShards() {
        securityContext.asProcessingUser(() -> indexShardManager.performAction(FindIndexShardCriteria.matchAll(),
                IndexShardAction.FLUSH));
    }

    // --------------------------------------------------------------------------------
    // Stroom, directly

    /// @param id A stream's id.
    /// @return Its meta, whatever its status.
    Meta meta(final long id) {
        return securityContext.asProcessingUserResult(() -> metaService.getMeta(id, true));
    }

    /// @param feed A feed's name.
    /// @return Its unlocked streams.
    List<Meta> streamsOf(final String feed) {
        return securityContext.asProcessingUserResult(() -> metaService.find(new FindMetaCriteria(
                MetaExpressionUtil.createFeedExpression(feed))).getValues());
    }

    /// @param feed A feed's name.
    /// @return The ids of its unlocked streams, oldest first.
    List<Long> streamIdsOf(final String feed) {
        return streamsOf(feed).stream().map(Meta::getId).sorted().toList();
    }

    /// @param parentId A stream's id.
    /// @return Its unlocked child streams, oldest first.
    List<Meta> childrenOf(final long parentId) {
        return securityContext.asProcessingUserResult(() -> metaService.find(new FindMetaCriteria(
                        MetaExpressionUtil.createParentIdExpression(parentId, Status.UNLOCKED))).getValues())
                .stream()
                .sorted(Comparator.comparingLong(Meta::getId))
                .toList();
    }

    /// @param uuid A pipeline's UUID.
    /// @return Its own layer's properties as Stroom keeps them (added ones), as JSON.
    List<Map<String, Object>> addedProperties(final String uuid) {
        return added(uuid, "properties");
    }

    /// @param uuid A pipeline's UUID.
    /// @param part The part of its own layer: `elements`, `links` or `properties`.
    /// @return What its own layer adds there, as Stroom keeps it, as JSON.
    List<Map<String, Object>> added(final String uuid, final String part) {
        return securityContext.asProcessingUserResult(() -> {
            final Object data = pipelineStore.readDocument(new DocRef(PipelineDoc.TYPE, uuid)).getPipelineData();
            final Map<String, Object> json = JsonUtil.readValue(JsonUtil.writeValueAsString(data, false), Map.class);
            final Map<String, Object> adds = NullSafe.map((Map<String, Object>) json.get(part));
            return NullSafe.list((List<Map<String, Object>>) adds.get("add"));
        });
    }

    /// @param uuid An XSLT's UUID.
    /// @return It, as Stroom holds it.
    XsltDoc readXslt(final String uuid) {
        return asUser(admin, () -> xsltStore.readDocument(new DocRef(XsltDoc.TYPE, uuid)));
    }

    /// @param uuid An XSLT's UUID.
    /// @return The mapping kept with it (`read_mapping(...)[1]['mapping']`).
    Map<String, Object> keptMapping(final String uuid) {
        final KeptMapping.Kept kept = KeptMapping.readMapping(readXslt(uuid).getDescription())
                .orElseThrow(() -> new AssertionError("No mapping kept with " + uuid));
        return (Map<String, Object>) kept.payload().get("mapping");
    }

    /// The XSLT's Documentation tab cleared, as a user might in Stroom.
    ///
    /// @param uuid The XSLT's UUID.
    void clearDescription(final String uuid) {
        editXslt(uuid, doc -> doc.copy().description("").build());
    }

    /// An XSLT changed directly in Stroom, as in its editor.
    ///
    /// @param uuid   The XSLT's UUID.
    /// @param change The change.
    void editXslt(final String uuid, final UnaryOperator<XsltDoc> change) {
        asUser(admin, () -> xsltStore.writeDocument(change.apply(xsltStore.readDocument(
                new DocRef(XsltDoc.TYPE, uuid)))));
    }

    /// @return The folder the standard template pipelines are in (Template Pipelines), where the
    /// suites' own fixtures go (found from the template: the content pack's folder isn't found by name).
    ExplorerNode templateFolder() {
        final String uuid = templateUuid("translation", "Event Data (Text)");
        return securityContext.asProcessingUserResult(() -> explorerNodeService.getParent(
                new DocRef(PipelineDoc.TYPE, uuid)).orElseThrow(() -> new AssertionError("No folder for " + uuid)));
    }

    /// A shared XSLT outside any build, beside the template pipelines (`shared_doc`): Stroom resolves an import by the
    /// document's name.
    ///
    /// @param name Its name.
    /// @param code Its code.
    /// @return Its UUID.
    String sharedXslt(final String name, final String code) {
        final ExplorerNode node = create(XsltDoc.TYPE, name, templateFolder());
        editXslt(node.getUuid(), doc -> doc.copy().data(code).build());
        return node.getUuid();
    }

    /// A template of the suite's own, a copy of a standard one beside it (`fixture_template`), so its children are
    /// only the suite's.
    ///
    /// @param name   Its name.
    /// @param source The standard translation template it copies.
    /// @return Its UUID.
    String fixtureTemplate(final String name, final String source) {
        final ExplorerNode node = create(PipelineDoc.TYPE, name, templateFolder());
        final String sourceUuid = templateUuid("translation", source);
        asUser(admin, () -> {
            final PipelineDoc original = pipelineStore.readDocument(new DocRef(PipelineDoc.TYPE, sourceUuid));
            final PipelineDoc fixture = pipelineStore.readDocument(node.getDocRef());
            pipelineStore.writeDocument(fixture.copy()
                    .parentPipeline(original.getParentPipeline())
                    .pipelineData(original.getPipelineData())
                    .description("Fixture for the e2e suite")
                    .build());
        });
        return node.getUuid();
    }

    /// A pipeline made directly in Stroom, outside any build: a child of a pipeline, with properties of its own.
    ///
    /// @param folder     The folder.
    /// @param name       Its name.
    /// @param parentUuid The pipeline it inherits from.
    /// @param properties Its own properties ([#property]).
    /// @return Its UUID.
    String childPipeline(final ExplorerNode folder,
                         final String name,
                         final String parentUuid,
                         final List<PipelineProperty> properties) {
        final ExplorerNode node = create(PipelineDoc.TYPE, name, folder);
        asUser(admin, () -> {
            final PipelineDoc parent = pipelineStore.readDocument(new DocRef(PipelineDoc.TYPE, parentUuid));
            final PipelineDoc child = pipelineStore.readDocument(node.getDocRef());
            pipelineStore.writeDocument(child.copy()
                    .parentPipeline(parent.asDocRef())
                    .pipelineData(new PipelineDataBuilder().addProperties(properties).build())
                    .build());
        });
        return node.getUuid();
    }

    /// A pipeline property naming a document, as Stroom keeps it.
    ///
    /// @param element  An element.
    /// @param name     A property of it.
    /// @param document The document it names.
    /// @return The property.
    static PipelineProperty property(final String element, final String name, final DocRef document) {
        return PipelineProperty.builder()
                .element(element)
                .name(name)
                .value(new PipelinePropertyValue(document))
                .build();
    }

    /// @param uuid A Documentation doc's UUID.
    /// @return Its text, as Stroom keeps it (empty for none).
    String documentation(final String uuid) {
        return asUser(admin, () -> NullSafe.string(documentationStore.readDocument(
                new DocRef(DocumentationDoc.TYPE, uuid)).getData()));
    }

    /// A Documentation doc made directly in Stroom, its text typed in as in the UI.
    ///
    /// @param folder The folder's path.
    /// @param name   Its name.
    /// @param text   Its text.
    /// @return Its UUID.
    String documentationDoc(final String folder, final String name, final String text) {
        final ExplorerNode node = create(DocumentationDoc.TYPE, name, folder);
        asUser(admin, () -> documentationStore.writeDocument(documentationStore.readDocument(node.getDocRef())
                .copy()
                .data(text)
                .build()));
        return node.getUuid();
    }

    /// @param uuid A document's UUID.
    /// @return Its explorer tags.
    Set<String> tags(final String uuid) {
        return securityContext.asProcessingUserResult(() -> explorerNodeService.getNodeByUuid(uuid)
                .map(ExplorerNode::getTags)
                .map(Set::copyOf)
                .orElse(Set.of()));
    }

    /// A document made directly in Stroom, as in its explorer, in a folder given by its path.
    ///
    /// @param type   Its type.
    /// @param name   Its name.
    /// @param folder The folder's path, e.g. `System/Template Pipelines`.
    /// @return Its node.
    ExplorerNode create(final String type, final String name, final String folder) {
        return create(type, name, findFolder(folder).orElseThrow(() -> new AssertionError("No folder " + folder)));
    }

    /// A document made directly in Stroom, as in its explorer, in a folder.
    ///
    /// @param type   Its type.
    /// @param name   Its name.
    /// @param folder The folder.
    /// @return Its node.
    ExplorerNode create(final String type, final String name, final ExplorerNode folder) {
        return asUser(admin, () -> explorerService.create(type, name, folder, PermissionInheritance.DESTINATION));
    }

    /// Deletes a document or folder directly in Stroom, as in its explorer.
    ///
    /// @param docRef The document.
    void delete(final DocRef docRef) {
        asUser(admin, () -> {
            final ExplorerNode node = explorerNodeService.getNode(docRef).orElseThrow();
            explorerService.delete(List.of(node));
        });
    }

    /// The folder at an explorer path, made directly in Stroom (with any folders above it) where it isn't there
    /// yet.
    ///
    /// @param path Its explorer path from the root, e.g. `System/E2E Bases`.
    /// @return The folder.
    ExplorerNode folder(final String path) {
        final List<String> names = List.of(path.split("/"));
        return asUser(admin, () -> explorerService.ensureFolderPath(DocPath.fromParts(names.subList(1,
                names.size())), explorerNodeService.getRoot(), PermissionInheritance.DESTINATION));
    }

    /// @param path An explorer path from the root, e.g. `System/MCP Workspace/a-build`.
    /// @return The folder there, if there is one.
    Optional<ExplorerNode> findFolder(final String path) {
        return securityContext.asProcessingUserResult(() -> {
            final List<String> names = List.of(path.split("/"));
            Optional<ExplorerNode> folder = Optional.of(explorerNodeService.getRoot());
            for (final String name : names.subList(1, names.size())) {
                folder = folder.flatMap(parent -> explorerNodeService.getNodesByNameAndType(parent, name,
                                ExplorerConstants.FOLDER_TYPE)
                        .stream()
                        .findFirst());
            }
            return folder;
        });
    }

    /// @param uuid A document's UUID.
    /// @return The explorer path of its folder, e.g. `System/MCP Workspace/a-build`.
    String folderOf(final String uuid) {
        return securityContext.asProcessingUserResult(() -> {
            final ExplorerNode node = explorerNodeService.getNodeByUuid(uuid).orElseThrow(() ->
                    new AssertionError("No explorer node for " + uuid));
            return explorerNodeService.getPath(node.getDocRef()).stream()
                    .map(ExplorerNode::getName)
                    .collect(Collectors.joining("/"));
        });
    }

    /// @param folder A folder's path.
    /// @param type   A document type.
    /// @param name   A document name.
    /// @return The documents of that type and name in it.
    List<ExplorerNode> documentsIn(final String folder, final String type, final String name) {
        return securityContext.asProcessingUserResult(() -> findFolder(folder)
                .map(parent -> explorerNodeService.getNodesByNameAndType(parent, name, type))
                .orElse(List.of()));
    }

    // --------------------------------------------------------------------------------
    // Forms

    /// A user who answers a choice with the first of the given options it offers, and agrees to everything else.
    ///
    /// @param choices The options to pick, in order of preference.
    /// @return The user.
    static Function<ElicitFormRequest, ElicitResult> choosing(final String... choices) {
        return form -> {
            final ElicitResult agreed = McpTestClient.agree(form);
            final Map<String, Object> content = new LinkedHashMap<>(agreed.content());
            final Object properties = form.requestedSchema().get("properties");
            if (properties instanceof final Map<?, ?> fields) {
                fields.forEach((name, field) -> {
                    if (field instanceof final Map<?, ?> schema
                        && schema.get("enum") instanceof final List<?> options) {
                        for (final String choice : choices) {
                            if (options.contains(choice)) {
                                content.put(String.valueOf(name), choice);
                                break;
                            }
                        }
                    }
                });
            }
            return new ElicitResult(ElicitResult.Action.ACCEPT, content, null);
        };
    }

    /// @return The messages of the forms the user has been shown, in order.
    List<String> formMessages() {
        return client.forms().stream().map(ElicitFormRequest::message).toList();
    }

    /// What the user was shown in forms: each form's message and its fields.
    ///
    /// @param forms The forms.
    /// @return Their text, a line each.
    static String formsText(final List<ElicitFormRequest> forms) {
        final StringBuilder sb = new StringBuilder();
        for (final ElicitFormRequest form : forms) {
            sb.append(form.message()).append(' ').append(JsonUtil.writeValueAsString(form.requestedSchema(), false))
                    .append('\n');
        }
        return sb.toString();
    }

    /// The forms the user has been shown since a count of them.
    ///
    /// @param before How many had been shown.
    /// @return Those shown since.
    List<ElicitFormRequest> formsSince(final int before) {
        final List<ElicitFormRequest> all = client.forms();
        return all.subList(before, all.size());
    }

    // --------------------------------------------------------------------------------
    // Results

    /// @param pairs Keys and values, in turn.
    /// @return A map of them in their order, nulls left out, for a tool's arguments.
    static Map<String, Object> args(final Object... pairs) {
        final Map<String, Object> args = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            if (pairs[i + 1] != null) {
                args.put((String) pairs[i], pairs[i + 1]);
            }
        }
        return args;
    }

    /// @param result A result.
    /// @param key    A key in it whose value is an object.
    /// @return That object.
    static Map<String, Object> map(final Map<String, Object> result, final String key) {
        assertThat(result.get(key)).as("%s of %s", key, result).isInstanceOf(Map.class);
        return (Map<String, Object>) result.get(key);
    }

    /// @param result A result.
    /// @param key    A key in it whose value is a list of objects.
    /// @return That list, empty if there is none.
    static List<Map<String, Object>> list(final Map<String, Object> result, final String key) {
        final Object value = result.get(key);
        if (value == null) {
            return List.of();
        }
        assertThat(value).as("%s of %s", key, result).isInstanceOf(List.class);
        return (List<Map<String, Object>>) value;
    }

    /// @param result A result.
    /// @param key    A key in it whose value is a list.
    /// @return Its items as text, empty if there is none.
    static List<String> strings(final Map<String, Object> result, final String key) {
        final Object value = result.get(key);
        return value instanceof final List<?> items
                ? items.stream().map(String::valueOf).toList()
                : List.of();
    }

    /// @param result A result.
    /// @param key    A key in it whose value is a list of numbers (e.g. stream ids).
    /// @return Them as longs, empty if there is none.
    static List<Long> ids(final Map<String, Object> result, final String key) {
        return ((List<?>) result.getOrDefault(key, List.of())).stream()
                .map(id -> ((Number) id).longValue())
                .toList();
    }

    /// @param value A JSON number.
    /// @return It as a long.
    static long number(final Object value) {
        assertThat(value).isInstanceOf(Number.class);
        return ((Number) value).longValue();
    }

    /// @param result A tool's answer about a document: its `uuid`, or its `saved` document's.
    /// @return The document's UUID.
    static String uuidOf(final Map<String, Object> result) {
        if (result.get("uuid") instanceof final String uuid) {
            return uuid;
        }
        if (result.get("saved") instanceof final Map<?, ?> saved && saved.get("uuid") instanceof final String uuid) {
            return uuid;
        }
        throw new AssertionError("No uuid in " + result);
    }

    /// @param value Any result.
    /// @return It as JSON, to look for text in.
    static String jsonOf(final Object value) {
        return JsonUtil.writeValueAsString(value, false);
    }

    /// @param groups A stepping verdict's groups.
    /// @return Each group's class and reason, to say why a verdict isn't the one expected.
    static List<String> said(final List<Map<String, Object>> groups) {
        return groups.stream().map(group -> group.get("class") + ": " + group.get("reason")).toList();
    }

    /// Logs a result whole, for reading a failure.
    ///
    /// @param step   What it is.
    /// @param result The result.
    static void dump(final String step, final Object result) {
        LOGGER.info(() -> LogUtil.message("{}: {}", step, JsonUtil.writeValueAsString(result, true)));
    }

    /// @param name The name of a text resource next to this class (a fixture shared by the suites).
    /// @return Its text.
    static String textResource(final String name) {
        try (final InputStream in = AbstractE2eTest.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("No test resource " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// @param name The name of a JSON resource next to this class.
    /// @return It, read.
    static Map<String, Object> jsonResource(final String name) {
        return JsonUtil.readValue(textResource(name), Map.class);
    }

    // --------------------------------------------------------------------------------
    // XML

    /// @param xml XML text.
    /// @return It parsed, namespace aware.
    static Document parse(final String xml) {
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            final DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (final Exception e) {
            throw new AssertionError("Not XML: " + xml, e);
        }
    }

    /// @param parent An element.
    /// @param name   A local name.
    /// @return Its child elements of that name.
    static List<Element> children(final Element parent, final String name) {
        final List<Element> found = new ArrayList<>();
        final NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof final Element element && name.equals(element.getLocalName())) {
                found.add(element);
            }
        }
        return found;
    }

    /// @param parent An element.
    /// @param path   Local names below it, separated by /.
    /// @return The first element at that path, if there is one.
    static Optional<Element> at(final Element parent, final String path) {
        Element current = parent;
        for (final String name : path.split("/")) {
            final List<Element> next = children(current, name);
            if (next.isEmpty()) {
                return Optional.empty();
            }
            current = next.getFirst();
        }
        return Optional.of(current);
    }

    /// @param parent An element.
    /// @param path   Local names below it, separated by /.
    /// @return The text of the first element at that path, or null.
    static String textAt(final Element parent, final String path) {
        return at(parent, path).map(Node::getTextContent).orElse(null);
    }

    /// @param parent An element.
    /// @param name   A local name.
    /// @return Every element of that name below it.
    static List<Element> descendants(final Element parent, final String name) {
        final List<Element> found = new ArrayList<>();
        final NodeList nodes = parent.getElementsByTagNameNS("*", name);
        for (int i = 0; i < nodes.getLength(); i++) {
            found.add((Element) nodes.item(i));
        }
        return found;
    }

    /// An element as text with whitespace-only text left out, to compare two XSLTs' output.
    ///
    /// @param element The element.
    /// @return Its text.
    static String canonical(final Element element) {
        final Element copy = (Element) element.cloneNode(true);
        stripWhitespace(copy);
        try {
            final Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            final StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(copy), new StreamResult(writer));
            return writer.toString();
        } catch (final Exception e) {
            throw new AssertionError(e);
        }
    }

    // --------------------------------------------------------------------------------
    // Private

    private String stepOutput(final String pipeline,
                              final long stream,
                              final int record,
                              final Integer part,
                              final String element) {
        final Map<String, Object> step = json("step_pipeline", args("pipeline_uuid", pipeline, "stream_id", stream,
                "record", record, "part", part));
        return String.valueOf(map(map(step, "elements"), element).get("output"));
    }

    // Filters made disabled are the user's to enable in Stroom: enabled through Stroom's API, as them.
    private void enableIfDisabled(final Map<String, Object> result) {
        if (result.get("filter_id") != null && Boolean.FALSE.equals(result.get("enabled"))) {
            final int filterId = (int) number(result.get("filter_id"));
            asUser(admin, () -> processorFilterService.setEnabled(filterId, true));
            LOGGER.info("Enabled filter {}, as the user would in Stroom", filterId);
        }
    }

    // What keeps an agent working unprompted: a reply's next.call names a tool the session is offered, and, where
    // that call only reads or steps (and its arguments are complete, no <placeholder>), making it as given is not
    // refused.
    private void leadsOn(final String tool, final Map<String, Object> result) {
        if (!(result.get("next") instanceof final Map<?, ?> next)
            || !(next.get("call") instanceof final Map<?, ?> call)) {
            return;
        }
        final String nextTool = String.valueOf(call.get("tool"));
        final Map<String, Object> arguments = call.get("arguments") instanceof final Map<?, ?> given
                ? (Map<String, Object>) given
                : Map.of();
        assertThat(client.toolNames()).as("%s's next names a tool the session has: %s", tool, next)
                .contains(nextTool);
        if (FOLLOWED.contains(nextTool) && !jsonOf(arguments).contains("<")) {
            final CallToolResult followed = client.call(nextTool, arguments);
            assertThat(followed.isError()).as("following %s's next, %s %s, leads on: %s", tool, nextTool,
                    arguments, McpTestClient.text(followed)).isNotEqualTo(true);
        }
    }

    private static void stripWhitespace(final Node node) {
        final NodeList nodes = node.getChildNodes();
        for (int i = nodes.getLength() - 1; i >= 0; i--) {
            final Node child = nodes.item(i);
            if (child.getNodeType() == Node.TEXT_NODE && child.getTextContent().isBlank()) {
                node.removeChild(child);
            } else {
                stripWhitespace(child);
            }
        }
    }

    private static String stripped(final String text, final char c) {
        int start = 0;
        int end = text.length();
        while (start < end && text.charAt(start) == c) {
            start++;
        }
        while (end > start && text.charAt(end - 1) == c) {
            end--;
        }
        return text.substring(start, end);
    }


    // --------------------------------------------------------------------------------


    /// A sample to onboard.
    ///
    /// @param format    The case's name (`csv`, `json`, ...), in the build's and feed's names; `json` tells the
    ///                  parser not to add a root object.
    /// @param template  The translation template the case uses.
    /// @param sample    The sample.
    /// @param converter The Data Splitter, or null for none.
    /// @param xslt      The hand-written XSLT.
    record Case(String format, String template, String sample, String converter, String xslt) {

    }

    /// What [#onboard] made: build, feed, raw stream, pipeline, XSLT and doc.
    ///
    /// @param build        The build.
    /// @param feed         The feed's name.
    /// @param raw          The sample's raw stream.
    /// @param pipelineUuid The translation pipeline.
    /// @param pipelineName Its name.
    /// @param xsltUuid     Its XSLT.
    /// @param docUuid      Its Documentation doc.
    /// @param events       The Events stream processing made of the raw stream.
    record Onboarded(String build,
                     String feed,
                     long raw,
                     String pipelineUuid,
                     String pipelineName,
                     String xsltUuid,
                     String docUuid,
                     long events) {

    }
}
