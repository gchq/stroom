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
import stroom.ai.impl.tools.documents.KeptMapping;
import stroom.ai.impl.tools.draft.SourceNotes;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.documentation.shared.DocumentationDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// The hidden link from a build's translation XSLT to its source notes doc ([KeptMapping#SOURCE_NOTES], decided
/// with the user 2026-11-25): the notes doc's UUID, so the event type catalogue is found from the XSLT wherever
/// either is moved. Written wherever the tools write a translation's mapping block and the build has source notes,
/// and again once notes are made or carried into the build after its translation was written. Notes in a build
/// that are a working copy of promoted notes are linked by the promoted doc's UUID, which promotion writes the copy
/// back over.
final class SourceNotesLinks {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SourceNotesLinks.class);

    private SourceNotesLinks() {
        // Static only.
    }

    /// @param guard The build's documents.
    /// @param build A build, or null.
    /// @return Its source notes doc as a translation links to it: the promoted doc a working copy is of, else the
    /// build's own; empty when the build has none.
    static Optional<DocRef> notesOf(final WriteGuard guard, final String build) {
        if (NullSafe.isBlankString(build)) {
            return Optional.empty();
        }
        try {
            return guard.folderContents(build).stream()
                    .filter(doc -> DocumentationDoc.TYPE.equals(doc.type())
                                   && NullSafe.string(doc.name()).endsWith(SourceNotes.NOTES_SUFFIX))
                    .findFirst()
                    .map(doc -> new DocRef(DocumentationDoc.TYPE, guard.copyOf(doc.docRef()).orElse(doc.uuid()),
                            doc.name()));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("The source notes of build {} weren't found: {}", build,
                    e.getMessage()), e);
            return Optional.empty();
        }
    }

    /// @param guard       The build's documents.
    /// @param build       The build, or null.
    /// @param description A translation XSLT's description, with its mapping block.
    /// @return It linking to the build's source notes, where the build has them; else as it is.
    static String linked(final WriteGuard guard, final String build, final String description) {
        return notesOf(guard, build)
                .map(notes -> KeptMapping.withSourceNotes(description, notes))
                .orElse(NullSafe.string(description));
    }

    /// Links every translation XSLT of the build (one keeping a translation mapping) to the build's source notes,
    /// where its link is missing or names other notes: for notes made, or carried in, after the translation was
    /// written. Only the hidden block changes; the XSLT's code and the user's own text are kept. A link that can't be
    /// written is left for the next write of the mapping.
    ///
    /// @param guard          The build's documents.
    /// @param documentAccess Reads and writes the XSLTs, as the user.
    /// @param build          The build.
    /// @return The XSLTs linked.
    static List<DocRef> linkTranslations(final WriteGuard guard,
                                         final DocumentAccess documentAccess,
                                         final String build) {
        final List<DocRef> linked = new ArrayList<>();
        final Optional<DocRef> notes = notesOf(guard, build);
        if (notes.isEmpty()) {
            return linked;
        }
        try {
            for (final WriteGuard.BuildDoc doc : guard.folderContents(build)) {
                if (!XsltDoc.TYPE.equals(doc.type())) {
                    continue;
                }
                final Optional<XsltDoc> xslt = documentAccess.read(doc.docRef(), XsltDoc.class);
                if (xslt.isEmpty() || !KeptMapping.readMapping(xslt.get().getDescription())
                        .filter(kept -> KeptMapping.TRANSLATION.equals(kept.kind()))
                        .isPresent()) {
                    continue;
                }
                final String described = KeptMapping.withSourceNotes(xslt.get().getDescription(), notes.get());
                if (Objects.equals(described, xslt.get().getDescription())) {
                    continue;
                }
                if (write(documentAccess, xslt.get().copy().description(described).build())) {
                    linked.add(doc.docRef());
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("The translations of build {} weren't linked to their source "
                                               + "notes: {}", build, e.getMessage()), e);
        }
        return linked;
    }

    // Writes the XSLT through its store; false where Stroom has none.
    @SuppressWarnings("unchecked")
    private static boolean write(final DocumentAccess documentAccess, final XsltDoc doc) {
        final Optional<DocumentActionHandler> handler = documentAccess.handler(XsltDoc.TYPE);
        handler.ifPresent(store -> store.writeDocument(doc));
        return handler.isPresent();
    }
}
