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

import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.processing.MergedPipeline;
import stroom.ai.impl.tools.processing.MergedPipeline.ElementProperty;
import stroom.ai.impl.tools.processing.MergedPipeline.TranslationDoc;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// Which template a pipeline inherits from, as facts for a copy, a new version, a review or a promotion (decided
/// with the user, 2026-11-25): a site swaps environments (production and pre-production, or Elasticsearch lookups
/// while reprocessing) by re-parenting pipelines onto another template, so the tools keep every pipeline's parent and
/// never make an inherited property the child's own; they say what it inherits and flag, changing nothing:
///
/// - a template other than its predecessor's (`inherits 'X'; its predecessor 'P' inherits 'Y'`);
/// - a template none of its siblings inherit (the other pipelines of its kind in the same folder that inherit one);
/// - an XSLT of its own where its template supplies one, so it won't follow the template.
///
/// Everything is read as the user; what can't be read is left out, never guessed.
class PipelineInheritance {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PipelineInheritance.class);

    /// The most siblings read.
    static final int MAX_SIBLINGS = 20;
    private static final String XSLT = "xslt";

    private final DocumentAccess documentAccess;
    private final PipelineShapes pipelineShapes;

    /// @param documentAccess Reads the pipelines, their folders and templates, as the user.
    /// @param pipelineShapes Reads each pipeline as Stroom runs it, and its kind.
    @Inject
    PipelineInheritance(final DocumentAccess documentAccess, final PipelineShapes pipelineShapes) {
        this.documentAccess = documentAccess;
        this.pipelineShapes = pipelineShapes;
    }

    /// The facts of a pipeline's inheritance.
    ///
    /// @param uuid        The pipeline.
    /// @param predecessor The pipeline it is the next version or copy of, or null.
    /// @param siblingsIn  The folder whose pipelines of its kind are its siblings (where it will live); null for its
    ///                    predecessor's folder where it has one (a copy or new version lives beside it once
    ///                    promoted), else its own.
    /// @return What it inherits and the flags; empty if the pipeline can't be read.
    Optional<Facts> facts(final String uuid, final String predecessor, final DocRef siblingsIn) {
        final Optional<PipelineDoc> found = read(uuid);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        final PipelineDoc pipeline = found.get();
        final DocRef template = named(pipeline.getParentPipeline());
        final List<String> flags = new ArrayList<>();
        if (predecessor != null && !predecessor.equals(uuid)) {
            read(predecessor).ifPresent(before -> {
                final DocRef was = named(before.getParentPipeline());
                if (!Objects.equals(uuidOf(template), uuidOf(was))) {
                    flags.add(inherits(pipeline.getName(), template) + "; its predecessor '" + before.getName()
                              + "' " + inheritsWord(was));
                }
            });
        }
        siblingFlag(pipeline, template, predecessor, siblingsIn).ifPresent(flags::add);
        flags.addAll(overrides(pipeline, template));
        return Optional.of(new Facts(pipeline.getName(), template, flags));
    }

    // Its template unlike any its siblings of the same kind inherit.
    private Optional<String> siblingFlag(final PipelineDoc pipeline,
                                         final DocRef template,
                                         final String predecessor,
                                         final DocRef siblingsIn) {
        final String stage = stage(pipeline.getUuid());
        if (stage == null) {
            return Optional.empty();
        }
        final Optional<DocRef> folder = siblingsIn != null
                ? Optional.of(siblingsIn)
                : documentAccess.parent(predecessor != null
                        ? new DocRef(PipelineDoc.TYPE, predecessor)
                        : pipeline.asDocRef());
        if (folder.isEmpty()) {
            return Optional.empty();
        }
        final Map<String, List<String>> byTemplate = new LinkedHashMap<>();
        final Map<String, DocRef> templates = new LinkedHashMap<>();
        int read = 0;
        for (final ExplorerNode node : documentAccess.children(folder.get())) {
            if (read >= MAX_SIBLINGS) {
                break;
            }
            final String sibling = node.getDocRef().getUuid();
            if (!PipelineDoc.TYPE.equals(node.getType()) || sibling.equals(pipeline.getUuid())
                || sibling.equals(predecessor)) {
                continue;
            }
            read++;
            if (!stage.equals(stage(sibling))) {
                continue;
            }
            read(sibling).filter(doc -> doc.getParentPipeline() != null).ifPresent(doc -> {
                // Only siblings that inherit: a template kept beside its children isn't one of them.
                final DocRef theirs = named(doc.getParentPipeline());
                final String key = NullSafe.string(uuidOf(theirs));
                templates.putIfAbsent(key, theirs);
                byTemplate.computeIfAbsent(key, k -> new ArrayList<>()).add(doc.getName());
            });
        }
        if (byTemplate.isEmpty() || byTemplate.containsKey(NullSafe.string(uuidOf(template)))) {
            return Optional.empty();
        }
        final List<String> theirs = new ArrayList<>();
        byTemplate.forEach((key, names) -> theirs.add(inheritWord(templates.get(key)) + " ("
                                                      + String.join(", ", names.stream().map(n -> "'" + n + "'")
                .toList()) + ")"));
        return Optional.of(inherits(pipeline.getName(), template) + "; the other " + stage + " pipelines in folder '"
                           + documentAccess.folderPath(folder.get()) + "' " + String.join(", and ", theirs));
    }

    // Each XSLT of its own on an element its template sets one on.
    private List<String> overrides(final PipelineDoc pipeline, final DocRef template) {
        if (template == null) {
            return List.of();
        }
        final MergedPipeline merged;
        final MergedPipeline parent;
        try {
            merged = pipelineShapes.merged(pipeline.getUuid());
            parent = pipelineShapes.merged(template.getUuid());
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No overrides read for {}: {}", pipeline.getUuid(), e.getMessage()));
            return List.of();
        }
        final List<String> out = new ArrayList<>();
        for (final TranslationDoc own : merged.translationDocs(pipeline.getUuid())) {
            if (own.inheritedFromTemplate() || !XsltDoc.TYPE.equals(own.doc().getType())) {
                continue;
            }
            if (parent.properties().get(new ElementProperty(own.element(), XSLT)) instanceof final Map<?, ?> theirs
                && !own.doc().getUuid().equals(String.valueOf(theirs.get("uuid")))) {
                out.add("'" + pipeline.getName() + "' sets its own XSLT '" + own.doc().getName() + "' on '"
                        + own.element() + "', where its template '" + template.getName() + "' supplies '"
                        + theirs.get("name") + "': it won't follow the template (a change to '" + theirs.get("name")
                        + "', or a template swapped in for this one, won't reach it)");
            }
        }
        return out;
    }

    private String stage(final String uuid) {
        try {
            return pipelineShapes.shape(uuid).stage();
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No stage read for {}: {}", uuid, e.getMessage()));
            return null;
        }
    }

    private Optional<PipelineDoc> read(final String uuid) {
        try {
            return documentAccess.read(new DocRef(PipelineDoc.TYPE, uuid), PipelineDoc.class);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Pipeline {} not read: {}", uuid, e.getMessage()));
            return Optional.empty();
        }
    }

    // A template with its name, read where the reference lacks it; null for none.
    private DocRef named(final DocRef parent) {
        if (parent == null || NullSafe.isBlankString(parent.getUuid())) {
            return null;
        }
        if (NullSafe.isNonBlankString(parent.getName())) {
            return parent;
        }
        return read(parent.getUuid())
                .map(doc -> new DocRef(PipelineDoc.TYPE, doc.getUuid(), doc.getName()))
                .orElse(parent);
    }

    private static String uuidOf(final DocRef ref) {
        return NullSafe.get(ref, DocRef::getUuid);
    }

    private static String inherits(final String name, final DocRef template) {
        return "'" + name + "' " + inheritsWord(template);
    }

    // As said of several: `inherit 'X'`.
    private static String inheritWord(final DocRef template) {
        return template == null
                ? "inherit no template"
                : "inherit '" + NullSafe.nonBlankStringElse(template.getName(), template.getUuid()) + "'";
    }

    private static String inheritsWord(final DocRef template) {
        return template == null
                ? "inherits no template"
                : "inherits '" + NullSafe.nonBlankStringElse(template.getName(), template.getUuid()) + "'";
    }


    // --------------------------------------------------------------------------------


    /// A pipeline's inheritance.
    ///
    /// @param pipeline The pipeline's name.
    /// @param template The template it inherits from, or null for none.
    /// @param flags    What differs, as facts: from its predecessor's or its siblings' template, and XSLTs of its
    ///                 own where its template supplies one.
    record Facts(String pipeline, DocRef template, List<String> flags) {

        /// @return It as results give it: `inherits` (`uuid`, `name`, or null for none) and `flags` (where any).
        Map<String, Object> toMap() {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("inherits", MergedPipeline.docRef(template));
            if (!flags.isEmpty()) {
                out.put("flags", flags);
            }
            return out;
        }

        /// @return It in words: what it inherits, then each flag.
        String said() {
            final String base = inherits(pipeline, template);
            final List<String> out = new ArrayList<>();
            out.add(base);
            // A flag starting with what it inherits says the rest.
            flags.forEach(flag -> out.add(flag.startsWith(base + "; ")
                    ? flag.substring(base.length() + 2)
                    : flag));
            return String.join("; ", out);
        }
    }
}
