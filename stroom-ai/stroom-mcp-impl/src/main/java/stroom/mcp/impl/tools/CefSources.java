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
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.cef.CefInstructions;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Where a CEF pipeline should come from, for draft_cef_mapping (the Stroom MCP server's `tools/cef.py`
/// `_templates`): a template the standing instructions name (they decide), the environment's forwarding
/// templates, the CEF pipelines there are already with the templates they inherit from, and the XSLTs that write
/// CEF lines; and the KafkaConfig documents the user may pick from. Everything is read as the user: only what they
/// may view is found.
class CefSources {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CefSources.class);

    /// The most existing CEF pipelines given (each with the template it inherits from).
    static final int MAX_EXISTING = 20;
    /// The most XSLTs writing CEF lines given.
    static final int MAX_XSLTS = 10;
    /// The most KafkaConfig documents given.
    static final int MAX_KAFKA_CONFIGS = 50;
    private static final List<String> CEF_NAMES = List.of("*CEF*", "*ArcSight*", "*Arcsight*");
    private static final String KAFKA_CONFIG = "KafkaConfig";

    private final PipelineIndex pipelineIndex;
    private final PipelineTemplates pipelineTemplates;
    private final DocumentAccess documentAccess;
    private final WriteGuard guard;

    /// @param pipelineIndex     Finds documents by name and content.
    /// @param pipelineTemplates Finds the forwarding templates.
    /// @param documentAccess    Reads each pipeline's parent.
    /// @param guard             Names the workspace folder, whose drafts are left out.
    @Inject
    CefSources(final PipelineIndex pipelineIndex,
               final PipelineTemplates pipelineTemplates,
               final DocumentAccess documentAccess,
               final WriteGuard guard) {
        this.pipelineIndex = pipelineIndex;
        this.pipelineTemplates = pipelineTemplates;
        this.documentAccess = documentAccess;
        this.guard = guard;
    }

    /// @param said    What the standing instructions say of CEF output ([CefInstructions#fromInstructions]).
    /// @param folders Folders the pipeline will live in.
    /// @return `from_instructions` (the template they name, the pipelines so named, and a note), then
    /// `forwarding_templates` (or `forwarding_templates_error`), `existing_cef_pipelines` and `xslts_writing_cef`,
    /// each when there are any.
    Map<String, Object> templates(final Map<String, Object> said, final List<String> folders) {
        final Map<String, Object> out = new LinkedHashMap<>();
        if (said.get(CefInstructions.TEMPLATE) instanceof final String template && !template.isEmpty()) {
            List<DocRef> named = pipelineIndex.named(PipelineDoc.TYPE, template, 5);
            if (named.isEmpty()) {
                named = pipelineIndex.named(PipelineDoc.TYPE, "*" + template + "*", 5);
            }
            final Map<String, Object> fromInstructions = new LinkedHashMap<>();
            fromInstructions.put("named", template);
            fromInstructions.put("found", named.stream().map(this::entry).toList());
            fromInstructions.put("note", "The standing instructions name this template: use it, whatever else is "
                                         + "found.");
            out.put("from_instructions", fromInstructions);
        }
        try {
            final List<Map<String, Object>> candidates = new ArrayList<>();
            for (final Object candidate : NullSafe.list((List<?>) pipelineTemplates.find("forwarding")
                    .get("candidates"))) {
                if (candidate instanceof final Map<?, ?> map) {
                    final Map<String, Object> shown = new LinkedHashMap<>();
                    for (final String key : List.of("uuid", "name", "path", "backend", "children",
                            "child_must_supply")) {
                        shown.put(key, map.get(key));
                    }
                    candidates.add(shown);
                }
            }
            out.put("forwarding_templates", candidates);
        } catch (final RuntimeException e) {
            // The search is advice; a broken pipeline elsewhere must not stop the draft.
            out.put("forwarding_templates_error", head(StreamSummaries.reason(e), 200));
        }
        // Pipelines and XSLTs in the workspace are drafts, not the environment's practice (seen: twenty leftover
        // builds' pipelines filled the list, and the production one it was looking for wasn't in it).
        final String drafts = PipelineIndex.SYSTEM + "/" + guard.workspaceFolder() + "/";
        // Nearest first: those in or beside the folders the pipeline is for are the examples that matter (seen: a
        // dozen promoted CEF pipelines elsewhere crowded out the one in the user's own folder).
        final List<List<String>> wanted = NullSafe.list(folders).stream().map(CefSources::parts).toList();
        final Map<String, Map<String, Object>> existing = new LinkedHashMap<>();
        for (final String pattern : CEF_NAMES) {
            for (final DocRef ref : pipelineIndex.named(PipelineDoc.TYPE, pattern, 200)) {
                final Map<String, Object> entry = entry(ref);
                if (!isDraft(String.valueOf(entry.get("path")), drafts)) {
                    existing.put(ref.getUuid(), entry);
                }
            }
        }
        final List<Map<String, Object>> ranked = existing.values().stream()
                .sorted(Comparator.comparingInt((Map<String, Object> e) -> nearness(String.valueOf(e.get("path")),
                                wanted))
                        .thenComparing(e -> NullSafe.string((String) e.get("name"))))
                .limit(MAX_EXISTING)
                .toList();
        for (final Map<String, Object> entry : ranked) {
            try {
                documentAccess.read(new DocRef(PipelineDoc.TYPE, (String) entry.get("uuid")), PipelineDoc.class)
                        .map(PipelineDoc::getParentPipeline)
                        .filter(parent -> NullSafe.isNonBlankString(parent.getUuid()))
                        .ifPresent(parent -> {
                            final Map<String, Object> inherits = new LinkedHashMap<>();
                            inherits.put("uuid", parent.getUuid());
                            inherits.put("name", parent.getName());
                            entry.put("inherits_from", inherits);
                        });
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message("Unable to read pipeline {}", entry.get("uuid")), e);
            }
        }
        if (!ranked.isEmpty()) {
            out.put("existing_cef_pipelines", ranked);
        }
        final List<Map<String, Object>> xslts = pipelineIndex.withContent("CEF:0", 200).stream()
                .filter(ref -> !isDraft(PipelineIndex.path(documentAccess, ref), drafts))
                .sorted(Comparator.comparingInt(ref -> nearness(PipelineIndex.path(documentAccess, ref), wanted)))
                .filter(ref -> XsltDoc.TYPE.equals(ref.getType()))
                .limit(MAX_XSLTS)
                .map(ref -> {
                    final Map<String, Object> xslt = new LinkedHashMap<>();
                    xslt.put("uuid", ref.getUuid());
                    xslt.put("name", ref.getName());
                    return xslt;
                })
                .toList();
        if (!xslts.isEmpty()) {
            out.put("xslts_writing_cef", xslts);
        }
        return out;
    }

    /// @return The KafkaConfig documents the user may view, each `uuid`, `name` and `path`.
    List<Map<String, Object>> kafkaConfigs() {
        return pipelineIndex.named(KAFKA_CONFIG, "*", MAX_KAFKA_CONFIGS).stream().map(this::entry).toList();
    }

    private Map<String, Object> entry(final DocRef ref) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("uuid", ref.getUuid());
        out.put("name", ref.getName());
        out.put("path", PipelineIndex.path(documentAccess, ref));
        return out;
    }

    /// @param path An explorer path (`System / A / B`, or `System/A/B`), or null.
    /// @return Its names.
    static List<String> parts(final String path) {
        return Arrays.stream(NullSafe.string(path).split("/"))
                .map(String::strip)
                .filter(part -> !part.isEmpty())
                .toList();
    }

    /// @param path   An explorer path.
    /// @param drafts The workspace folder's path, ending `/`.
    /// @return True if it is in the workspace: a build's draft.
    static boolean isDraft(final String path, final String drafts) {
        return (String.join("/", parts(path)) + "/").startsWith(drafts);
    }

    /// @param path   An explorer path.
    /// @param wanted The folders the pipeline is for, as their names.
    /// @return Minus the most leading names the path shares with any of them: the nearest sort first.
    static int nearness(final String path, final List<List<String>> wanted) {
        final List<String> parts = parts(path);
        int best = 0;
        for (final List<String> target : wanted) {
            int shared = 0;
            for (int i = 0; i < Math.min(parts.size(), target.size()); i++) {
                if (!parts.get(i).equals(target.get(i))) {
                    break;
                }
                shared++;
            }
            best = Math.max(best, shared);
        }
        return -best;
    }

    private static String head(final String text, final int n) {
        return text.length() <= n
                ? text
                : text.substring(0, n);
    }
}
