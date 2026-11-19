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

import stroom.ai.impl.tools.indexing.FeedIndexes;
import stroom.ai.impl.tools.sample.SourceTypeMeta;
import stroom.ai.impl.tools.sample.SourceTypeRegistry;
import stroom.ai.impl.tools.sample.SourceTypeScope;
import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.index.impl.IndexFieldService;
import stroom.index.shared.LuceneIndexDoc;
import stroom.mcp.impl.McpTerminal;
import stroom.mcp.impl.McpTestClient;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Status;
import stroom.query.api.datasource.Field;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.search.elastic.ElasticIndexStore;
import stroom.search.elastic.ElasticTestNode;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.json.JsonUtil;
import stroom.util.shared.PageRequest;

import com.google.inject.Injector;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import jakarta.inject.Inject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/// What the evaluation set's runs share: the cases to run, the check of a build's Events, and the cases' reference
/// solutions run through the REAL tools ([#runReference]), which also make the pre-state of a case that starts from
/// another case's build ([#setUp]).
///
/// A reference solution goes through the tools the way an agent would, with no model: the feed and sample (or the
/// files on the user's disk, sent by their upload command, [McpTerminal]), the Data Splitter,
/// build_translation_xslt, the pipeline from the case's template, stepping, processing, each event validated and
/// scored against what the case expects ([EvalScore]), the documentation's Field mapping, then a Lucene index, its
/// pipeline, processing and the verification searches.
///
/// An aggregated feed's case (its reference's `source_types`) is one build of one source type group: the feed surveyed
/// (with its source type field named, unless a registry beside the feed says it), the registry written by
/// `record_source_types` (the case's rows, if any, and the group's source types and name, as the user picks them in
/// the form), the group's XSLT and its own pipeline (`group=`), which passes over every other source type's records;
/// its Events, registry and passed-over count are scored ([EvalScore#scoreSourceTypes]), and its index is its own. A
/// case whose data is in the feed already (`streams`, [EvalCases#streams]) has it stored there, a stream of parts each
/// with its receipt headers.
///
/// A case that starts from another (`setup: {from_case: 56}`, [EvalCases#setup]) has that case's reference solution
/// run first, on a feed promoted outside the build as a site's feeds are, and the build promoted with its layout
/// accepted: the case's own build then starts on the feed, its registry and the promoted group. Its reference names
/// the group it builds: a new group gets a pipeline and an index of its own; the promoted group, joined, is changed in
/// a new version of its pipeline (a promoted pipeline is never changed in place, decided by the user 2026-11-17), as
/// is its indexing pipeline, and its index gains the build's fields. A case with `promote: true` is promoted at the
/// end, and its registry scored beside the feed.
@SuppressWarnings("unchecked")
abstract class AbstractEvalTest extends AbstractE2eTest {

    /// Where a feed made outside the case's build goes when promoted, with the documents a setup build promotes.
    static final String FEEDS = "Eval Feeds";

    // Where a setup build (or a case that promotes) puts its documents.
    private static final Map<String, Object> DESTINATIONS = Map.of("Pipeline", FEEDS, "XSLT", FEEDS, "Index", FEEDS,
            "Documentation", FEEDS);
    // The documentation's skeleton.
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
    private static final Pattern ERROR_LINE = Pattern.compile("\\b(ERROR|FATAL)\\b");

    // Whether a reference build confirms its reference catalogue first ([#confirmCatalogue]), and at most how many
    // times it confirms a set of event types.
    private static final boolean CATALOGUES = Boolean.getBoolean("testEvalCatalogues");
    private static final int CATALOGUE_SETS = 40;

    // The streams a question's events are in.
    private static final int QUESTION_STREAMS = 10;

    // The forms the user answered in the case's own build: each its message and the answer's content.
    private final List<Map<String, Object>> formsAnswered = new ArrayList<>();

    @Inject
    private Injector injector;
    @Inject
    private IndexFieldService indexFieldService;
    @Inject
    private MetaService metaService;
    @Inject
    private ElasticIndexStore elasticIndexStore;

    // A filter made disabled (a reference pipeline's) is enabled, as the user would in Stroom.
    @Override
    boolean enablesDisabledFilters() {
        return true;
    }

    /// All the cases, or those `-DtestEvalCases` (or the `STROOM_EVAL_CASES` environment variable) names: their
    /// numbers, comma-separated, e.g. `12,35`.
    ///
    /// @return The cases' ids.
    static List<String> cases() {
        final String property = System.getProperty("testEvalCases");
        final String only = property != null
                ? property
                : System.getenv("STROOM_EVAL_CASES");
        if (only == null || only.isBlank()) {
            return EvalCases.IDS;
        }
        final List<String> numbers = List.of(only.strip().split("\\s*,\\s*"));
        return EvalCases.IDS.stream().filter(id -> numbers.contains(id.split("_", 2)[0])).toList();
    }

