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

import stroom.ai.impl.AiAuditLog;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentTypeName;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/// How the chat finds, reads and audits Stroom documents, as the user: the document tools, and documents the user
/// attaches to a chat. Every document is checked for View before anything about it is given, and a document the
/// user can't view is treated exactly as one that doesn't exist, so nothing reveals whether it does.
public class DocumentAccess {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DocumentAccess.class);

    /// What a tool says for a document the user can't view, or that doesn't exist, which it doesn't tell apart.
    public static final String NO_PERMISSION = "You don't have permission to view this document.";

    private final Provider<ExplorerNodeService> explorerNodeServiceProvider;
    private final SecurityContext securityContext;
    @SuppressWarnings("rawtypes")
    private final Provider<Map<DocumentTypeName, DocumentActionHandler>> documentActionHandlersProvider;
    private final AiAuditLog auditLog;

    /// @param explorerNodeServiceProvider    Finds documents in the Explorer tree.
    /// @param securityContext                Checks the user's permissions.
    /// @param documentActionHandlersProvider Each document type's handler, which reads its documents.
    /// @param auditLog                       Records what is viewed.
    @Inject
    @SuppressWarnings("rawtypes")
    DocumentAccess(final Provider<ExplorerNodeService> explorerNodeServiceProvider,
                   final SecurityContext securityContext,
                   final Provider<Map<DocumentTypeName, DocumentActionHandler>> documentActionHandlersProvider,
                   final AiAuditLog auditLog) {
        this.explorerNodeServiceProvider = explorerNodeServiceProvider;
        this.securityContext = securityContext;
        this.documentActionHandlersProvider = documentActionHandlersProvider;
        this.auditLog = auditLog;
    }

    /// @param uuid A document's UUID, as the model gave it.
    /// @return The document, with its type and name, if it exists, is a document rather than the System or
    /// Favourites root, and the user can view it. Empty otherwise, with no difference between the cases.
    public Optional<ExplorerNode> find(final String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return Optional.empty();
        }
        try {
            return explorerNodeServiceProvider.get().getNodeByUuid(uuid.strip())
                    .filter(node -> node.getDocRef() != null)
                    .filter(node -> !ExplorerConstants.isRootNode(node))
                    .filter(node -> canView(node.getDocRef()));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to find document " + uuid, e);
            return Optional.empty();
        }
    }

    /// @param docRef A document.
    /// @return True if the user can view it. False if they can't, or if checking fails (failing closed).
    public boolean canView(final DocRef docRef) {
        if (docRef == null || docRef.getUuid() == null) {
            return false;
        }
        try {
            return securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to check View on " + docRef, e);
            return false;
        }
    }

    /// @param docRef A document the user can view.
    /// @return The node for it in the Explorer tree, if it is in the tree.
    public Optional<ExplorerNode> node(final DocRef docRef) {
        try {
            return explorerNodeServiceProvider.get().getNode(docRef);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to find the node of " + docRef, e);
            return Optional.empty();
        }
    }

    /// @param docRef A document the user can view.
    /// @return The path of the folder holding it in the Explorer tree, e.g. `/Security/Lookups`, or `/` for one
    /// at the top.
    public String path(final DocRef docRef) {
        try {
            return explorerPath(explorerNodeServiceProvider.get().getPath(docRef));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to find the path of " + docRef, e);
            return "/";
        }
    }

    /// @param ancestors The folders holding a document, from the top of the tree (the System root) down.
    /// @return Their path, e.g. `/Security/Lookups`, leaving out the System root.
    static String explorerPath(final List<ExplorerNode> ancestors) {
        if (ancestors == null) {
            return "/";
        }
        return "/" + ancestors.stream()
                .filter(node -> node != null && !ExplorerConstants.isSystemNode(node))
                .map(ExplorerNode::getName)
                .collect(Collectors.joining("/"));
    }

    /// Reads a document through its type's handler, which checks the user's permission itself too.
    ///
    /// @param docRef A document the user can view.
    /// @return The document, or empty if its type has no handler or it can't be read.
    public Optional<Object> read(final DocRef docRef) {
        @SuppressWarnings("rawtypes") final DocumentActionHandler handler = handler(docRef.getType()).orElse(null);
        if (handler == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(handler.readDocument(docRef));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to read " + docRef, e);
            return Optional.empty();
        }
    }

    /// @param type A document type.
    /// @return Its handler, if it has one.
    @SuppressWarnings("rawtypes")
    public Optional<DocumentActionHandler> handler(final String type) {
        if (type == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(documentActionHandlersProvider.get().get(new DocumentTypeName(type)));
    }

    /// Records in the audit log that the user viewed a document for the chat.
    ///
    /// @param chatId  The chat.
    /// @param docRef  The document.
    /// @param purpose Why, e.g. "Read by the read_document tool".
    public void logView(final int chatId, final DocRef docRef, final String purpose) {
        auditLog.documentViewed(chatId, docRef, purpose);
    }
}
