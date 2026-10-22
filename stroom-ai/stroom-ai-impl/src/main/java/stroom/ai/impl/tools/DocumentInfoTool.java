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

import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.docstore.api.DocDependencyService;
import stroom.docstore.shared.AbstractDoc;
import stroom.explorer.shared.ExplorerNode;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.security.shared.DocumentPermission;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/// `document_info` - what a Stroom document is and where it sits: its type, name, Explorer path, tags, who created
/// and last changed it and when, and the documents it depends on and that depend on it. Read as the user: a
/// document they can't view is refused without saying whether it exists, and each document at either end of a
/// dependency is checked for View again, leaving out any that can't be checked.
///
/// A document can be given by its Explorer path rather than its UUID (as the Stroom MCP server's `find_documents`
/// gave paths, with or without the System root), and its type checked, so a client that found documents there can
/// look them up here.
class DocumentInfoTool extends AbstractBuiltInTool {

    static final String NAME = "document_info";
    static final String UUID_ARG = "uuid";
    static final String PATH_ARG = "path";
    static final String TYPE_ARG = "type";
    /// The most dependencies, and the most dependants, listed.
    static final int MAX_LINKS = 50;

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DocumentInfoTool.class);

    private final DocumentAccess documentAccess;
    private final Provider<DocDependencyService> docDependencyServiceProvider;

    @Inject
    DocumentInfoTool(final DocumentAccess documentAccess,
                     final Provider<DocDependencyService> docDependencyServiceProvider) {
        this.documentAccess = documentAccess;
        this.docDependencyServiceProvider = docDependencyServiceProvider;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW)
                .build();
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CORE;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(ToolDescription.builder()
                        .general("Describes a Stroom document (e.g. a feed, pipeline, XSLT, dictionary, index or "
                                 + "dashboard) given its UUID: its type, name, Explorer path, tags, who created and "
                                 + "last changed it and when, its version, and the documents it depends on and that "
                                 + "depend on it, each as a link. Give its Explorer path instead of its UUID to look "
                                 + "it up by where it is, e.g. '/Feeds/Events/Acme/ACME-EVENTS'.")
                        .chatOnly(" Use read_document for its content and document_documentation for its "
                                  + "documentation, and folder_contents with name to find documents by name and "
                                  + "type.")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(UUID_ARG, "The document's UUID.")
                        .addStringProperty(PATH_ARG, "Instead of the uuid, the document's Explorer path: its "
                                                     + "folder's path and its name, e.g. "
                                                     + "'/Feeds/Events/Acme/ACME-EVENTS' (with or without the "
                                                     + "System root).")
                        .addStringProperty(TYPE_ARG, "Optional. The document's type, e.g. 'Feed' or 'Pipeline': "
                                                     + "with path, it tells apart documents of one name in a "
                                                     + "folder; with uuid, it is checked.")
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall request, final ToolCallContext context) {
        final String uuid = stringArg(request, UUID_ARG);
        final String path = stringArg(request, PATH_ARG);
        final String type = stringArg(request, TYPE_ARG);
        if (uuid == null && path == null) {
            return ToolResult.text("Error: give the document's uuid, or its path.");
        }
        final ExplorerNode node = (uuid != null
                ? documentAccess.find(uuid)
                : documentAccess.findByPath(path, type)).orElse(null);
        if (node == null) {
            return ToolResult.text(DocumentAccess.NO_PERMISSION);
        }
        final DocRef docRef = node.getDocRef();
        if (type != null && !type.equalsIgnoreCase(docRef.getType())) {
            return ToolResult.text("Error: " + docRef.getUuid() + " is a " + docRef.getType() + ", not a " + type
                                   + ".");
        }
        documentAccess.logView(context.session(), docRef, "Described by the " + NAME + " tool");

        final StringBuilder sb = new StringBuilder();
        sb.append(docRef.getType()).append(" '").append(docRef.getName()).append("'\n");
        sb.append("UUID: ").append(docRef.getUuid()).append('\n');
        final StroomLinks links = context.links();
        sb.append("Link: ").append(links.document(docRef)).append('\n');
        sb.append("Path: ").append(documentAccess.path(docRef)).append('\n');
        if (NullSafe.hasItems(node.getTags())) {
            sb.append("Tags: ").append(String.join(", ", node.getTags().stream().sorted().toList())).append('\n');
        }
        documentAccess.read(docRef)
                .filter(AbstractDoc.class::isInstance)
                .map(AbstractDoc.class::cast)
                .ifPresent(doc -> appendAudit(sb, doc));

        appendLinks(sb, links, "Dependencies (documents it uses)",
                DependencyCriteria.FIELD_DEF_FROM_UUID.getFilterQualifier(), docRef,
                Dependency::getFrom, Dependency::getTo);
        appendLinks(sb, links, "Dependants (documents that use it)",
                DependencyCriteria.FIELD_DEF_TO_UUID.getFilterQualifier(), docRef,
                Dependency::getTo, Dependency::getFrom);
        return ToolResult.text(sb.toString().stripTrailing());
    }

    private static void appendAudit(final StringBuilder sb, final AbstractDoc doc) {
        if (doc.getCreateTimeMs() != null || doc.getCreateUser() != null) {
            sb.append("Created: ").append(when(doc.getCreateTimeMs()))
                    .append(" by ").append(NullSafe.nonBlankStringElse(doc.getCreateUser(), "unknown")).append('\n');
        }
        if (doc.getUpdateTimeMs() != null || doc.getUpdateUser() != null) {
            sb.append("Updated: ").append(when(doc.getUpdateTimeMs()))
                    .append(" by ").append(NullSafe.nonBlankStringElse(doc.getUpdateUser(), "unknown")).append('\n');
        }
        if (NullSafe.isNonBlankString(doc.getVersion())) {
            sb.append("Version: ").append(doc.getVersion()).append('\n');
        }
    }

    // Lists the documents at the other end of the document's dependency edges, as links, checking each for View.
    private void appendLinks(final StringBuilder sb,
                             final StroomLinks links,
                             final String heading,
                             final String filterQualifier,
                             final DocRef docRef,
                             final Function<Dependency, DocRef> thisEnd,
                             final Function<Dependency, DocRef> otherEnd) {
        final List<DocRef> linked;
        try {
            linked = linked(filterQualifier, docRef, thisEnd, otherEnd);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to list the " + heading + " of " + docRef, e);
            sb.append('\n').append(heading).append(": couldn't be listed.\n");
            return;
        }
        sb.append('\n').append(heading).append(": ").append(linked.isEmpty()
                ? "none you can see"
                : linked.size()).append('\n');
        linked.stream()
                .limit(MAX_LINKS)
                .forEach(other -> sb.append("- ").append(links.document(other)).append(" (").append(other.getType())
                        .append(")\n"));
        if (linked.size() > MAX_LINKS) {
            sb.append("- … and ").append(linked.size() - MAX_LINKS).append(" more\n");
        }
    }

    private List<DocRef> linked(final String filterQualifier,
                                final DocRef docRef,
                                final Function<Dependency, DocRef> thisEnd,
                                final Function<Dependency, DocRef> otherEnd) {
        final DependencyCriteria criteria = new DependencyCriteria(PageRequest.unlimited(), null,
                filterQualifier + ":=" + docRef.getUuid());
        final List<Dependency> dependencies = NullSafe.list(NullSafe.get(
                docDependencyServiceProvider.get().fetchDependencies(criteria, Set.of()),
                page -> page.getValues()));
        // The service lets an edge through when checking it fails, so both ends are checked again here.
        final Predicate<Dependency> visible = dependency ->
                thisEnd.apply(dependency) != null
                && docRef.getUuid().equals(thisEnd.apply(dependency).getUuid())
                && documentAccess.canView(thisEnd.apply(dependency))
                && documentAccess.canView(otherEnd.apply(dependency));
        final Set<DocRef> linked = new LinkedHashSet<>();
        for (final Dependency dependency : dependencies) {
            if (visible.test(dependency)) {
                linked.add(otherEnd.apply(dependency));
            }
        }
        final List<DocRef> sorted = new ArrayList<>(linked);
        sorted.sort(Comparator.comparing((DocRef other) -> NullSafe.string(other.getType()))
                .thenComparing(other -> NullSafe.string(other.getName())));
        return sorted;
    }

    private static String when(final Long timeMs) {
        return timeMs == null
                ? "unknown"
                : DateUtil.createNormalDateTimeString(timeMs);
    }
}