    /// The pre-state of a case that starts from another ([EvalCases#setup]): the feed made in a build of its own, that
    /// case's data uploaded to it, and the feed promoted to [#FEEDS]; that case's reference solution run on it in
    /// build `eval-<tag>-setup` (its group built, processed and indexed), and the build promoted with its layout
    /// accepted. Deterministic: no model takes part. Fails if that case's reference doesn't pass.
    ///
    /// @param evalCase The case.
    /// @param tag      The case's tag, for the setup build's name.
    /// @param feed     The feed's name, which the case's own build then uses.
    /// @return What the setup left, or null for a case that starts from an empty Stroom.
    Setup setUp(final Map<String, Object> evalCase, final String tag, final String feed) {
        final Map<String, Object> from = EvalCases.setup(evalCase);
        if (from == null) {
            return null;
        }
        final String build = "eval-" + tag + "-setup";
        final Map<String, Object> reference = (Map<String, Object>) from.get("reference");
        agreed("create_feed", args("build", build + "-feed", "name", feed, "encoding",
                reference.getOrDefault("feed_encoding", "UTF-8")));
        // A feed's data arrives before its promotion: a promoted feed takes no uploads from the tools.
        final List<Long> raws = uploadData(from, feed, EvalCases.samples(from));
        promote(build + "-feed", Map.of("Feed", FEEDS));
        final EvalScore score = new EvalScore((String) from.get("id"));
        final Built built = build(from, score, build, feed, null, raws);
        if (!promotes(from)) {
            promote(build, DESTINATIONS);
        }
        if (!score.passed()) {
            throw new AssertionError("The setup, case " + from.get("id") + "'s reference, failed: " + score.summary());
        }
        if (built.indexed() == null) {
            throw new AssertionError("The setup, case " + from.get("id") + ", made no index");
        }
        // The forms of the case's own build only.
        formsAnswered.clear();
        return new Setup(feed, build, built, new EvalScore.Before(built.group(), built.pipeline(),
                built.indexed().index(), snapshot(built)));
    }

    /// A case run with its reference solution, on its setup's feed and promoted documents where it has one.
    ///
    /// @param evalCase The case.
    /// @param score    Its score.
    /// @param build    The build's name.
    /// @param feed     The feed's name: the setup's, for a case with one.
    /// @param setup    What the case's setup left ([#setUp]), or null for none.
    void runReference(final Map<String, Object> evalCase,
                      final EvalScore score,
                      final String build,
                      final String feed,
                      final Setup setup) {
        build(evalCase, score, build, feed, setup, null);
    }

    /// A question's data, indexed before the user asks (`elastic_index`): its events ([EvalProxyUploads]) in an
    /// Elasticsearch index of their own, in streams in Stroom, behind an Elastic Index doc of the case's name beside
    /// the standard templates. Skips the case without an Elasticsearch node ([ElasticTestNode#getOrSkip]).
    ///
    /// @param evalCase The case.
    /// @return The Elastic Index doc, and the index to remove once the case is done.
    QuestionIndex questionIndex(final Map<String, Object> evalCase) {
        final ElasticTestNode node = ElasticTestNode.getOrSkip();
        final Map<String, Object> spec = (Map<String, Object>) evalCase.get("elastic_index");
        final String indexName = ElasticTestNode.createIndexName("eval-" + String.valueOf(evalCase.get("id"))
                .split("_", 2)[0]);
        final List<Long> streams = new ArrayList<>();
        asUser(admin, () -> {
            for (int i = 0; i < QUESTION_STREAMS; i++) {
                streams.add(metaService.create(MetaProperties.builder()
                        .feedName("EVAL-PROXY-" + indexName.toUpperCase(Locale.ROOT))
                        .typeName(StreamTypeNames.EVENTS)
                        .createMs(System.currentTimeMillis())
                        .build(), Status.UNLOCKED).getId());
            }
        });
        EvalProxyUploads.seed(node, indexName, streams);
        final DocRef cluster = liveElasticCluster(node, "Eval cluster " + indexName);
        final ExplorerNode created = create(ElasticIndexDoc.TYPE, String.valueOf(spec.get("name")),
                templateFolder());
        asUser(admin, () -> elasticIndexStore.writeDocument(elasticIndexStore.readDocument(created.getDocRef())
                .copy()
                .clusterRef(cluster)
                .indexName(indexName)
                .timeField("@timestamp")
                .build()));
        return new QuestionIndex(node, indexName, created.getDocRef());
    }

