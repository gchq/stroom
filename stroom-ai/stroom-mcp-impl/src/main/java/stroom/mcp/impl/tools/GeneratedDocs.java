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
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.shared.ExplorerNode;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/// The Documentation docs generated for pipelines and indexes (tagged `mcp-generated`, named after the document
/// they describe), as describe_feed reads them. Drafts still in a build, below the build workspace folder, are
/// left out.
class GeneratedDocs {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GeneratedDocs.class);

    /// The tag generated documentation carries.
    static final String GENERATED = "mcp-generated";
    /// The folder builds are drafted in, below System (the Stroom MCP server's default workspace folder).
    static final String WORKSPACE_FOLDER = "MCP Workspace";

    private final Provider<DocFinder> docFinderProvider;
    private final DocumentAccess documentAccess;
    private final PipelineIndex pipelineIndex;

    /// @param docFinderProvider Finds Documentation docs by name.
    /// @param documentAccess    Checks View on each, reads it and its tags.
    /// @param pipelineIndex     Lists every Documentation doc.
    @Inject
    GeneratedDocs(final Provider<DocFinder> docFinderProvider,
                  final DocumentAccess documentAccess,
                  final PipelineIndex pipelineIndex) {
        this.docFinderProvider = docFinderProvider;
        this.documentAccess = documentAccess;
        this.pipelineIndex = pipelineIndex;
    }

    /// The Documentation doc named after a pipeline or index doc: the generated one (tagged mcp-generated) in its
    /// folder first, then any generated one of that name, then one written by hand beside it.
    ///
    /// @param name   The pipeline's or index's name.
    /// @param folder Its folder, e.g. `System/Feeds/Acme`, or null.
    /// @return The doc, or empty if there is none.
    Optional<Found> generatedDoc(final String name, final String folder) {
        final List<Found> named = named(name);
        final Optional<Found> generated = named.stream()
                .filter(Found::generated)
                .sorted(Comparator.comparing(found -> !Objects.equals(found.path(), folder)))
                // Not a draft in a build, unpromoted.
                .filter(found -> !isDraft(found.path()))
                .findFirst();
        if (generated.isPresent()) {
            return generated;
        }
        return named.stream()
                .filter(found -> Objects.equals(found.path(), folder))
                .findFirst()
                .map(found -> new Found(found.docRef(), false, folder));
    }

    /// @param name A feed's name.
    /// @param max  The most docs to give.
    /// @return The generated docs whose names hold the name (pipelines are named after their feed), that aren't
    /// drafts in a build.
    List<Found> generatedNamedLike(final String name, final int max) {
        final String want = name.toLowerCase(Locale.ROOT);
        return pipelineIndex.list(DocumentationDoc.TYPE).stream()
                .filter(docRef -> NullSafe.string(docRef.getName()).toLowerCase(Locale.ROOT).contains(want))
                .map(this::found)
                .filter(Found::generated)
                .filter(found -> !isDraft(found.path()))
                .limit(max)
                .toList();
    }

    /// @param docRef A Documentation doc the user can view.
    /// @return Its body, or null when Stroom no longer has it (seen: a search still listing a deleted doc).
    String text(final DocRef docRef) {
        return documentAccess.read(docRef, DocumentationDoc.class)
                .map(doc -> NullSafe.string(doc.getData()))
                .orElse(null);
    }

    private List<Found> named(final String name) {
        try {
            return NullSafe.list(docFinderProvider.get().findByName(DocumentationDoc.TYPE, name)).stream()
                    .filter(docRef -> name.equals(docRef.getName()))
                    .filter(documentAccess::canView)
                    .map(this::found)
                    .toList();
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to find the documentation named " + name, e);
            return List.of();
        }
    }

    private Found found(final DocRef docRef) {
        final boolean generated = documentAccess.node(docRef)
                .map(ExplorerNode::getTags)
                .map(tags -> tags.contains(GENERATED))
                .orElse(false);
        return new Found(docRef, generated, PipelineIndex.path(documentAccess, docRef));
    }

    private static boolean isDraft(final String path) {
        return NullSafe.string(path).startsWith(PipelineIndex.SYSTEM + "/" + WORKSPACE_FOLDER);
    }


    // --------------------------------------------------------------------------------


    /// A Documentation doc found.
    ///
    /// @param docRef    The doc.
    /// @param generated True if it is tagged as generated.
    /// @param path      Its folder.
    record Found(DocRef docRef, boolean generated, String path) {

    }
}
