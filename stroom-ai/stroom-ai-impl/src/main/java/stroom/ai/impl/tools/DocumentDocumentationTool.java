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
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.security.shared.DocumentPermission;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;

import java.util.Map;
import java.util.Optional;

/// `document_documentation` - the Markdown on a Stroom document's Documentation tab, read as the user. The tab edits
/// the document's own `description` field, which each type declares for itself, so the document is read through
/// its type's handler and the field taken from it. A document the user can't view is refused without saying
/// whether it exists.
class DocumentDocumentationTool extends AbstractWorkspaceTool {

    static final String NAME = "document_documentation";
    static final String UUID_ARG = "uuid";
    /// The field the Documentation tab edits.
    static final String DESCRIPTION_FIELD = "description";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DocumentDocumentationTool.class);

    private final DocumentAccess documentAccess;

    @Inject
    DocumentDocumentationTool(final ChatWorkspace workspace, final DocumentAccess documentAccess) {
        super(workspace);
        this.documentAccess = documentAccess;
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
                        .general("Reads the documentation of a Stroom document given its UUID: the Markdown on its "
                                 + "Documentation tab, or 'none' if it has none.")
                        .chatOnly(" Use read_document for the document's content (including a Documentation "
                                  + "document's text).")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(UUID_ARG, "The document's UUID.")
                        .required(UUID_ARG)
                        .build())
                .build();
    }

    @Override
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
        final SessionWorkspace workspace = sessionWorkspace(context);
        final String uuid = stringArg(request, UUID_ARG);
        if (uuid == null) {
            throw new WorkspaceException("Give the document's uuid.");
        }
        final ExplorerNode node = documentAccess.find(uuid).orElse(null);
        if (node == null) {
            return ToolResult.text(DocumentAccess.NO_PERMISSION);
        }
        final DocRef docRef = node.getDocRef();
        documentAccess.logView(context.session(), docRef, "Documentation read by the " + NAME + " tool");
        final Optional<Object> doc = documentAccess.read(docRef);
        if (doc.isEmpty()) {
            return ToolResult.text("Error: " + ReadDocumentTool.describe(docRef) + " couldn't be read.");
        }
        final String documentation = description(doc.get()).orElse(null);
        if (documentation == null || documentation.isBlank()) {
            return ToolResult.text("none");
        }
        if (documentation.length() <= limits(context).getMaxCharsPerRead()) {
            return ToolResult.text(
                    "Documentation of " + ReadDocumentTool.describe(docRef) + ":\n\n" + documentation.strip());
        }
        final WorkspacePath path = ReadDocumentTool.documentPath(docRef, "documentation.md");
        workspace.writeDocument(path, documentation, docRef.getUuid());
        return ToolResult.text(
                "The documentation of " + ReadDocumentTool.describe(docRef) + " is too large to show in full ("
                + size(documentation.length()) + "), so it has been saved to " + path + ".\n\n"
                + ReadDocumentTool.previewNote(documentation));
    }

    /// @param doc A document, as its handler read it.
    /// @return Its `description` field, which its Documentation tab edits, if it has one.
    static Optional<String> description(final Object doc) {
        try {
            final Map<?, ?> fields = JsonUtil.readValue(JsonUtil.writeValueAsString(doc), Map.class);
            final Object description = fields == null
                    ? null
                    : fields.get(DESCRIPTION_FIELD);
            return description instanceof final String text
                    ? Optional.of(text)
                    : Optional.empty();
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to read the description of " + doc, e);
            return Optional.empty();
        }
    }
}