    /// A question's reference answer: its `reference.esql_query` (`pipeline`, and any `timeFrom`, `timeTo`,
    /// `where` and `limit`) run with esql_query on the question's index, and scored as an agent's answer is
    /// ([EvalScore#scoreQuestion]).
    ///
    /// @param evalCase The case.
    /// @param score    Its score.
    void runQuestionReference(final Map<String, Object> evalCase, final EvalScore score) {
        final QuestionIndex index = questionIndex(evalCase);
        try {
            final Map<String, Object> reference = (Map<String, Object>) ((Map<String, Object>) evalCase
                    .get("reference")).get("esql_query");
            final Map<String, Object> arguments = new LinkedHashMap<>(reference);
            arguments.put("index", index.doc().getUuid());
            final CallToolResult result = client.call("esql_query", arguments);
            final String text = McpTestClient.text(result);
            final boolean refused = Boolean.TRUE.equals(result.isError()) || text.startsWith("Error:");
            final Map<String, Object> json = !refused && result.structuredContent() instanceof final Map<?, ?> map
                    ? (Map<String, Object>) map
                    : Map.of();
            dump("esql_query", json);
            score.scoreQuestion(evalCase, List.of(new EvalScore.ToolUse("esql_query", refused, json, text)));
        } finally {
            index.node().deleteIndices(index.indexName());
        }
    }

    /// The setup group's pipeline, XSLT and index, as they are now: each as text, to compare with what the setup
    /// left.
    ///
    /// @param built What the setup's build made.
    /// @return Each document's content, by name.
    Map<String, String> snapshot(final Built built) {
        final Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("pipeline", jsonOf(List.of(added(built.pipeline(), "elements"), added(built.pipeline(),
                "links"), added(built.pipeline(), "properties"))));
        snapshot.put("XSLT", readXslt(built.xslt()).getData());
        snapshot.put("index", String.join(",", luceneFields(new DocRef(LuceneIndexDoc.TYPE,
                built.indexed().index()))));
        return snapshot;
    }

    /// The registry beside a feed promoted to [#FEEDS].
    ///
    /// @param feed The feed.
    /// @return The registry, as [SourceTypeRegistry#read] reads it, or null if there is none.
    Map<String, Object> registryBesideFeed(final String feed) {
        final List<ExplorerNode> found = documentsIn("System/" + FEEDS, "Documentation",
                feed + SourceTypeRegistry.SUFFIX);
        return found.isEmpty()
                ? null
                : SourceTypeRegistry.read(documentation(found.getFirst().getUuid()));
    }

    /// The folder a source type group's pipeline is in, as the registry's rows name it: where promotion put it.
    ///
    /// @param registry The registry, as [SourceTypeRegistry#read] reads it, or null.
    /// @param group    The group's name (any case), or null.
    /// @return The explorer path of the pipeline's folder, or null if no row of the group names a pipeline.
    String groupPipelineFolder(final Map<String, Object> registry, final String group) {
        if (registry == null || group == null) {
            return null;
        }
        return ((List<Map<String, Object>>) registry.getOrDefault("rows", List.of())).stream()
                .filter(row -> group.equalsIgnoreCase(String.valueOf(row.get("group"))))
                .map(row -> row.get("pipeline") instanceof final Map<?, ?> pipeline
                        ? (String) pipeline.get("uuid")
                        : null)
                .filter(Objects::nonNull)
                .findFirst()
                .map(this::folderOf)
                .orElse(null);
    }

    /// The name of a case's source type group: the one it expects (`expected.source_types.group.name`), else its
    /// reference's.
    ///
    /// @param evalCase A case.
    /// @return The group's name, or null for a case with none.
    static String groupOf(final Map<String, Object> evalCase) {
        final Map<String, Object> expected = evalCase.get("expected") instanceof final Map<?, ?> given
                ? (Map<String, Object>) given
                : Map.of();
        if (expected.get("source_types") instanceof final Map<?, ?> sourceTypes
            && sourceTypes.get("group") instanceof final Map<?, ?> group
            && group.get("name") instanceof final String name) {
            return name;
        }
        return evalCase.get("reference") instanceof final Map<?, ?> reference
               && reference.get("source_types") instanceof final Map<?, ?> sourceTypes
               && sourceTypes.get("group") instanceof final String name
                ? name
                : null;
    }

