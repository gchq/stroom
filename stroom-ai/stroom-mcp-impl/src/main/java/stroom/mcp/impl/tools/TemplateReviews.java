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

import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.indexing.IndexTemplates;
import stroom.ai.impl.tools.indexing.IndexTemplates.NamedTemplate;
import stroom.ai.impl.tools.indexing.TemplateMappings;
import stroom.ai.impl.tools.sample.PyJson;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Supplier;

/// How an index template is agreed with the user, for propose_index_template and check_index_template (the Stroom
/// MCP server's `tools/indexing.py` `_agree`, `_asking`, `_template_summary`, `_not_theirs` and `_commit_action`):
/// shown in the chat first (needs_review), then confirmed in a short form, then kept with the pipeline (its
/// description) as the agreed one, which create_processor_filter asks the user to confirm is committed to the
/// cluster. Nothing is written to Elasticsearch: the user's cluster admin commits the template.
class TemplateReviews {

    private final Consent consent;
    private final IndexingMemory memory;
    private final PipelineDocs pipelineDocs;
    private final WriteGuard guard;
    private final IndexTemplateAgreements agreements;
    private final Supplier<Instant> clock;

    /// @param consent      Asks the user to confirm.
    /// @param memory       Keeps the template shown, by its review id.
    /// @param pipelineDocs Reads and saves the pipeline.
    /// @param guard        Checks the pipeline is the build's own.
    /// @param agreements   Digests the pipeline's indexing XSLT.
    @Inject
    TemplateReviews(final Consent consent,
                    final IndexingMemory memory,
                    final PipelineDocs pipelineDocs,
                    final WriteGuard guard,
                    final IndexTemplateAgreements agreements) {
        this(consent, memory, pipelineDocs, guard, agreements, Instant::now);
    }

    /// As the injected constructor, with the clock given, for tests.
    TemplateReviews(final Consent consent,
                    final IndexingMemory memory,
                    final PipelineDocs pipelineDocs,
                    final WriteGuard guard,
                    final IndexTemplateAgreements agreements,
                    final Supplier<Instant> clock) {
        this.consent = consent;
        this.memory = memory;
        this.pipelineDocs = pipelineDocs;
        this.guard = guard;
        this.agreements = agreements;
        this.clock = clock;
    }

    /// The user reviews the index template in the chat, then confirms it in a short form; once they have, it is
    /// kept with the pipeline as the agreed one, which create_processor_filter asks them to confirm is committed to
    /// the cluster.
    ///
    /// @param call           The call, whose forms are its own.
    /// @param context        The call's context.
    /// @param action         The tool asking.
    /// @param pipelineUuid   The indexing pipeline.
    /// @param destination    Where it writes (`index name`, `cluster`).
    /// @param name           The template's name.
    /// @param body           Its body.
    /// @param components     The component templates it composes, by name.
    /// @param confirmationId The confirmation id given, or null.
    /// @param reviewed       The review id given, or null.
    /// @return The round to return (needs_review, or the confirmation), or empty once agreed.
    /// @throws ToolRefusal if the pipeline isn't the build's own, or the user declines.
    Optional<Map<String, Object>> agree(final ToolCall call,
                                        final ToolCallContext context,
                                        final String action,
                                        final String pipelineUuid,
                                        final Map<String, Object> destination,
                                        final String name,
                                        final Map<String, Object> body,
                                        final Map<String, ?> components,
                                        final String confirmationId,
                                        final Object reviewed) {
        final PipelineDoc doc = pipelineDocs.read(pipelineUuid);
        guard.checkInBuild(context.session(), new DocRef(PipelineDoc.TYPE, doc.getUuid(), doc.getName()));
        final String devTools = IndexTemplates.devTools(name, body);
        final String digest = agreements.indexingXsltDigest(pipelineUuid);
        final Map<String, Object> kept = KeptMapping.readAgreedTemplate(doc.getDescription()).orElse(Map.of());
        if (sameAsAgreed(kept, name, body, destination, components, digest)) {
            // Already agreed, as it is: seen in Ask Stroom AI, the template the user had agreed was checked again
            // once committed, and they were asked to review and confirm it a second time.
            return Optional.empty();
        }
        memory.showTemplate(devTools);
        if (!Consent.wasReviewed(reviewed, devTools)) {
            // Passed on only with this template's id, as promote_build's layout (seen there: reviewed=true from the
            // start).
            final String reviewId = Consent.reviewId(devTools);
            final Map<String, Object> round = new LinkedHashMap<>();
            round.put("status", "needs_review");
            round.put("dev_tools", devTools);
            round.put("review_id", reviewId);
            round.put("hint", "Show the user this index template before anything else, in your reply (not in your "
                              + "thinking, which they may not see): dev_tools as a json code block, exactly as it is "
                              + "(a form can't show it readably), with the notes on what came from where, and say it "
                              + "is not to go on the cluster until they've confirmed it. Then, in the same turn, "
                              + "without waiting for a reply, call " + action + " again with the same arguments plus "
                              + "reviewed='" + reviewId + "' (this template's review_id); "
                              + (CheckIndexTemplateTool.NAME.equals(action)
                    ? "the template itself may be left out, and component templates given before are kept: don't "
                      + "retype them"
                    : "component templates given before are kept: don't send them again")
                              + ". The form that opens is where they confirm it or say what to change. Seen: an "
                              + "agent ended its turn after showing it, and the user committed the template before "
                              + "it was agreed." + Consent.reviewNote(reviewed));
            return Optional.of(round);
        }
        final Optional<Map<String, Object>> gate = consent.require(call, context, Consent.Request.confirmation(action,
                "Use Elasticsearch index template '" + name + "' for index '" + destination.get("index name")
                + "' (cluster " + destination.get("cluster") + "), as shown in the chat",
                summary(name, body, devTools), confirmationId));
        if (gate.isPresent()) {
            return gate;
        }
        final Map<String, Object> agreed = new LinkedHashMap<>();
        agreed.put("name", name);
        agreed.put("index", destination.get("index name"));
        agreed.put("cluster", destination.get("cluster"));
        agreed.put("component_templates", new ArrayList<>(new TreeSet<>(components.keySet())));
        agreed.put("xslt", digest);
        agreed.put("agreed", clock.get().truncatedTo(ChronoUnit.SECONDS).toString());
        agreed.put("dev_tools", devTools);
        pipelineDocs.write(doc.copy().description(KeptMapping.withAgreedTemplate(doc.getDescription(), agreed))
                .build());
        return Optional.empty();
    }

