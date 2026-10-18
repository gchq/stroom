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
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/// How the chat finds, reads and audits Stroom documents, as the user: the document, content and stream tools, and
/// documents the user attaches to a chat. It is the one place the tools check View (and View Data), find a
/// document's folder, read documents and log what they viewed. Every document is checked for View before anything
/// about it is given, and a document the user can't view is treated exactly as one that doesn't exist, so nothing
/// reveals whether it does. Every check fails closed.
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

    /// @param uuid A document's UUID, as the model gave it.
    /// @return The document (with its type and name) if [#find(String)] finds it. Empty otherwise.
    public Optional<DocRef> findDocRef(final String uuid) {
        return find(uuid).map(ExplorerNode::getDocRef);
    }

    /// Finds a folder by its UUID or its Explorer path, following only folders the user can view.
    ///
    /// @param folder A folder's UUID, or its Explorer path, e.g. `/Security/Lookups`. Null, blank or `/` for the
    ///               top of the tree.
    /// @return The folder, or the System root for the top of the tree, which anyone may look in. Empty if there is
    /// no such folder, or the user can't view it or a folder above it on the path, with no difference between the
    /// cases.
    public Optional<DocRef> findFolder(final String folder) {
        final String value = folder == null
                ? ""
                : folder.strip();
        if (value.isEmpty() || "/".equals(value) || ExplorerConstants.SYSTEM_DOC_REF.getUuid().equals(value)) {
            return Optional.of(ExplorerConstants.SYSTEM_DOC_REF);
        }
        if (!value.startsWith("/")) {
            return findDocRef(value).filter(ExplorerConstants::isFolder);
        }
        try {
            ExplorerNode current = ExplorerConstants.SYSTEM_NODE;
            for (final String name : value.substring(1).split("/")) {
                if (name.isBlank()) {
                    continue;
                }
                final ExplorerNode parent = current;
                current = explorerNodeServiceProvider.get().getNodesByName(parent, name.strip()).stream()
                        .filter(ExplorerConstants::isFolder)
                        .filter(node -> canView(node.getDocRef()))
                        .findFirst()
                        .orElse(null);
                if (current == null) {
                    return Optional.empty();
                }
            }
            return Optional.of(current.getDocRef());
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to find the folder " + folder, e);
            return Optional.empty();
        }
    }

    /// @param folder A folder the user can view, or the System root.
    /// @return What it holds that the user can view, as the Explorer tree shows it, leaving out the Favourites
    /// root. Empty if it can't be listed.
    public List<ExplorerNode> children(final DocRef folder) {
        try {
            return NullSafe.list(explorerNodeServiceProvider.get().getChildren(folder)).stream()
                    .filter(node -> node != null && node.getDocRef() != null)
                    .filter(node -> !ExplorerConstants.isRootNode(node))
                    .filter(node -> canView(node.getDocRef()))
                    .toList();
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to list the children of " + folder, e);
            return List.of();
        }
    }

    /// @return True if the user may view data (streams), as the Data screen needs. False if they may not, or if
    /// checking fails (failing closed).
    public boolean canViewData() {
        try {
            return securityContext.hasAppPermission(AppPermission.VIEW_DATA_PERMISSION);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to check the View Data permission", e);
            return false;
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

    /// Reads a document of a class, after checking View.
    ///
    /// @param docRef A document.
    /// @param type   The class of document expected, e.g. `XsltDoc.class`.
    /// @param <T>    The class of document.
    /// @return The document, or empty if the user can't view it, it can't be read, or it isn't of that class.
    public <T> Optional<T> read(final DocRef docRef, final Class<T> type) {
        if (!canView(docRef)) {
            return Optional.empty();
        }
        return read(docRef)
                .filter(type::isInstance)
                .map(type::cast);
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
