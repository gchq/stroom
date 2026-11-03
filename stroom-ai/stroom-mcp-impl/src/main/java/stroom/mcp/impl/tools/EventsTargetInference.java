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
import stroom.ai.impl.tools.InstructionRules;
import stroom.ai.impl.tools.InstructionRules.RequiredImport;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.validation.EventsNamespaces;
import stroom.ai.impl.tools.validation.HeldSchemas;
import stroom.docref.DocRef;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/// The Events namespace a new translation should write, inferred from what the environment already writes: the
/// imports its standing instructions require of translations, then the XSLTs its translation template (and the
/// templates that template inherits from) sets. Seen in testing: an environment whose Events are all in a fork of the
/// event-logging schema (`file://xml/schema/accounting/events`), whose common import writes the fork; the agent asked
/// the user which namespace to write, and then dropped the import to write event-logging:3.
///
/// Only namespaces this Stroom holds Events schemas for count: `event-logging:3`, and the forks
/// ([HeldSchemas#forks()]).
class EventsTargetInference {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EventsTargetInference.class);

    private final HeldSchemas heldSchemas;
    private final Provider<RequiredImports> requiredImportsProvider;
    private final Provider<PipelineTemplates> pipelineTemplatesProvider;
    private final Provider<PipelineIndex> pipelineIndexProvider;
    private final DocumentAccess documentAccess;

    /// @param heldSchemas               The Events schemas this Stroom holds.
    /// @param requiredImportsProvider   The imports standing instructions require.
    /// @param pipelineTemplatesProvider The environment's templates.
    /// @param pipelineIndexProvider     Every pipeline the user can view.
    /// @param documentAccess            Reads the XSLTs, as the user.
    @Inject
    EventsTargetInference(final HeldSchemas heldSchemas,
                          final Provider<RequiredImports> requiredImportsProvider,
                          final Provider<PipelineTemplates> pipelineTemplatesProvider,
                          final Provider<PipelineIndex> pipelineIndexProvider,
                          final DocumentAccess documentAccess) {
        this.heldSchemas = heldSchemas;
        this.requiredImportsProvider = requiredImportsProvider;
        this.pipelineTemplatesProvider = pipelineTemplatesProvider;
        this.pipelineIndexProvider = pipelineIndexProvider;
        this.documentAccess = documentAccess;
    }

    /// @param session The session, for the audit log.
    /// @param build   The build, or null.
    /// @return The namespace the environment's translations write, and why; empty when nothing settles it (no
    /// fork held, or nothing that writes Events found), so the user is asked.
    Optional<Inferred> infer(final ToolSession session, final String build) {
        final Set<String> forks = new LinkedHashSet<>();
        heldSchemas.forks().forEach(doc -> forks.add(NullSafe.string(doc.getNamespaceURI()).strip()));
        if (forks.isEmpty()) {
            // Only event-logging:3 to write: nothing to infer.
            return Optional.empty();
        }
        final Set<String> namespaces = new HashSet<>(forks);
        namespaces.add(EventsNamespaces.STANDARD);
        final Optional<Inferred> fromImports = fromRequiredImports(session, build, namespaces);
        return fromImports.isPresent()
                ? fromImports
                : fromTemplate(namespaces);
    }

    // The namespace the first required import that writes Events elements writes them in.
    private Optional<Inferred> fromRequiredImports(final ToolSession session,
                                                   final String build,
                                                   final Set<String> namespaces) {
        final RequiredImports requiredImports = requiredImportsProvider.get();
        for (final RequiredImport required : requiredImports.requiredFor(session, InstructionRules.TRANSLATION,
                build, List.of())) {
            final Optional<String> written = requiredImports.named(required.href())
                    .flatMap(doc -> EventsNamespaces.writtenBy(doc.getData(), namespaces));
            if (written.isPresent()) {
                return Optional.of(new Inferred(written.get(), "the import '" + required.href() + "' the standing "
                                                               + "instructions require of translations writes it"));
            }
        }
        return Optional.empty();
    }

    // The namespace the XSLTs the best translation template (or one it inherits from) sets write in.
    private Optional<Inferred> fromTemplate(final Set<String> namespaces) {
        try {
            final Map<String, Object> found = pipelineTemplatesProvider.get().find(InstructionRules.TRANSLATION);
            if (!(found.get("candidates") instanceof final List<?> candidates) || candidates.isEmpty()
                || !(candidates.getFirst() instanceof final Map<?, ?> best)) {
                return Optional.empty();
            }
            final Map<String, PipelineIndex.Entry> index = pipelineIndexProvider.get().read();
            final Set<String> seen = new HashSet<>();
            String uuid = Objects.toString(best.get("uuid"), null);
            while (uuid != null && seen.add(uuid) && index.containsKey(uuid)) {
                final PipelineIndex.Entry pipeline = index.get(uuid);
                for (final DocRef xslt : xslts(pipeline.own())) {
                    final Optional<String> written = documentAccess.read(xslt, XsltDoc.class)
                            .flatMap(doc -> EventsNamespaces.writtenBy(doc.getData(), namespaces));
                    if (written.isPresent()) {
                        return Optional.of(new Inferred(written.get(), "the XSLT '" + xslt.getName() + "' set on "
                                                                       + "the translation template '"
                                                                       + pipeline.name() + "' writes it"));
                    }
                }
                uuid = pipeline.parentUuid();
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No namespace inferred from the templates: {}", e.getMessage()), e);
        }
        return Optional.empty();
    }

    // The XSLT documents a pipeline's own properties name.
    private static List<DocRef> xslts(final PipelineData own) {
        final List<DocRef> out = new ArrayList<>();
        for (final PipelineProperty property : NullSafe.list(NullSafe.get(own, PipelineData::getAddedProperties))) {
            final DocRef entity = NullSafe.get(property, PipelineProperty::getValue, value -> value.getEntity());
            if (entity != null && XsltDoc.TYPE.equals(entity.getType())) {
                out.add(entity);
            }
        }
        return out;
    }


    // --------------------------------------------------------------------------------


    /// A namespace inferred, and why.
    ///
    /// @param namespace `event-logging:3`, or a fork's.
    /// @param why       What writes it, for the agent and the user.
    record Inferred(String namespace, String why) {

    }
}