    /// The template in a few short lines, for the confirmation form: the user reviews the whole request, with its
    /// notes, in the chat first (seen in VS Code: the form showed it as one long unformatted line, and an invalid
    /// template was agreed). Seen in testing: with every field, note and component template in it, the form ran to
    /// 2,000 characters and the client declined it as too long. Bound to exactly what was shown by its review id.
    ///
    /// @param name  The template's name.
    /// @param body  Its body.
    /// @param shown The template as shown (dev_tools).
    /// @return The form's details.
    static Map<String, Object> summary(final String name, final Map<String, Object> body, final String shown) {
        final Map<String, Object> mappings = body.get("template") instanceof final Map<?, ?> template
                                             && template.get("mappings") instanceof final Map<?, ?> found
                ? castMap(found)
                : Map.of();
        final long fields = TemplateMappings.readMappingFields(body).values().stream()
                .filter(spec -> spec.get("type") != null && !"object".equals(spec.get("type")))
                .count();
        final Object given = body.get("index_patterns");
        final List<String> patterns = given instanceof final List<?> list
                ? list.stream().map(String::valueOf).toList()
                : given == null
                        ? List.of()
                        : List.of(String.valueOf(given));
        final Map<String, Object> details = new LinkedHashMap<>();
        details.put("index template", name + ", exactly as shown in the chat (review id " + Consent.reviewId(shown)
                                      + ")");
        details.put("applies to", String.join(", ", patterns)
                                  + (body.get("priority") != null
                ? " (priority " + body.get("priority") + ")"
                : "")
                                  + (body.containsKey("data_stream")
                ? ", as a data stream"
                : ""));
        if (body.get("composed_of") instanceof final List<?> composed && !composed.isEmpty()) {
            details.put("composed of", String.join(", ", composed.stream().map(String::valueOf).toList()));
        }
        details.put("fields mapped here", fields);
        final String dynamic = String.valueOf(mappings.get("dynamic") == null
                ? "None"
                : mappings.get("dynamic")).toLowerCase(Locale.ROOT);
        details.put("unmapped fields", switch (dynamic) {
            case "false" -> "kept in _source, not searchable";
            case "strict" -> "refused";
            case "runtime" -> "runtime fields";
            default -> "mapped by Elasticsearch as documents bring them";
        });
        return details;
    }