    /// The options a form's fields offer, as the user sees them: each field's choices (`enum`, or titled `oneOf` or
    /// `anyOf` choices, or its items' for a multi-choice field) and `examples`.
    ///
    /// @param schema The form's requested schema.
    /// @return The options, in the fields' order.
    static List<String> formOptions(final Map<String, Object> schema) {
        final List<String> options = new ArrayList<>();
        if (schema != null && schema.get("properties") instanceof final Map<?, ?> fields) {
            for (final Object field : fields.values()) {
                if (field instanceof final Map<?, ?> definition) {
                    options.addAll(McpTestClient.options(definition));
                    for (final String key : List.of("enum", "oneOf", "anyOf", "examples")) {
                        if (definition.get(key) instanceof final List<?> list) {
                            for (final Object option : list) {
                                options.add(String.valueOf(option instanceof final Map<?, ?> titled
                                        ? titled.get("const")
                                        : option));
                            }
                        }
                    }
                }
            }
        }
        return options;
    }

    /// @param evalCase A case.
    /// @return Whether its build is promoted at the end (`promote: true`).
    static boolean promotes(final Map<String, Object> evalCase) {
        return Boolean.TRUE.equals(evalCase.get("promote"));
    }

    /// The forms the user answered in the case's own build (since the setup, for a case with one).
    ///
    /// @return Each form's `message` and the `content` it was answered with.
    List<Map<String, Object>> formsAnswered() {
        return List.copyOf(formsAnswered);
    }

