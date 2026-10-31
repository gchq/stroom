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

import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.fielddoc.IndexFieldDocs;
import stroom.ai.impl.tools.indexing.IndexingHelpers;
import stroom.ai.impl.tools.indexing.TemplateCheck;
import stroom.pipeline.shared.PipelineDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
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

/// evaluate_pipeline's `index` section for an indexing pipeline: the index doc it writes to, and the fields the
/// documents it writes (stepped by step_sample) have against the index's own fields, as Stroom lists them: fields
/// written that the index lacks, index fields nothing writes, and values whose field's type or analyzer doesn't suit
/// them. For Elasticsearch, whether an index template is agreed with the pipeline, for check_index_template.
///
/// Read as the user: the index docs and their fields are only those they may view.
class IndexEvaluation {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexEvaluation.class);

    /// The most fields listed of each comparison.
    static final int FIELDS_SHOWN = 40;
    /// The most mismatched fields listed.
    static final int MISMATCHES_SHOWN = 20;
    /// The most values of a field checked against its type.
    static final int VALUES_CHECKED = 50;
    // The type a Lucene field's (or Stroom's view of an Elastic field's) type is checked as.
    private static final Map<String, String> CHECKED_AS = Map.of("id", "long", "long", "long", "integer", "integer",
            "float", "float", "double", "double", "date", "date", "boolean", "boolean", "ipv4_address", "ip",
            "keyword", "keyword");

    private final IndexDocs indexDocs;
    private final IndexTemplateAgreements templateAgreements;

    /// @param indexDocs          Finds the index docs a pipeline writes to, and their fields.
    /// @param templateAgreements Reads the index template agreed with an Elasticsearch indexing pipeline.
    @Inject
    IndexEvaluation(final IndexDocs indexDocs, final IndexTemplateAgreements templateAgreements) {
        this.indexDocs = indexDocs;
        this.templateAgreements = templateAgreements;
    }

    /// The `index` section.
    ///
    /// @param merged     The indexing pipeline, as Stroom runs it.
    /// @param pipeline   Its document.
    /// @param outputs    What each record stepped wrote at its indexing XSLT, or null if nothing was stepped.
    /// @param notStepped Why nothing was stepped, when nothing was.
    /// @return `status`, `index`, `backend`, the Elasticsearch `cluster` and `index_name`, `index_fields`,
    /// `documents_stepped`, `fields_written`, `written_not_in_index`, `index_fields_not_written`,
    /// `type_mismatches`, any `index_template`, and `notes` for what couldn't be compared.
    Map<String, Object> index(final MergedPipeline merged,
                              final PipelineDoc pipeline,
                              final List<String> outputs,
                              final String notStepped) {
        final Map<String, Object> section = new LinkedHashMap<>();
        final List<IndexDocs.Index> written;
        try {
            written = indexDocs.written(merged);
        } catch (final RuntimeException e) {
            return status("unavailable", "The index docs it writes to can't be read: " + reason(e));
        }
        if (written.isEmpty()) {
            return status("no_index", "Its indexing filter names no index doc you may view (a Lucene index doc, or "
                                      + "an Elastic Index doc whose index name it writes).");
        }
        final IndexDocs.Index index = written.getFirst();
        final List<String> notes = new ArrayList<>();
        section.put("status", "checked");
        section.put("index", MergedPipeline.docRef(index.docRef()));
        section.put("backend", index.backend());
        if (index.elastic() != null) {
            elasticDestination(pipeline, section, notes);
        }
        if (written.size() > 1) {
            notes.add("It writes to " + written.size() + " index docs; compared with the first only: "
                      + ToolText.list(written.stream().map(w -> w.docRef().getName()).toList()));
        }

        List<Map<String, Object>> fields = null;
        try {
            fields = indexDocs.fields(index);
            section.put("index_fields", fields.size());
        } catch (final RuntimeException e) {
            notes.add("The index's fields can't be read: " + reason(e));
        }
        Map<String, List<String>> values = null;
        if (outputs == null) {
            notes.add("The fields it writes are not known: " + NullSafe.nonBlankStringElse(notStepped,
                    "nothing was stepped") + ".");
        } else {
            final List<Map<String, List<String>>> documents = IndexFieldDocs.indexDocuments(outputs);
            values = writtenValues(documents);
            section.put("documents_stepped", documents.size());
            section.put("fields_written", values.size());
        }
        if (fields != null && values != null) {
            compare(fields, values, section);
        } else {
            section.put("status", "partial");
        }
        if (!notes.isEmpty()) {
            section.put("notes", notes);
        }
        return section;
    }

    // Fields written against the index's fields: missing, unused, and values their field doesn't suit.
    private static void compare(final List<Map<String, Object>> fields,
                                final Map<String, List<String>> values,
                                final Map<String, Object> section) {
        final Set<String> listed = new LinkedHashSet<>();
        fields.forEach(field -> listed.add(String.valueOf(field.get("name"))));
        final List<String> writtenNames = List.copyOf(values.keySet());
        final List<String> missing = IndexingHelpers.fieldsNotListed(Map.of("written", writtenNames), listed)
                .getOrDefault("written", List.of());
        section.put("written_not_in_index", shown(missing));
        final List<String> unused = new ArrayList<>(new TreeSet<>(listed));
        unused.removeAll(values.keySet());
        section.put("index_fields_not_written", shown(unused));
        if (unused.size() > FIELDS_SHOWN) {
            section.put("index_fields_not_written_more", unused.size() - FIELDS_SHOWN);
        }
        final List<Map<String, Object>> mismatches = new ArrayList<>();
        for (final Map<String, Object> field : fields) {
            final List<String> held = values.get(String.valueOf(field.get("name")));
            if (held != null) {
                mismatch(field, held).ifPresent(mismatches::add);
            }
        }
        section.put("type_mismatches", mismatches.subList(0, Math.min(MISMATCHES_SHOWN, mismatches.size())));
        if (!missing.isEmpty()) {
            section.put("hint", "Fields written that the index lacks are not searchable"
                                + (section.get("backend").equals("elasticsearch")
                    ? " as mapped (Elasticsearch adds a field it hasn't seen with a guessed type, or not at all)"
                    : " (Lucene indexes only the fields its index doc lists)")
                                + ": add them to the index, or stop writing them.");
        }
    }

    /// @param field  An index field, as [IndexDocs#fields] gives it.
    /// @param values Values the pipeline wrote to it.
    /// @return Why its type or analyzer doesn't suit the values, if it doesn't: `field`, `index_type`,
    /// `values_checked`, `values_not_fitting`, `example` and `problem`.
    static Optional<Map<String, Object>> mismatch(final Map<String, Object> field, final List<String> values) {
        final String mapped = checkedAs(field);
        if (mapped == null) {
            return Optional.empty();
        }
        int notFitting = 0;
        String example = null;
        String problem = null;
        if ("keyword".equals(mapped)) {
            for (final String value : values) {
                if (value.strip().contains(" ")) {
                    notFitting++;
                    if (example == null) {
                        example = value;
                    }
                }
            }
            problem = "holds text of several words in a keyword field: a search for one word in it finds nothing "
                      + "(only the whole value, or a wildcard). If it is searched by word, give it a text analyzer "
                      + "(Lucene) or map it as text (Elasticsearch).";
        } else {
            for (final String value : values) {
                final String why = TemplateCheck.fits(Map.of("type", mapped), "string", value);
                if (why != null) {
                    notFitting++;
                    if (problem == null) {
                        example = value;
                        problem = why;
                    }
                }
            }
        }
        if (notFitting == 0) {
            return Optional.empty();
        }
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("field", field.get("name"));
        out.put("index_type", typeSaid(field));
        out.put("values_checked", values.size());
        out.put("values_not_fitting", notFitting);
        out.put("example", ToolText.head(example, 200));
        out.put("problem", problem);
        return Optional.of(out);
    }

    /// @param documents The documents the indexing XSLT wrote ([IndexFieldDocs#indexDocuments]).
    /// @return Each field the documents have, in the order first written, with up to [#VALUES_CHECKED] of its
    /// values.
    static Map<String, List<String>> writtenValues(final List<Map<String, List<String>>> documents) {
        final Map<String, List<String>> values = new LinkedHashMap<>();
        for (final Map<String, List<String>> document : documents) {
            document.forEach((name, held) -> {
                final List<String> kept = values.computeIfAbsent(name, k -> new ArrayList<>());
                for (final String value : held) {
                    if (kept.size() < VALUES_CHECKED && NullSafe.isNonEmptyString(value)) {
                        kept.add(value);
                    }
                }
            });
        }
        return values;
    }

    // Where an Elasticsearch indexing pipeline writes, and whether an index template is agreed for it.
    private void elasticDestination(final PipelineDoc pipeline,
                                    final Map<String, Object> section,
                                    final List<String> notes) {
        try {
            final Optional<Map<String, Object>> destination = templateAgreements.elasticDestination(
                    pipeline.getUuid());
            if (destination.isEmpty()) {
                return;
            }
            section.put("cluster", destination.get().get("cluster"));
            section.put("index_name", destination.get().get("index name"));
            final Optional<String> problem = templateAgreements.agreementProblem(pipeline, destination.get());
            final Map<String, Object> template = new LinkedHashMap<>();
            template.put("agreed", problem.isEmpty());
            template.put("next", problem.isEmpty()
                    ? "An index template is agreed with this pipeline: check_index_template pipeline_uuid="
                      + pipeline.getUuid() + " compares it with the documents the pipeline writes. Suggest it in "
                      + "the report; don't run it unless the user asks."
                    : ToolText.head(problem.get(), 400));
            section.put("index_template", template);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to read the Elasticsearch destination of {}",
                    pipeline.getUuid()), e);
            notes.add("Where it writes in Elasticsearch can't be read: " + reason(e));
        }
    }

    // The type its values are checked as, or null when any value suits it.
    private static String checkedAs(final Map<String, Object> field) {
        final Object nativeType = field.get("elasticsearch_type");
        if (nativeType != null) {
            return String.valueOf(nativeType);
        }
        final String type = String.valueOf(field.get("type"));
        if ("text".equals(type)) {
            return "KEYWORD".equals(field.get("analyzer"))
                    ? "keyword"
                    : null;
        }
        return CHECKED_AS.get(type);
    }

    private static String typeSaid(final Map<String, Object> field) {
        if (field.get("elasticsearch_type") != null) {
            return String.valueOf(field.get("elasticsearch_type"));
        }
        return field.get("analyzer") == null
                ? String.valueOf(field.get("type"))
                : field.get("type") + " (" + field.get("analyzer") + " analyzer)";
    }

    private static List<String> shown(final List<String> fields) {
        return fields.subList(0, Math.min(FIELDS_SHOWN, fields.size()));
    }

    private static Map<String, Object> status(final String status, final String reason) {
        final Map<String, Object> section = new LinkedHashMap<>();
        section.put("status", status);
        section.put("reason", reason);
        return section;
    }

    private static String reason(final RuntimeException e) {
        return NullSafe.nonBlankStringElse(e.getMessage(), e.getClass().getSimpleName()).strip();
    }
}