    /// The confirmation to return, with what the agent shows alongside it: what to do first, the template after.
    /// Seen in testing: the review reply ran to 10,000 characters, the client showed it cut short, and the template
    /// was never put to the user. The fields written are in the template.
    ///
    /// @param gate   The round to return.
    /// @param result The tool's result so far.
    /// @return The round, with the result's key parts.
    static Map<String, Object> asking(final Map<String, Object> gate, final Map<String, Object> result) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", gate.get("status"));
        out.put("hint", gate.get("hint") + " If the user corrects the template instead, check_index_template with "
                        + "their version: it is confirmed there (component templates given before are kept).");
        gate.forEach((key, value) -> {
            if (!"status".equals(key) && !"hint".equals(key)) {
                out.put(key, value);
            }
        });
        for (final String key : List.of("compatible", "blocking", "pipeline_changes", "template_name", "index",
                "cluster", "self_check", "from_example", "notes", "component_templates")) {
            if (result.containsKey(key)) {
                Object value = result.get(key);
                if ("self_check".equals(key) && value instanceof final Map<?, ?> check) {
                    final Map<String, Object> shorter = new LinkedHashMap<>();
                    check.forEach((name, part) -> {
                        if (!"fields_written".equals(name)) {
                            shorter.put(String.valueOf(name), part);
                        }
                    });
                    value = shorter;
                }
                out.put(key, value);
            }
        }
        return out;
    }

    /// Refuses a template name that is the example's own (or one of its components'): committed, it would replace
    /// the sibling source's template. Seen in testing: a template made under another index's name and for its
    /// indices.
    ///
    /// @param name       The template's name.
    /// @param given      The template_name given, or null.
    /// @param example    The example's own name, or null.
    /// @param components The component templates' names.
    /// @throws ToolRefusal for one of their names.
    static void refuseTheirName(final String name,
                                final String given,
                                final String example,
                                final List<String> components) {
        if (NullSafe.isNonEmptyString(name) && name.equals(example)) {
            throw new ToolRefusal("'" + name + "' is the example index template's own name: committed, this template "
                                  + "would replace it, and the sibling source's index would follow this one. "
                                  + (NullSafe.isNonEmptyString(given)
                    ? "Leave template_name out (the new index's name is used), or give a name of its own."
                    : "The index name is the example's: rename_index to the new index's own name."));
        }
        if (NullSafe.isNonEmptyString(name) && components.contains(name)) {
            throw new ToolRefusal("'" + name + "' is a component template's name, not a name for this index "
                                  + "template: leave template_name out (the new index's name is used), or give a name "
                                  + "of its own.");
        }
    }

    /// The agreed template's request, as a button for the cluster admin to copy (asked for by the user).
    ///
    /// @param result      The tool's result, with `dev_tools`.
    /// @param name        The template's name.
    /// @param destination Where the pipeline writes.
    /// @return The result, with the button in `user_actions`.
    static Map<String, Object> withCommitAction(final Map<String, Object> result,
                                                final String name,
                                                final Map<String, Object> destination) {
        return UserActionEntries.withEntries(result, Collections.singletonList(
                UserActionEntries.esRequest("commit-template", NullSafe.string((String) result.get("dev_tools")),
                        "Commit the agreed index template '" + name + "' to cluster "
                        + destination.get("cluster"))));
    }

    // Whether the template kept with the pipeline is this one, for the same index, cluster, components and code.
    private static boolean sameAsAgreed(final Map<String, Object> kept,
                                        final String name,
                                        final Map<String, Object> body,
                                        final Map<String, Object> destination,
                                        final Map<String, ?> components,
                                        final String digest) {
        final Object devTools = kept.get("dev_tools");
        if (devTools == null || String.valueOf(devTools).isEmpty()) {
            return false;
        }
        final NamedTemplate parsed;
        try {
            parsed = IndexTemplates.parseTemplate(String.valueOf(devTools));
        } catch (final IllegalArgumentException e) {
            return false;
        }
        final List<String> keptComponents = kept.get("component_templates") instanceof final List<?> list
                ? list.stream().map(String::valueOf).sorted().toList()
                : List.of();
        return Objects.equals(parsed.name(), name)
               && PyJson.dumpsSortedKeys(parsed.body()).equals(PyJson.dumpsSortedKeys(body))
               && Objects.equals(kept.get("index"), destination.get("index name"))
               && Objects.equals(kept.get("cluster"), destination.get("cluster"))
               && keptComponents.equals(components.keySet().stream().sorted().toList())
               && Objects.equals(kept.get("xslt"), digest);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(final Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