    /// Every event of a case's sample, up to [#READ_EVENTS] a stream, a larger stream's in blocks spread over it,
    /// each validated, and scored.
    ///
    /// @param evalCase The case.
    /// @param score    Its score.
    /// @param streams  The build's Events streams.
    void checkOutput(final Map<String, Object> evalCase, final EvalScore score, final List<Long> streams) {
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
                    // Rounded half to even.
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

    /// @param text Some text.
    /// @return Its first 1,500 characters.
    static String firstLines(final String text) {
        final String value = String.valueOf(text);
        return value.length() > 1_500
                ? value.substring(0, 1_500) + "…"
                : value;
    }

    /// Writes a case's result as JSON.
    ///
    /// @param dir    Where.
    /// @param id     The case.
    /// @param result Its result.
    static void write(final Path dir, final String id, final Map<String, Object> result) {
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(id + ".json"), JsonUtil.writeValueAsString(new LinkedHashMap<>(result),
                    true));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // A case's reference build: on a feed made in the build, its data uploaded; or on a feed promoted already that the
    // build starts on, its data there already (stored, the setup's own build; or the setup's, for a case on top).
    private Built build(final Map<String, Object> evalCase,
                        final EvalScore score,
                        final String build,
                        final String feed,
                        final Setup setup,
                        final List<Long> stored) {
        final Map<String, Object> reference = (Map<String, Object>) evalCase.get("reference");
        final List<String> samples = EvalCases.samples(evalCase);
        // The reference solutions leave a user's Id whole, so the user keeps the Ids as they are when the
        // translation asks; seen: case 30's alice@example.org split to alice.
        answering(choosing("Keep as is"));
        // The server's own Data Splitter (build_data_splitter, inferred or from a spec) when the case gives no
        // converter of its own: saved in the build, where create_pipeline finds it for the parser.
        final boolean byServer = reference.get("splitter") != null && reference.get("converter") == null;
        Object splitter = reference.get("splitter") != null && !byServer
                ? reference.get("splitter")
                : null;
        final List<Long> raws = new ArrayList<>();
        if (setup != null || stored != null) {
            json("start_build", args("build", build, "feeds", List.of(feed)));
            raws.addAll(setup != null
                    ? setup.built().raws()
                    : stored);
        } else {
            agreed("create_feed", args("build", build, "name", feed, "encoding",
                    reference.getOrDefault("feed_encoding", "UTF-8")));
            raws.addAll(uploadData(evalCase, feed, samples));
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
        // An aggregated feed's: the registry and the build's one source type group, whose XSLT and pipeline are the
        // group's.
        final Map<String, Object> sourceTypes = reference.get("source_types") instanceof final Map<?, ?> given
                ? (Map<String, Object>) given
                : null;
        final String group = sourceTypes != null
                ? (String) sourceTypes.get("group")
                : null;
        // The setup's group, joined: changed in a new version of its pipeline.
        final boolean joins = setup != null && group != null && group.equalsIgnoreCase(setup.built().group());
        if (sourceTypes != null) {
            recordSourceTypes(build, feed, sourceTypes);
        }
        if (CATALOGUES) {
            confirmCatalogue(evalCase, score, build, raws);
        }
        final Map<String, Object> translation = args("mapping", reference.get("mapping"), "stream_ids", raws,
                "splitter", splitter, "build", build);
        if (group != null) {
            // A later build's group and source types are its scope's (with those an existing group has already).
            translation.putAll(args("group", group, "source_types", setup == null
                    ? sourceTypes.get("source_types")
                    : null, "feeds", List.of(feed)));
        } else {
            translation.put("name", feed + "-Events");
        }
        final Map<String, Object> generated = agreed("build_translation_xslt", translation);
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
        if (group == null) {
            // A group's pipeline has its XSLT set by create_pipeline group=.
            properties.add(docProperty("translationFilter", "xslt", uuidOf(map(generated, "saved")), "XSLT"));
        }
        if ("Event Data (JSON)".equals(evalCase.get("template"))) {
            // JSON lines need the parser's root map round the top-level objects; an array reads better without it.
            final boolean lines = "lines".equals(((Map<String, Object>) reference.get("mapping")).get("json_layout"));
            properties.add(valueProperty("jsonParser", "addRootObject", lines));
        }
        final String pipeline;
        final String pipelineName;
        if (joins) {
            // The promoted group's pipeline, run in the build as its new version, under the names proposed.
            final Map<String, Object> copied = agreed("copy_pipeline", args("build", build, "source_uuid",
                    setup.built().pipeline(), "new_name", setup.built().pipelineName()));
            pipeline = uuidOf(copied);
            pipelineName = (String) copied.get("name");
        } else {
            // A group's pipeline is named for its group.
            final Map<String, Object> made = agreed("create_pipeline", args("build", build, "name", group == null
                            ? feed + "-Events"
                            : null, "group", group, "template_uuid", template, "set_properties", properties,
                    "replace_parser", replaceParser, "references", references.isEmpty()
                            ? null
                            : references));
            pipeline = uuidOf(made);
            pipelineName = (String) made.get("name");
        }
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
        final Map<String, Object> saved = group != null
                ? map(generated, "group")
                : Map.of();
        final Indexed indexed = score.stage1()
                ? index(build, feed, group, pipeline, events, score, setup)
                : null;
        if (promotes(evalCase)) {
            // A feed made in the build goes with it, beside a setup's.
            final Map<String, Object> destinations = new LinkedHashMap<>(DESTINATIONS);
            if (setup == null && stored == null) {
                destinations.put("Feed", FEEDS);
            }
            promote(build, destinations);
        }
        final Built built = new Built(List.copyOf(raws), group, pipeline, pipelineName, saved.get("xslt") != null
                ? uuidOf(map(saved, "xslt"))
                : null, indexed);
        if (sourceTypes != null) {
            final Map<String, Object> registry = promotes(evalCase)
                    ? registryBesideFeed(feed)
                    : saved.get("registry") != null
                            ? SourceTypeRegistry.read(documentation(uuidOf(map(saved, "registry"))))
                            : null;
            scoreSourceTypes(evalCase, score, registry, gate, group, raws);
            if (promotes(evalCase)) {
                score.scoreLayout(evalCase, groupPipelineFolder(registry, group));
            }
            if (setup != null) {
                final String indexUsed = indexed != null
                        ? indexed.index()
                        : null;
                score.scoreLaterBuild(evalCase, registry, setup.before(), snapshot(setup.built()), build, indexUsed);
            }
        }
        score.scoreForms(evalCase, formsAnswered());
        return built;
    }

    // TODO phase 3: catalogue_event_types confirms the case's reference catalogue before the translation, as an agent
    //  would (each event type proposed, the catalogue checked against the sample, then the sets and the catch-all
    //  confirmed by the user), and the build's catalogue is scored ([EvalScore#scoreCatalogue]) from the build's
    //  source notes, not the reference; then the switch goes and this runs for every build case. Until the tool exists
    //  it runs only with -DtestEvalCatalogues=true.
    private void confirmCatalogue(final Map<String, Object> evalCase,
                                  final EvalScore score,
                                  final String build,
                                  final List<Long> raws) {
        final Map<String, Object> catalogue = EvalCatalogues.reference(evalCase);
        final List<Map<String, Object>> proposals = new ArrayList<>();
        for (final Map<String, Object> type : (List<Map<String, Object>>) catalogue.get("event_types")) {
            final Map<String, Object> proposal = new LinkedHashMap<>(type);
            proposal.put("op", "add");
            proposals.add(proposal);
        }
        final Map<String, Object> checked = json("catalogue_event_types", args("build", build, "stream_ids", raws,
                "proposals", proposals));
        answering(choosing("Accept all"));
        Map<String, Object> confirmed = Map.of();
        for (int set = 0; set < CATALOGUE_SETS && !"confirmed".equals(confirmed.get("status")); set++) {
            confirmed = json("catalogue_event_types", args("build", build, "stream_ids", raws, "confirm", true,
                    "catch_all", Map.of("allow_unknown", "records no event type recognises")));
        }
        if (!"confirmed".equals(confirmed.get("status"))) {
            score.problem("catalogue_event_types confirmed nothing: " + firstLines(String.valueOf(confirmed)));
        }
        score.scoreCatalogue(evalCase, catalogue, map(checked, "check"));
    }

    // The case's data in the feed: its streams of parts stored as the proxy aggregated them, its files on the user's
    // disk sent whole by the upload command (their bytes, not text), or its samples uploaded.
    private List<Long> uploadData(final Map<String, Object> evalCase, final String feed, final List<String> samples) {
        final List<Long> raws = new ArrayList<>();
        final List<EvalCases.SampleFile> files = EvalCases.files(evalCase);
        final List<List<EvalCases.StreamPart>> streams = EvalCases.streams(evalCase);
        if (!streams.isEmpty()) {
            for (final List<EvalCases.StreamPart> parts : streams) {
                raws.add(storeParts(feed, feedParts(feed, parts)));
            }
        } else if (!files.isEmpty()) {
            for (final EvalCases.SampleFile file : files) {
                raws.add(uploadFile(feed, file));
            }
        } else {
            for (final String sample : samples) {
                raws.add(upload(feed, sample));
            }
        }
        return raws;
    }

    // The aggregated feed surveyed (its source type field named, unless the registry beside the feed says it), then
    // record_source_types: the table shown (with the case's rows, if any), and the registry written once the user
    // puts the group's source types in the group and names it, in the form.
    private void recordSourceTypes(final String build,
                                   final String feed,
                                   final Map<String, Object> sourceTypes) {
        json("survey_feed", args("feed", feed, "build", build, "source_type_field", sourceTypes.get("field")));
        // A later build's system and environment are the registry's.
        final String environment = sourceTypes.get("system_name") != null
                ? "Eval"
                : null;
        final Map<String, Object> shown = json("record_source_types", args("build", build, "feed", feed,
                "system_name", sourceTypes.get("system_name"), "environment", environment, "rows",
                sourceTypes.get("rows"), "patterns", sourceTypes.get("patterns")));
        if (!"needs_review".equals(shown.get("status"))) {
            throw new AssertionError("record_source_types showed no table: " + firstLines(String.valueOf(shown)));
        }
        answering(grouping(strings(sourceTypes, "source_types"), (String) sourceTypes.get("group")));
        final Map<String, Object> written = json("record_source_types", args("build", build, "feed", feed,
                "system_name", sourceTypes.get("system_name"), "environment", environment, "reviewed",
                shown.get("review_id"), "rows", shown.get("rows"), "patterns", sourceTypes.get("patterns"), "group",
                sourceTypes.get("group")));
        if (written.get("registry") == null) {
            throw new AssertionError("record_source_types wrote no registry: " + firstLines(String.valueOf(written)));
        }
    }

    // A user who puts these source types in the build's group, named so, keeps the Ids as they are when the
    // translation asks, and agrees to everything else (the group's Events into the feed's, a new version's names as
    // proposed).
    private static Function<ElicitFormRequest, ElicitResult> grouping(final List<String> sourceTypes,
                                                                      final String group) {
        final Function<ElicitFormRequest, ElicitResult> scoping = TestE2eSourceTypeGroups.scopeOf(sourceTypes,
                group);
        final Function<ElicitFormRequest, ElicitResult> keeping = choosing("Keep as is");
        return form -> form.message().contains(TestE2eSourceTypeGroups.SCOPE_FORM)
                ? scoping.apply(form)
                : keeping.apply(form);
    }

    // The user's answers, each form and its answer kept.
    private void answering(final Function<ElicitFormRequest, ElicitResult> user) {
        client.answeringWith(form -> {
            final ElicitResult answer = user.apply(form);
            final Map<String, Object> answered = new LinkedHashMap<>();
            answered.put("message", form.message());
            answered.put("content", answer.content());
            answered.put("options", formOptions(form.requestedSchema()));
            synchronized (formsAnswered) {
                formsAnswered.add(answered);
            }
            return answer;
        });
    }

    // The group's build scored: the registry given, the records its pipeline passed over (told as the tools tell
    // them, by the registry over the raw records, its XSLT logging nothing of them) and what it logged processing
    // the feed.
    private void scoreSourceTypes(final Map<String, Object> evalCase,
                                  final EvalScore score,
                                  final Map<String, Object> registry,
                                  final Map<String, Object> gate,
                                  final String group,
                                  final List<Long> raws) {
        int errorLines = 0;
        for (final Map<String, Object> stream : list(gate, "streams")) {
            for (final long errors : ids(stream, "errors")) {
                final String logged = storedText(errors);
                errorLines += (int) logged.lines().filter(line -> ERROR_LINE.matcher(line).find()).count();
            }
        }
        final Long passedOver = registry == null || group == null || list(gate, "streams").isEmpty()
                ? null
                : passedOver(registry, group, raws);
        score.scoreSourceTypes(evalCase, registry, passedOver, errorLines);
    }

    // The raw records of source types outside the group, by the registry's source type field and rows (the first
    // match winning; a part's meta for a meta: field), as review_coverage tells them.
    private long passedOver(final Map<String, Object> registry, final String group, final List<Long> raws) {
        final List<String> types = new ArrayList<>();
        list(registry, "rows").stream()
                .filter(row -> group.equalsIgnoreCase(String.valueOf(row.get("group"))))
                .forEach(row -> types.add(String.valueOf(row.get("name"))));
        return passedOverOutside(registry, types, raws);
    }

    /// The raw records of source types outside a set of them, by the registry's source type field and rows (the
    /// first match winning; a part's meta for a meta: field), as review_coverage tells them: what a group's pipeline
    /// passes over, its XSLT logging nothing of them.
    ///
    /// @param registry The registry, as [SourceTypeRegistry#read] reads it.
    /// @param types    The source types kept, by row name.
    /// @param raws     The raw streams.
    /// @return The records of the other source types.
    long passedOverOutside(final Map<String, Object> registry, final List<String> types, final List<Long> raws) {
        final String field = String.valueOf(registry.get("field"));
        final SourceTypeScope scope = new SourceTypeScope(null, field, SourceTypeRegistry.rows(registry), types);
        long passed = 0;
        for (final long raw : raws) {
            for (final StoredPart part : storedParts(raw)) {
                final SourceTypeScope.Filtered filtered = scope.byPart()
                        ? scope.filter(part.text(), SourceTypeMeta.partFields(field, part.attributes()))
                        : scope.filter(part.text());
                passed += filtered.passedOver().values().stream().mapToLong(Integer::longValue).sum();
            }
        }
        return passed;
    }

    // A stream's parts, each with its receipt headers and the case's feed as its Feed.
    private static List<Map.Entry<Map<String, String>, String>> feedParts(final String feed,
                                                                         final List<EvalCases.StreamPart> parts) {
        final List<Map.Entry<Map<String, String>, String>> out = new ArrayList<>();
        for (final EvalCases.StreamPart part : parts) {
            final Map<String, String> meta = new LinkedHashMap<>(part.meta());
            meta.put("Feed", feed);
            out.add(Map.entry(meta, part.text()));
        }
        return out;
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

    // The events in a Lucene index, through its pipeline, found by the verification searches. A later build's new
    // group gets an index of its own, named like the setup group's; the setup's group, joined, indexes into its own
    // index through a working copy of its indexing pipeline, the build's plan written to the copy's XSLT.
    private Indexed index(final String build,
                          final String feed,
                          final String group,
                          final String eventsPipeline,
                          final List<Long> events,
                          final EvalScore score,
                          final Setup setup) {
        final Indexed before = setup != null
                ? setup.built().indexed()
                : null;
        final boolean joins = before != null && group != null && group.equalsIgnoreCase(setup.built().group());
        final String indexName;
        if (before == null) {
            indexName = feed + "-INDEX";
        } else if (joins) {
            indexName = before.name();
        } else {
            indexName = FeedIndexes.groupIndexName(before.name(), List.of(setup.built().group()), group);
        }
        final Map<String, Object> plan = map(json("draft_index_mapping", args("backend", "lucene",
                "index_name", indexName, "convention", "stroom-flat", "events_stream_ids", events)), "plan");
        final String index;
        final String pipeline;
        if (joins) {
            index = before.index();
            // The group's indexing pipeline as its new version, under the names proposed, writing to the
            // group's index.
            final Map<String, Object> copied = agreed("copy_pipeline", args("build", build, "source_uuid",
                    before.pipeline(), "new_name", indexName + " - Indexing"));
            pipeline = uuidOf(copied);
            final String xslt = list(copied, "copied_documents").stream()
                    .filter(doc -> "XSLT".equals(doc.get("type")))
                    .map(doc -> (String) doc.get("uuid"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("The indexing pipeline's new version has no XSLT: "
                                                          + copied));
            json("save_xslt", args("build", build, "uuid", xslt, "index_plan", plan));
        } else {
            index = uuidOf(agreed("create_index_doc", args("build", build, "backend", "lucene",
                    "name", indexName, "time_field", plan.get("time_field"))));
            // Generated from the plan.
            final String xslt = uuidOf(agreed("save_xslt", args("build", build, "name", indexName + "-XSLT",
                    "index_plan", plan)));
            final String template = (String) templates("indexing").stream()
                    .filter(candidate -> "lucene".equals(candidate.get("backend")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No Lucene indexing template"))
                    .get("uuid");
            pipeline = uuidOf(agreed("create_indexing_pipeline", args("build", build,
                    "name", indexName + " - Indexing", "template_uuid", template, "xslt_uuid", xslt,
                    "index_uuid", index)));
        }
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
        // A joined group's working copy indexes into the build's test index, which has the plan's new fields: the
        // production index is left as it is until promotion.
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
            score.problem("indexing: gate " + gate.get("gate") + " " + firstLines(String.valueOf(gate.get("problems")))
                          + ", logged " + loggedProblems(gate, 3) + ", searches " + verified.get("checks"));
        }
        return new Indexed(index, indexName, pipeline);
    }

    // The ERROR, FATAL and WARN lines of the Error streams a processing round wrote, up to a limit a stream.
    private List<String> loggedProblems(final Map<String, Object> gate, final int limit) {
        final List<String> lines = new ArrayList<>();
        for (final Map<String, Object> stream : list(gate, "streams")) {
            for (final long errors : ids(stream, "errors")) {
                storedText(errors).lines()
                        .filter(line -> ERROR_LINE.matcher(line).find() || line.contains("WARN"))
                        .limit(limit)
                        .forEach(lines::add);
            }
        }
        return lines;
    }

    // promote_build: the layout shown first (needs_review), then promoted once the user agrees in the form.
    private void promote(final String build, final Map<String, Object> destinations) {
        final Map<String, Object> review = json("promote_build", args("build", build, "destinations",
                destinations));
        if (!"needs_review".equals(review.get("status"))) {
            throw new AssertionError("promote_build showed no layout for " + build + ": " + firstLines(
                    String.valueOf(review)));
        }
        final Map<String, Object> promoted = json("promote_build", args("build", build, "destinations",
                destinations, "reviewed", review.get("review_id")));
        if (promoted.get("promoted") == null) {
            throw new AssertionError(build + " not promoted: " + firstLines(String.valueOf(promoted)));
        }
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

    private List<String> luceneFields(final DocRef index) {
        return asUser(admin, () -> indexFieldService.findFields(new FindFieldCriteria(new PageRequest(0, 500),
                FindFieldCriteria.DEFAULT_SORT_LIST, index)).getValues().stream().map(Field::getFldName).toList());
    }

    // pipeline_writes.element_id: XMLFragmentParser is xmlFragmentParser, DSParser dsParser (a leading acronym is
    // lowered whole).
    private static String elementId(final String elementType) {
        final String lowered = Pattern.compile("^[A-Z]+(?=[A-Z][a-z])").matcher(elementType)
                .replaceFirst(match -> match.group().toLowerCase());
        return Character.toLowerCase(lowered.charAt(0)) + lowered.substring(1);
    }

    // --------------------------------------------------------------------------------

    /// What a case's setup left ([#setUp]).
    ///
    /// @param feed   The feed, promoted to [#FEEDS].
    /// @param build  The setup's build, promoted.
    /// @param built  What its reference build made.
    /// @param before Its group's pipeline, XSLT and index as it left them, for the case's score.
    record Setup(String feed, String build, Built built, EvalScore.Before before) {

    }

    /// What a reference build made.
    ///
    /// @param raws         The feed's raw streams it processed.
    /// @param group        Its source type group, or null for none.
    /// @param pipeline     Its events pipeline's UUID (a working copy, for a promoted group joined).
    /// @param pipelineName The pipeline's name.
    /// @param xslt         The group's XSLT's UUID, or null for a build with no group.
    /// @param indexed      Its index, or null if it reached none.
    record Built(List<Long> raws,
                 String group,
                 String pipeline,
                 String pipelineName,
                 String xslt,
                 Indexed indexed) {

    }

    /// A build's index.
    ///
    /// @param index    The index's UUID.
    /// @param name     Its name.
    /// @param pipeline Its indexing pipeline's UUID.
    record Indexed(String index, String name, String pipeline) {

    }

    private record Read(List<String> records, Integer total) {

    }


    // --------------------------------------------------------------------------------


    /// A question's index.
    ///
    /// @param node      The Elasticsearch node holding it.
    /// @param indexName Its name there.
    /// @param doc       Its Elastic Index doc.
    record QuestionIndex(ElasticTestNode node, String indexName, DocRef doc) {

    }
}
