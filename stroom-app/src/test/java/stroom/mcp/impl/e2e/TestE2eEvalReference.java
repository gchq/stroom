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

import stroom.mcp.impl.McpTerminal;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/// The Stroom MCP server's evaluation set run with its reference solutions (`dev/eval/run_eval.py --reference`),
/// ported: for each of its 53 cases (`eval/*.yaml`, [EvalCases]), the case's own converter or Data Splitter and
/// field mapping go through the REAL tools on a real Stroom the way an agent would, with no model: the feed and
/// sample (or the files on the user's disk, sent by their upload command, [McpTerminal]), the Data Splitter,
/// build_translation_xslt, the pipeline from the case's template, stepping, processing, each event validated and
/// scored against what the case expects ([EvalScore]), the documentation's Field mapping, then a Lucene index,
/// its pipeline, processing and the verification searches.
///
/// A case passes when its Events hold what it expects and they are indexed and found, as `run_eval.py` has it, and,
/// stricter, when nothing else was found wrong on the way (`run_eval.py` reports those problems but passes the
/// case). Each case's score is written to `build/eval-reference/<case>.json`. All 53 take about 20 minutes;
/// `STROOM_EVAL_CASES=12,35` runs only those.
///
/// Not ported: `set_index_fields` after `create_index_doc` (the Java port's `create_index_doc` adds the plan
/// drafted for the index in the session, and has no such tool), and the processor filters' deletion after each
/// case (each case here starts from an empty Stroom).
@SuppressWarnings("unchecked")
class TestE2eEvalReference extends AbstractE2eTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestE2eEvalReference.class);

    // As run_eval.py's DOC_SKELETON.
    private static final String DOC_SKELETON = """
            ## Purpose and data

            Evaluation case.

            ## Processing

            Child of the template.

            ## Output

            See Field mapping.
            """;
    // Events read and checked a stream; a larger stream's are read in blocks of BLOCK, spread over it.
    private static final int READ_EVENTS = 300;
    private static final int BLOCK = 50;
    private static final Pattern SAMPLED_VALUES = Pattern.compile("Sample values are what the [1-9]\\d* documents");
    private static final Path RESULTS = Path.of("build", "eval-reference");

    @Inject
    private Injector injector;

    // As e2e.agreed, which run_eval.py's calls go through: a filter made disabled (a reference pipeline's) is
    // enabled, as the user would in Stroom.
    @Override
    boolean enablesDisabledFilters() {
        return true;
    }

    // All of them, or those STROOM_EVAL_CASES names (their numbers, comma-separated, e.g. 12,35), as run_eval.py
    // takes them.
    static List<String> cases() {
        final String only = System.getenv("STROOM_EVAL_CASES");
        if (only == null || only.isBlank()) {
            return EvalCases.IDS;
        }
        final List<String> numbers = List.of(only.split("\\s*,\\s*"));
        return EvalCases.IDS.stream().filter(id -> numbers.contains(id.split("_", 2)[0])).toList();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void theReferenceSolutionReachesIndexedEvents(final String id) {
        final Map<String, Object> evalCase = EvalCases.load(id);
        final EvalScore score = new EvalScore(id);
        final long startMs = System.currentTimeMillis();
        try {
            runReference(evalCase, score);
        } catch (final AssertionError | RuntimeException e) {
            // A step failing is the case's problem, as run_eval.py has it.
            LOGGER.info(() -> id + " failed at a step", e);
            score.problem(e.getClass().getSimpleName() + ": " + firstLines(e.getMessage()));
        }
        score.note("ran in " + (System.currentTimeMillis() - startMs) / 1000 + " s");
        write(id, score);
        LOGGER.info(score::summary);
        assertThat(score.passed()).as(score.summary()).isTrue();
    }

    // run_eval.py's run_reference.
    private void runReference(final Map<String, Object> evalCase, final EvalScore score) {
        final String tag = ((String) evalCase.get("id")).split("_", 2)[0];
        final String build = "eval-" + tag;
        final String feed = "EVAL-" + tag;
        final Map<String, Object> reference = (Map<String, Object>) evalCase.get("reference");
        final List<String> samples = EvalCases.samples(evalCase);
        // The server's own Data Splitter (build_data_splitter, inferred or from a spec) when the case gives no
        // converter of its own: saved in the build, where create_pipeline finds it for the parser.
        final boolean byServer = reference.get("splitter") != null && reference.get("converter") == null;
        Object splitter = reference.get("splitter") != null && !byServer
                ? reference.get("splitter")
                : null;
        agreed("create_feed", args("build", build, "name", feed, "encoding",
                reference.getOrDefault("feed_encoding", "UTF-8")));
        final List<Long> raws = new ArrayList<>();
        final List<EvalCases.SampleFile> files = EvalCases.files(evalCase);
        if (!files.isEmpty()) {
            // Files on the user's disk go whole, as the upload command sends them: their bytes, not text.
            for (final EvalCases.SampleFile file : files) {
                raws.add(uploadFile(feed, file));
            }
        } else {
            for (final String sample : samples) {
                raws.add(upload(feed, sample));
            }
        }
        if (byServer) {
            final Object spec = "infer".equals(reference.get("splitter"))
                    ? null
                    : reference.get("splitter");
            final Map<String, Object> splitterMade = json("build_data_splitter", args("stream_ids", raws,
                    "spec", spec, "save_as", feed, "build", build));
            if (number(splitterMade.getOrDefault("unmatched_count", 0)) != 0 || splitterMade.get("saved") == null) {
                throw new AssertionError("the server's Data Splitter: " + splitterMade.get("unmatched_count")
                                         + " unmatched, saved " + (splitterMade.get("saved") != null) + ": "
                                         + firstLines(String.valueOf(splitterMade.getOrDefault("hint",
                        splitterMade.get("notes")))));
            }
            splitter = splitterMade.get("spec");
        }
        final Map<String, Object> generated = agreed("build_translation_xslt", args("mapping",
                reference.get("mapping"), "stream_ids", raws, "splitter", splitter, "build", build,
                "name", feed + "-Events"));
        if (!Boolean.TRUE.equals(generated.get("ok"))) {
            throw new AssertionError("mapping problems: " + generated.get("problems"));
        }
        if (generated.get("sample_check") instanceof final Map<?, ?> check && check.get("warnings") != null
            && !((List<?>) check.get("warnings")).isEmpty()) {
            score.note("sample check: " + check.get("warnings"));
        }
        final String template = templateUuid("translation", (String) evalCase.get("template"));
        final List<Map<String, Object>> references = new ArrayList<>();
        if (reference.get("reference_data") instanceof final Map<?, ?> referenceData) {
            references.add(Map.of("feed", referenceData(build, feed, (Map<String, Object>) referenceData, score)));
        }
        final List<Map<String, Object>> properties = new ArrayList<>();
        final String converter = (String) reference.get("converter");
        final String replaceParser = (String) reference.get("replace_parser");
        if (converter != null) {
            String code = "csv_header".equals(converter)
                    ? CSV_SPLITTER
                    : converter;
            if ("profile".equals(converter)) {
                // The server's own: the text converter profile_sample gives for the sample.
                code = (String) map(json("profile_sample", args("sample", samples.getFirst())), "text_converter")
                        .get("code");
            }
            final String converterUuid = uuidOf(agreed("save_text_converter", args("build", build, "name", feed,
                    "converter_type", reference.getOrDefault("converter_type", "DATA_SPLITTER"), "code", code)));
            final String parser = replaceParser != null
                    ? elementId(replaceParser)
                    : "dsParser";
            properties.add(docProperty(parser, "textConverter", converterUuid, "TextConverter"));
        }
        properties.add(docProperty("translationFilter", "xslt", uuidOf(map(generated, "saved")), "XSLT"));
        if ("Event Data (JSON)".equals(evalCase.get("template"))) {
            // JSON lines need the parser's root map round the top-level objects; an array reads better without it.
            final boolean lines = "lines".equals(((Map<String, Object>) reference.get("mapping")).get("json_layout"));
            properties.add(valueProperty("jsonParser", "addRootObject", lines));
        }
        final String pipeline = uuidOf(agreed("create_pipeline", args("build", build, "name", feed + "-Events",
                "template_uuid", template, "set_properties", properties, "replace_parser", replaceParser,
                "references", references.isEmpty()
                        ? null
                        : references)));
        final Map<String, Object> stepped = stepSample(pipeline, raws);
        if ("blocking".equals(stepped.get("verdict"))) {
            score.problem("stepping blocking: " + firstLines(String.valueOf(stepped.get("groups"))));
        }
        final Map<String, Object> gate = process(pipeline, raws);
        final List<Long> events = new ArrayList<>();
        for (final Map<String, Object> stream : list(gate, "streams")) {
            ((List<?>) stream.getOrDefault("events", List.of())).forEach(id -> events.add(number(id)));
        }
        checkOutput(evalCase, score, events);
        // The documentation: its Field mapping section is generated from the kept mapping over the sample streams.
        final Map<String, Object> written = agreed("write_documentation", args("build", build,
                "pipeline_uuid", pipeline, "markdown", DOC_SKELETON, "change", "Created", "stream_ids", raws));
        final String section = String.valueOf(written.getOrDefault("field_mapping", ""));
        if (!section.contains("### Event types") || section.contains("not written by a rule")) {
            score.problem("documentation: field mapping section incomplete ("
                          + section.substring(0, Math.min(120, section.length())) + ")");
        }
        final List<String> checks = strings(json("build_status", args("build", build)), "before_promotion");
        if (checks.stream().anyMatch(check -> check.contains("Field mapping") || check.contains("differs"))) {
            score.problem("documentation: build checks report a stale field mapping or a hand-edited XSLT: "
                          + checks);
        }
        if (!score.stage1()) {
            return;
        }
        index(build, feed, pipeline, events, score);
    }

    // The user directory: a Raw Reference feed, a Reference Data pipeline, processed to Reference streams.
    private String referenceData(final String build,
                                 final String feed,
                                 final Map<String, Object> referenceData,
                                 final EvalScore score) {
        final String referenceFeed = feed + "-USERS";
        agreed("create_feed", args("build", build, "name", referenceFeed, "stream_type", "Raw Reference"));
        // Reference data applies from its effective time: before the events' streams, or lookups find nothing.
        final long raw = number(json("upload_sample", args("feed", referenceFeed, "sample",
                referenceData.get("sample"), "stream_type", "Raw Reference",
                "effective_time", "2000-01-01T00:00:00.000Z")).get("stream_id"));
        final Map<String, Object> code = json("build_reference_xslt", args("mapping", referenceData.get("mapping")));
        if (!Boolean.TRUE.equals(code.get("ok"))) {
            throw new AssertionError("reference mapping problems: " + code.get("problems"));
        }
        final String template = templateUuid("reference", "Reference Data");
        final List<Map<String, Object>> properties = new ArrayList<>();
        final String converter = "csv_header".equals(referenceData.get("converter"))
                ? CSV_SPLITTER
                : (String) referenceData.get("converter");
        if (converter != null) {
            properties.add(docProperty("combinedParser", "textConverter", uuidOf(agreed("save_text_converter",
                    args("build", build, "name", referenceFeed, "converter_type", "DATA_SPLITTER", "code",
                            converter))), "TextConverter"));
        }
        properties.add(docProperty("translationFilter", "xslt", saveXslt(build, referenceFeed + "-Reference",
                (String) code.get("xslt")), "XSLT"));
        final String pipeline = uuidOf(agreed("create_pipeline", args("build", build,
                "name", referenceFeed + "-Reference", "template_uuid", template, "set_properties", properties)));
        final Map<String, Object> stepped = stepSample(pipeline, List.of(raw));
        if (!"clean".equals(stepped.get("verdict"))) {
            score.problem("reference pipeline stepping: " + stepped.get("verdict"));
        }
        agreed("create_processor_filter", args("pipeline_uuid", pipeline, "stream_ids", List.of(raw)));
        processAll();
        final Map<String, Object> gate = json("wait_for_processing", args("pipeline_uuid", pipeline,
                "stream_ids", List.of(raw), "output_type", "Reference", "timeout_seconds", 10));
        if (!"pass".equals(gate.get("gate"))) {
            score.problem("reference data: " + gate.getOrDefault("problems", gate));
        }
        return referenceFeed;
    }

    // The events in a Lucene index, through its pipeline, found by the verification searches.
    private void index(final String build,
                       final String feed,
                       final String eventsPipeline,
                       final List<Long> events,
                       final EvalScore score) {
        final String indexName = feed + "-INDEX";
        final Map<String, Object> plan = map(json("draft_index_mapping", args("backend", "lucene",
                "index_name", indexName, "convention", "stroom-flat", "events_stream_ids", events)), "plan");
        final String index = uuidOf(agreed("create_index_doc", args("build", build, "backend", "lucene",
                "name", indexName, "time_field", plan.get("time_field"))));
        // Generated from the plan.
        final String xslt = uuidOf(agreed("save_xslt", args("build", build, "name", indexName + "-XSLT",
                "index_plan", plan)));
        final String template = (String) templates("indexing").stream()
                .filter(candidate -> "lucene".equals(candidate.get("backend")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Lucene indexing template"))
                .get("uuid");
        final String pipeline = uuidOf(agreed("create_indexing_pipeline", args("build", build,
                "name", indexName + " - Indexing", "template_uuid", template, "xslt_uuid", xslt,
                "index_uuid", index)));
        final Map<String, Object> stepped = stepSample(pipeline, events);
        if (!"clean".equals(stepped.get("verdict"))) {
            score.problem("indexing pipeline stepping: " + stepped.get("verdict"));
        }
        agreed("create_processor_filter", args("pipeline_uuid", pipeline, "stream_ids", events,
                "source_pipeline_uuid", eventsPipeline));
        processAll();
        flushIndexShards();
        final Map<String, Object> gate = json("wait_for_processing", args("pipeline_uuid", pipeline,
                "stream_ids", events, "expect_events", false, "timeout_seconds", 10));
        final Map<String, Object> verified = agreed("verify_index", args("build", build, "index_uuid", index,
                "backend", "lucene", "stream_ids", events, "expected_documents", score.events(),
                "fields", List.of("StreamId", "EventId", plan.get("time_field"))));
        final boolean indexed = "pass".equals(gate.get("gate")) && Boolean.TRUE.equals(verified.get("passed"));
        score.indexed(indexed);
        final Map<String, Object> documented = agreed("write_documentation", args("build", build,
                "pipeline_uuid", pipeline, "markdown", DOC_SKELETON, "change", "Created", "stream_ids", events));
        final String section = String.valueOf(documented.getOrDefault("field_mapping", ""));
        if (!section.contains("| Index field |")) {
            score.problem("documentation: no index field mapping generated");
        } else if (!SAMPLED_VALUES.matcher(section).find()) {
            score.problem("documentation: the index field mapping has no sampled values");
        }
        if (!indexed) {
            score.problem("indexing: gate " + gate.get("gate") + ", searches " + verified.get("checks"));
        }
    }

    // run_eval.py's check_output: every event of a case's sample, up to READ_EVENTS a stream, a larger stream's in
    // blocks spread over it, each validated.
    private void checkOutput(final Map<String, Object> evalCase, final EvalScore score, final List<Long> streams) {
        final List<String> records = new ArrayList<>();
        final List<Boolean> validity = new ArrayList<>();
        int totals = 0;
        boolean cut = false;
        for (final long stream : streams) {
            final Read first = readEvents(stream, 0, READ_EVENTS);
            List<String> got = first.records();
            final int total = first.total() != null
                    ? first.total()
                    : got.size();
            if (total > READ_EVENTS) {
                final int blocks = READ_EVENTS / BLOCK;
                got = new ArrayList<>(got.subList(0, Math.min(BLOCK, got.size())));
                for (int i = 1; i < blocks; i++) {
                    // Python's round(), half to even.
                    final int from = (int) Math.rint(i * (total - BLOCK) / (double) (blocks - 1));
                    got.addAll(readEvents(stream, from, BLOCK).records());
                }
            }
            totals += total;
            cut = cut || total > got.size();
            for (final String record : got) {
                records.add(record);
                final Map<String, Object> checked = json("check_events", args("events_xml", record));
                validity.add(checked.get("schema") instanceof final Map<?, ?> schema
                             && Boolean.TRUE.equals(schema.get("valid")));
            }
        }
        score.scoreEvents(evalCase, records, validity, cut
                ? totals
                : null);
    }

    // Up to count events from first, reading on past the size limit; and how many the stream holds.
    private Read readEvents(final long stream, final int first, final int count) {
        final List<String> got = new ArrayList<>();
        Integer total = null;
        int from = first;
        while (got.size() < count) {
            final Map<String, Object> read = json("fetch_events", args("streamId", stream, "firstRecord", from,
                    "recordCount", Math.min(100, count - got.size()), "maxChars", 200_000));
            final List<String> records = strings(read, "records");
            got.addAll(records);
            if (read.get("total_records") instanceof final Number number) {
                total = number.intValue();
            }
            from = read.get("next_first_record") instanceof final Number next
                    ? next.intValue()
                    : from + records.size();
            if (records.isEmpty() || (total != null && from >= total)) {
                break;
            }
        }
        return new Read(got, total);
    }

    // As the user's terminal runs the command upload_sample files= gives for the file.
    private long uploadFile(final String feed, final EvalCases.SampleFile file) {
        final String path = "/home/user/" + file.name();
        final Map<String, Object> given = json("upload_sample", args("feed", feed, "files", List.of(path)));
        final Map<String, Object> command = list(given, "commands").getFirst();
        final McpTerminal.Posted posted = McpTerminal.run(injector, admin, (String) command.get("bash"), path,
                file.data());
        assertThat(posted.status()).as("%s reached Stroom: %s", file.name(), posted.body()).isEqualTo(200);
        return number(posted.body().get("stream_id"));
    }

    // pipeline_writes.element_id: XMLFragmentParser is xmlFragmentParser, DSParser dsParser (a leading acronym is
    // lowered whole).
    private static String elementId(final String elementType) {
        final String lowered = Pattern.compile("^[A-Z]+(?=[A-Z][a-z])").matcher(elementType)
                .replaceFirst(match -> match.group().toLowerCase());
        return Character.toLowerCase(lowered.charAt(0)) + lowered.substring(1);
    }

    private static String firstLines(final String text) {
        final String value = String.valueOf(text);
        return value.length() > 1_500
                ? value.substring(0, 1_500) + "…"
                : value;
    }

    private static void write(final String id, final EvalScore score) {
        try {
            Files.createDirectories(RESULTS);
            Files.writeString(RESULTS.resolve(id + ".json"), JsonUtil.writeValueAsString(
                    new LinkedHashMap<>(score.toMap()), true));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record Read(List<String> records, Integer total) {

    }
}
