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
import stroom.docstore.api.ContentIndex;
import stroom.docstore.api.DocFinder;
import stroom.explorer.shared.DocContentMatch;
import stroom.explorer.shared.FindInContentRequest;
import stroom.explorer.shared.StringMatch;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// Every pipeline the user can view, with its name, folder and parent, as the template tools read them. Stroom has
/// no "children of" query, so each pipeline document is read once per call. It is read afresh on every call (the
/// documents are read in-process), so a pipeline made a moment ago is always in it.
class PipelineIndex {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PipelineIndex.class);

    /// The Explorer tree's root, as paths start.
    static final String SYSTEM = "System";

    /// The most matches asked of the content index.
    static final int MAX_CONTENT_MATCHES = 5_000;

    private final Provider<DocFinder> docFinderProvider;
    private final DocumentAccess documentAccess;
    private final Provider<ContentIndex> contentIndexProvider;

    /// @param docFinderProvider Lists the pipelines.
    /// @param documentAccess    Checks View on each, and reads it.
    PipelineIndex(final Provider<DocFinder> docFinderProvider, final DocumentAccess documentAccess) {
        this(docFinderProvider, documentAccess, () -> null);
    }

    /// @param docFinderProvider    Lists the pipelines.
    /// @param documentAccess       Checks View on each, and reads it.
    /// @param contentIndexProvider Finds documents by their content.
    @Inject
    PipelineIndex(final Provider<DocFinder> docFinderProvider,
                  final DocumentAccess documentAccess,
                  final Provider<ContentIndex> contentIndexProvider) {
        this.docFinderProvider = docFinderProvider;
        this.documentAccess = documentAccess;
        this.contentIndexProvider = contentIndexProvider;
    }

    /// @return Every pipeline the user can view, by UUID, in name order. A pipeline that can't be read (e.g. deleted
    /// since it was listed) is left out.
    Map<String, Entry> read() {
        final Map<String, Entry> index = new LinkedHashMap<>();
        for (final DocRef docRef : list(PipelineDoc.TYPE)) {
            final Optional<PipelineDoc> doc = documentAccess.read(docRef, PipelineDoc.class);
            if (doc.isEmpty()) {
                continue;
            }
            final PipelineDoc pipeline = doc.get();
            index.put(docRef.getUuid(), new Entry(docRef.getUuid(),
                    NullSafe.nonBlankStringElse(pipeline.getName(), docRef.getName()),
                    path(documentAccess, docRef),
                    NullSafe.get(pipeline.getParentPipeline(), DocRef::getUuid),
                    pipeline.getPipelineData()));
        }
        return index;
    }

    /// @param type A document type.
    /// @return Every document of the type the user can view, in name order.
    List<DocRef> list(final String type) {
        try {
            return NullSafe.list(docFinderProvider.get().findByName(type, "*", true)).stream()
                    .filter(documentAccess::canView)
                    .sorted(Comparator.comparing((DocRef docRef) -> NullSafe.string(docRef.getName()))
                            .thenComparing(DocRef::getUuid))
                    .toList();
        } catch (final RuntimeException e) {
            LOGGER.debug("Unable to list the documents of type {}", type, e);
            return List.of();
        }
    }

    /// @param type A document type.
    /// @param text Text the documents' content must contain, e.g. `reference-data:2`.
    /// @return The documents of the type the user can view whose content contains the text, in name order: found with
    /// Stroom's content index (the explorer's content search); while the index isn't ready, every document of the type,
    /// for the caller to look in itself.
    List<DocRef> containing(final String type, final String text) {
        final ContentIndex contentIndex = contentIndexProvider.get();
        try {
            if (contentIndex != null && contentIndex.isReady()) {
                final ResultPage<DocContentMatch> page = contentIndex.findInContent(new FindInContentRequest(
                        new PageRequest(0, MAX_CONTENT_MATCHES), null, StringMatch.contains(text, false)));
                return NullSafe.list(NullSafe.get(page, ResultPage::getValues)).stream()
                        .map(DocContentMatch::getDocRef)
                        .filter(docRef -> docRef != null && type.equals(docRef.getType()))
                        .distinct()
                        .filter(documentAccess::canView)
                        .sorted(Comparator.comparing((DocRef docRef) -> NullSafe.string(docRef.getName()))
                                .thenComparing(DocRef::getUuid))
                        .toList();
            }
        } catch (final RuntimeException e) {
            LOGGER.debug("Unable to search the content index for '{}'", text, e);
        }
        return list(type);
    }

    /// @param type    A document type.
    /// @param pattern A name, with `*` for any characters, e.g. `*CEF*`.
    /// @param limit   The most to give.
    /// @return The documents of the type so named that the user can view, in name order; none if they can't be listed.
    List<DocRef> named(final String type, final String pattern, final int limit) {
        try {
            return NullSafe.list(docFinderProvider.get().findByName(type, pattern, true)).stream()
                    .filter(documentAccess::canView)
                    .sorted(Comparator.comparing((DocRef docRef) -> NullSafe.string(docRef.getName()))
                            .thenComparing(DocRef::getUuid))
                    .limit(limit)
                    .toList();
        } catch (final RuntimeException e) {
            LOGGER.debug("Unable to find the documents of type {} named {}", type, pattern, e);
            return List.of();
        }
    }

    /// @param text  Text the documents' content must contain, e.g. `CEF:0`.
    /// @param limit The most matches asked of the content index.
    /// @return The documents of any type the user can view whose content contains it (any case), in the content index's
    /// order; none while the index is still being built, or if it can't be searched. As the explorer's content search
    /// does, the search itself starts the index's first build on this node (and waits a moment for it), so a later call
    /// finds what it holds: seen in testing, asked only once it was ready, an index nothing else had started was never
    /// searched.
    List<DocRef> withContent(final String text, final int limit) {
        final ContentIndex contentIndex = contentIndexProvider.get();
        try {
            if (contentIndex == null) {
                return List.of();
            }
            final ResultPage<DocContentMatch> page = contentIndex.findInContent(new FindInContentRequest(
                    new PageRequest(0, limit), null, StringMatch.contains(text, false)));
            return NullSafe.list(NullSafe.get(page, ResultPage::getValues)).stream()
                    .map(DocContentMatch::getDocRef)
                    .filter(docRef -> docRef != null && documentAccess.canView(docRef))
                    .distinct()
                    .toList();
        } catch (final RuntimeException e) {
            LOGGER.debug("Unable to search the content index for '{}'", text, e);
            return List.of();
        }
    }

    /// @param documentAccess Finds the folder.
    /// @param docRef         A document the user can view.
    /// @return The Explorer path of its folder from System, e.g. `System/Template Pipelines`, as the template
    /// folders are configured.
    static String path(final DocumentAccess documentAccess, final DocRef docRef) {
        final String path = documentAccess.path(docRef);
        return path == null || "/".equals(path)
                ? SYSTEM
                : SYSTEM + path;
    }


    // --------------------------------------------------------------------------------


    /// One pipeline.
    ///
    /// @param uuid       Its UUID.
    /// @param name       Its name.
    /// @param path       Its folder, e.g. `System/Template Pipelines`.
    /// @param parentUuid The UUID of the pipeline it inherits from, or null.
    /// @param own        What it adds to, or removes from, what it inherits (its own layer), or null.
    record Entry(String uuid, String name, String path, String parentUuid, PipelineData own) {

    }
}
