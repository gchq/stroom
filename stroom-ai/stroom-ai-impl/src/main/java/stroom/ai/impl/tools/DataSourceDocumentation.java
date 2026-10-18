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
import stroom.explorer.shared.ExplorerNode;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/// The documentation that says what a data source's fields hold, which its field names often don't: e.g. that an
/// index's `document.description` is a tweet's text. Gathered from where people and the Stroom MCP server write it:
///
/// - the data source's own Documentation tab;
/// - the Documentation docs in its folder that are about it: named after it (where the MCP server documents an
///   existing index) or after a pipeline that feeds it (where it documents an index's build), or that mention it;
/// - for each pipeline that feeds it, the pipeline's Documentation tab and the Documentation doc beside the pipeline
///   with its name (where the MCP server documents a pipeline it builds, with a field mapping from the source's
///   fields to the index's);
/// - for a View, the same for the index or pipeline it reads.
///
/// The pipelines that feed an index are those that depend on it, and for an Elastic index those that use its
/// cluster and write to its Elasticsearch index by name (`indexName`), as an Elastic indexing filter does without
/// naming the index doc.
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
    /// The most pipelines using an Elastic index's cluster that are read to find those writing to it.
    static final int MAX_CLUSTER_PIPELINES = 100;
    /// The most Documentation docs in a data source's folder that are read to find those about it.
    static final int MAX_FOLDER_DOCS = 50;
    /// The most docs in its folder given only because they mention it (not named after it or a pipeline).
    static final int MAX_MENTIONING_DOCS = 3;
    /// The property of an Elastic indexing filter that names the Elasticsearch index it writes to.
    static final String INDEX_NAME_PROPERTY = "indexName";

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

        final List<DocRef> sources = new ArrayList<>();
        sources.add(dataSource);
        // A View reads an index or a pipeline's output: theirs say what its fields hold too.
        if ("View".equals(dataSource.getType())) {
            linked(dataSource, false).stream()
                    .filter(docRef -> !PipelineDoc.TYPE.equals(docRef.getType()))
                    .forEach(source -> {
                        sources.add(source);
                        ownTab(source, "the Documentation tab of the index it reads")
                                .ifPresent(doc -> found.putIfAbsent("tab:" + source.getUuid(), doc));
                    });
        }
        final List<DocRef> pipelines = new ArrayList<>();
        final List<String> mentions = new ArrayList<>();
        for (final DocRef source : sources) {
            // Pipelines that write to an index depend on it; a View depends on the pipeline it uses.
            final boolean view = "View".equals(source.getType());
            final List<DocRef> feeding = new ArrayList<>(linked(source, !view));
            if (ElasticIndexDoc.TYPE.equals(source.getType())) {
                feeding.addAll(elasticPipelines(source, mentions));
            }
            for (final DocRef pipeline : feeding) {
                if (PipelineDoc.TYPE.equals(pipeline.getType()) && !pipelines.contains(pipeline)
                    && pipelines.size() < MAX_PIPELINES) {
                    pipelines.add(pipeline);
                }
            }
        }
        for (final DocRef source : sources) {
            folderDocs(source, pipelines, mentions).forEach(doc -> found.putIfAbsent(doc.from().getUuid(), doc));
        }
        for (final DocRef pipeline : pipelines) {
            ownTab(pipeline, "the Documentation tab of a pipeline that writes to it")
                    .ifPresent(doc -> found.putIfAbsent("tab:" + pipeline.getUuid(), doc));
            besideDoc(pipeline).ifPresent(doc -> found.putIfAbsent(doc.from().getUuid(), doc));
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

    /// @param index    An Elastic index doc.
    /// @param mentions Given the Elasticsearch index name, to look for in documentation.
    /// @return The pipelines the user can view that use its cluster and write to its Elasticsearch index by name.
    /// Empty if the user can't view the index doc, as its settings can't then be read.
    private List<DocRef> elasticPipelines(final DocRef index, final List<String> mentions) {
        final ElasticIndexDoc doc = documentAccess.read(index, ElasticIndexDoc.class).orElse(null);
        if (doc == null || NullSafe.isBlankString(doc.getIndexName()) || doc.getClusterRef() == null) {
            return List.of();
        }
        mentions.add(doc.getIndexName());
        final Pattern indexName = wildcard(doc.getIndexName());
        final List<DocRef> writing = new ArrayList<>();
        final List<DocRef> usingCluster = linked(doc.getClusterRef(), true).stream()
                .filter(docRef -> PipelineDoc.TYPE.equals(docRef.getType()))
                .limit(MAX_CLUSTER_PIPELINES)
                .toList();
        for (final DocRef pipeline : usingCluster) {
            final List<PipelineProperty> properties = documentAccess.read(pipeline, PipelineDoc.class)
                    .map(PipelineDoc::getPipelineData)
                    .map(PipelineData::getAddedProperties)
                    .orElse(List.of());
            final boolean writes = NullSafe.list(properties).stream()
                    .filter(property -> INDEX_NAME_PROPERTY.equals(property.getName()))
                    .map(property -> NullSafe.get(property.getValue(), PipelinePropertyValue::getString))
                    .anyMatch(name -> name != null && indexName.matcher(name).matches());
            if (writes) {
                writing.add(pipeline);
            }
        }
        return writing;
    }

    // An Elasticsearch index name, which may have `*` wild cards, as a pattern matching the names it covers.
    private static Pattern wildcard(final String indexName) {
        final StringBuilder sb = new StringBuilder();
        for (final String part : indexName.strip().split("\\*", -1)) {
            if (!sb.isEmpty()) {
                sb.append(".*");
            }
            sb.append(Pattern.quote(part));
        }
        return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
    }

    /// @param source    A data source, or the index a View reads.
    /// @param pipelines The pipelines that feed it.
    /// @param mentions  Other names for it, e.g. its Elasticsearch index name.
    /// @return The Documentation docs in its folder the user can view that are named after it or a pipeline that
    /// feeds it, or whose text mentions its name, UUID or other names.
    private List<Documented> folderDocs(final DocRef source,
                                        final List<DocRef> pipelines,
                                        final List<String> mentions) {
        final DocRef folder = documentAccess.parent(source).orElse(null);
        if (folder == null) {
            return List.of();
        }
        final Set<String> names = new HashSet<>();
        NullSafe.consume(source.getName(), names::add);
        pipelines.forEach(pipeline -> NullSafe.consume(pipeline.getName(), names::add));
        final List<String> words = new ArrayList<>(mentions);
        NullSafe.consume(source.getName(), words::add);
        words.add(source.getUuid());

        final List<Documented> docs = new ArrayList<>();
        final List<Documented> mentioning = new ArrayList<>();
        final List<ExplorerNode> children = documentAccess.children(folder).stream()
                .filter(node -> DocumentationDoc.TYPE.equals(node.getDocRef().getType()))
                .limit(MAX_FOLDER_DOCS)
                .toList();
        for (final ExplorerNode child : children) {
            final DocRef docRef = child.getDocRef();
            final String text = documentAccess.read(docRef, DocumentationDoc.class)
                    .map(DocumentationDoc::getData)
                    .filter(NullSafe::isNonBlankString)
                    .orElse(null);
            if (text == null) {
                continue;
            }
            final boolean named = names.contains(docRef.getName());
            final String lower = text.toLowerCase(Locale.ROOT);
            final boolean mentioned = words.stream()
                    .filter(NullSafe::isNonBlankString)
                    .anyMatch(word -> lower.contains(word.toLowerCase(Locale.ROOT)));
            if (named) {
                docs.add(new Documented(docRef, source, "the Documentation doc beside it", text));
            } else if (mentioned && mentioning.size() < MAX_MENTIONING_DOCS) {
                mentioning.add(new Documented(docRef, source, "a Documentation doc beside it that mentions it",
                        text));
            }
        }
        // Those named after it first, as the most likely to be about it.
        docs.addAll(mentioning);
        return docs;
    }

    // A document's Documentation tab, if the user can view it and it has one.
    private Optional<Documented> ownTab(final DocRef docRef, final String what) {
        if (!documentAccess.canView(docRef)) {
            return Optional.empty();
        }
        return documentAccess.read(docRef)
                .flatMap(DocumentDocumentationTool::description)
                .filter(NullSafe::isNonBlankString)
                .map(text -> new Documented(docRef, docRef, what, text));
    }

    // The Documentation doc with the same name as a pipeline that writes to the data source, in the pipeline's folder,
    // if the user can view it.
    private Optional<Documented> besideDoc(final DocRef docRef) {
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
                        return Optional.of(new Documented(doc, docRef,
                                "the Documentation doc beside a pipeline that writes to it", text.get()));
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
