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

package stroom.ai.impl.tools;

import stroom.docref.DocRef;
import stroom.docstore.api.DocDependencyService;
import stroom.docstore.api.DocFinder;
import stroom.documentation.shared.DocumentationDoc;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.pipeline.shared.PipelineDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// The documentation that says what a data source's fields hold, which its field names often don't: e.g. that an
/// index's `document.description` is a tweet's text. Gathered from where people and the Stroom MCP server write it:
///
/// - the data source's own Documentation tab;
/// - the Documentation doc beside it with the same name (where the MCP server documents an existing index);
/// - for each pipeline that feeds it, the pipeline's Documentation tab and the Documentation doc beside the pipeline
///   with its name (where the MCP server documents a pipeline it builds, with a field mapping from the source's
///   fields to the index's);
/// - for a View, the same for the index or pipeline it reads.
///
/// Read as the user: every document is checked for View, links in the dependency graph are checked at both ends,
/// and what can't be read is left out without saying it exists. Every check fails closed.
public class DataSourceDocumentation {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataSourceDocumentation.class);

    /// The most characters of documentation given for one data source.
    static final int MAX_CHARS = 20_000;
    /// The most characters of one document's documentation given.
    static final int MAX_CHARS_PER_DOC = 8_000;
    /// The most pipelines feeding a data source whose documentation is read.
    static final int MAX_PIPELINES = 5;

    private final DocumentAccess documentAccess;
    private final DataSourceAccess dataSourceAccess;
    private final Provider<DocFinder> docFinderProvider;
    private final Provider<DocDependencyService> docDependencyServiceProvider;

    @Inject
    DataSourceDocumentation(final DocumentAccess documentAccess,
                            final DataSourceAccess dataSourceAccess,
                            final Provider<DocFinder> docFinderProvider,
                            final Provider<DocDependencyService> docDependencyServiceProvider) {
        this.documentAccess = documentAccess;
        this.dataSourceAccess = dataSourceAccess;
        this.docFinderProvider = docFinderProvider;
        this.docDependencyServiceProvider = docDependencyServiceProvider;
    }

    /// Gathers a data source's documentation, and records in the audit log the documents read for it.
    ///
    /// @param chatId     The chat.
    /// @param dataSource A data source the user can use.
    /// @param purpose    Why, e.g. "Documentation read by the describe_data_source tool".
    /// @return Each piece of documentation, in the order above, with what it documents. Empty if there is none.
    public List<Documented> find(final int chatId, final DocRef dataSource, final String purpose) {
        final Map<String, Documented> found = new LinkedHashMap<>();
        // The data source's own Documentation tab, which Use is enough to see, as the query editor shows it.
        dataSourceAccess.documentation(dataSource).ifPresent(text ->
                found.put("tab:" + dataSource.getUuid(), new Documented(dataSource, dataSource,
                        "its Documentation tab", text)));
        besideDoc(dataSource, dataSource).ifPresent(doc -> found.putIfAbsent(doc.from().getUuid(), doc));

        final List<DocRef> sources = new ArrayList<>();
        sources.add(dataSource);
        // A View reads an index or a pipeline's output: theirs say what its fields hold too.
        if ("View".equals(dataSource.getType())) {
            linked(dataSource, false).stream()
                    .filter(docRef -> !PipelineDoc.TYPE.equals(docRef.getType()))
                    .forEach(source -> {
                        sources.add(source);
                        ownTab(source).ifPresent(doc -> found.putIfAbsent("tab:" + source.getUuid(), doc));
                        besideDoc(source, source).ifPresent(doc -> found.putIfAbsent(doc.from().getUuid(), doc));
                    });
        }
        final List<DocRef> pipelines = new ArrayList<>();
        for (final DocRef source : sources) {
            // Pipelines that write to an index depend on it; a View depends on the pipeline it uses.
            final boolean view = "View".equals(source.getType());
            for (final DocRef pipeline : linked(source, !view)) {
                if (PipelineDoc.TYPE.equals(pipeline.getType()) && !pipelines.contains(pipeline)
                    && pipelines.size() < MAX_PIPELINES) {
                    pipelines.add(pipeline);
                }
            }
        }
        for (final DocRef pipeline : pipelines) {
            ownTab(pipeline).ifPresent(doc -> found.putIfAbsent("tab:" + pipeline.getUuid(), doc));
            besideDoc(pipeline, pipeline).ifPresent(doc -> found.putIfAbsent(doc.from().getUuid(), doc));
        }

        final List<Documented> docs = List.copyOf(found.values());
        final List<DocRef> read = docs.stream()
                .map(Documented::from)
                .filter(docRef -> !docRef.equals(dataSource))
                .distinct()
                .toList();
        if (!read.isEmpty()) {
            documentAccess.logViews(chatId, read, purpose);
        }
        return docs;
    }

    /// @param docs The data source's documentation, as [#find(int, DocRef, String)] gave it.
    /// @return It, for the agent, at most [#MAX_CHARS] in all; empty if there is none.
    public static String note(final List<Documented> docs) {
        if (docs.isEmpty()) {
            return "";
        }
        final StringBuilder sb = new StringBuilder("\n\nDocumentation (what its fields hold, e.g. a field "
                                                   + "mapping from the source's fields; use it to pick fields):");
        int used = 0;
        for (final Documented doc : docs) {
            sb.append("\n\n### ").append(DocumentInfoTool.link(doc.from())).append(" (")
                    .append(doc.from().getType()).append("): ").append(doc.what());
            if (!doc.from().equals(doc.of())) {
                sb.append(" of ").append(doc.of().getType()).append(" '").append(doc.of().getName()).append('\'');
            }
            sb.append('\n');
            final int room = Math.max(0, Math.min(MAX_CHARS_PER_DOC, MAX_CHARS - used));
            final String text = doc.text().strip();
            sb.append(text, 0, Math.min(text.length(), room));
            if (text.length() > room) {
                sb.append("\n[cut short; read the rest with read_document or document_documentation, uuid ")
                        .append(doc.from().getUuid()).append(']');
            }
            used += Math.min(text.length(), room);
        }
        return sb.toString();
    }

    // A document's Documentation tab, if the user can view it and it has one.
    private Optional<Documented> ownTab(final DocRef docRef) {
        if (!documentAccess.canView(docRef)) {
            return Optional.empty();
        }
        return documentAccess.read(docRef)
                .flatMap(DocumentDocumentationTool::description)
                .filter(NullSafe::isNonBlankString)
                .map(text -> new Documented(docRef, docRef, "its Documentation tab", text));
    }

    // The Documentation doc with the same name as a document, in the same folder, if the user can view it.
    private Optional<Documented> besideDoc(final DocRef docRef, final DocRef of) {
        if (NullSafe.isBlankString(docRef.getName())) {
            return Optional.empty();
        }
        try {
            final String folder = AgentInstructions.normalise(documentAccess.path(docRef));
            for (final DocRef doc : NullSafe.list(docFinderProvider.get().findByName(DocumentationDoc.TYPE,
                    docRef.getName(), false))) {
                if (docRef.getName().equals(doc.getName())
                    && folder.equals(AgentInstructions.normalise(documentAccess.path(doc)))) {
                    final Optional<String> text = documentAccess.read(doc, DocumentationDoc.class)
                            .map(DocumentationDoc::getData)
                            .filter(NullSafe::isNonBlankString);
                    if (text.isPresent()) {
                        return Optional.of(new Documented(doc, of, "the Documentation doc beside it",
                                text.get()));
                    }
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to find the documentation beside " + docRef, e);
        }
        return Optional.empty();
    }

    /// @param docRef       A document.
    /// @param toDependants True for the documents that use it, false for those it uses.
    /// @return Them, each checked for View at both ends, as the dependency graph's own check lets a link through
    /// if checking fails.
    private List<DocRef> linked(final DocRef docRef, final boolean toDependants) {
        final String qualifier = (toDependants
                ? DependencyCriteria.FIELD_DEF_TO_UUID
                : DependencyCriteria.FIELD_DEF_FROM_UUID).getFilterQualifier();
        try {
            final ResultPage<Dependency> page = docDependencyServiceProvider.get().fetchDependencies(
                    new DependencyCriteria(new PageRequest(0, 100), null, qualifier + ":=" + docRef.getUuid()),
                    Set.of());
            final List<DocRef> linked = new ArrayList<>();
            for (final Dependency dependency : NullSafe.list(NullSafe.get(page, ResultPage::getValues))) {
                final DocRef self = toDependants
                        ? dependency.getTo()
                        : dependency.getFrom();
                final DocRef other = toDependants
                        ? dependency.getFrom()
                        : dependency.getTo();
                if (self != null && other != null && docRef.getUuid().equals(self.getUuid())
                    && documentAccess.canView(other) && !linked.contains(other)) {
                    linked.add(other);
                }
            }
            return linked;
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to read the links of " + docRef, e);
            return List.of();
        }
    }


    // --------------------------------------------------------------------------------


    /// A piece of a data source's documentation.
    ///
    /// @param from The document it is in, e.g. a Documentation doc or a pipeline.
    /// @param of   The document it documents, e.g. the data source or a pipeline that feeds it.
    /// @param what Where in `from` it is, e.g. "its Documentation tab".
    /// @param text The documentation.
    public record Documented(DocRef from, DocRef of, String what, String text) {

    }
}
