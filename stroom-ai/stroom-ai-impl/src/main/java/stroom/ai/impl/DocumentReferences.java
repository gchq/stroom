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

package stroom.ai.impl;

import stroom.ai.api.AiService;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.shared.AiAttachmentStatus;
import stroom.ai.shared.AiAttachmentType;
import stroom.ai.shared.AiChatAttachment;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.DocumentReference;
import stroom.ai.shared.DocumentReferenceContext;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import dev.langchain4j.data.message.UserMessage;
import jakarta.inject.Inject;

import java.util.List;

/// Stroom documents attached to chats as references, from the Explorer tree ("Attach to Chat"). Each document is an
/// attachment of its own, ready at once as there is nothing to download, and the model is given a short note about
/// it with its uuid, which it reads with its document tools. The user's View permission is checked when it is
/// attached and again whenever the note is given.
public class DocumentReferences {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DocumentReferences.class);

    /// What the user is told when they attach a document they can't view, or that doesn't exist.
    static final String CANNOT_ATTACH = "That document can't be attached: you don't have permission to view it.";

    private final AiService aiService;
    private final DocumentAccess documentAccess;

    /// @param aiService      Stores the attachments and messages.
    /// @param documentAccess Finds documents and checks the user can view them.
    @Inject
    DocumentReferences(final AiService aiService, final DocumentAccess documentAccess) {
        this.aiService = aiService;
        this.documentAccess = documentAccess;
    }

    /// Attaches each of the documents to the chat, as an attachment of its own with an ATTACHMENT message. A
    /// document the user can't view is not attached, and an ERROR message says so.
    ///
    /// @param chatId  The chat.
    /// @param context The documents.
    public void attach(final int chatId, final DocumentReferenceContext context) {
        for (final DocumentReference reference : NullSafe.list(context.getDocuments())) {
            final DocRef given = NullSafe.get(reference, DocumentReference::getDocRef);
            final ExplorerNode node = given != null && documentAccess.canView(given)
                    ? documentAccess.node(given).orElse(null)
                    : null;
            if (node == null || node.getDocRef() == null || !documentAccess.canView(node.getDocRef())) {
                LOGGER.debug(() -> "Not attaching " + given + " to chat " + chatId);
                aiService.storeMessage(chatId, AiMessageType.ERROR, CANNOT_ATTACH);
                continue;
            }
            final DocRef docRef = node.getDocRef();
            final String path = documentAccess.path(docRef);
            final String description = DocumentReferenceContext.describe(docRef);
            final String contextJson = JsonUtil.writeValueAsString(
                    new DocumentReferenceContext(List.of(new DocumentReference(docRef, path))));
            final AiChatAttachment attachment = aiService.createAttachment(chatId, AiAttachmentType.DOCUMENT,
                    contextJson);
            aiService.updateAttachmentStatus(attachment.getId(), AiAttachmentStatus.READY, null, description, null,
                    false);
            aiService.storeMessage(chatId, AiMessageType.ATTACHMENT, attachment.getId(), description);
            documentAccess.logView(ToolSession.chat(chatId), docRef, "Attached to the chat as a reference");
        }
    }

    /// @param attachment A [AiAttachmentType#DOCUMENT] attachment.
    /// @return The note the model is given about it: what it is, its uuid and path, and the tools to read it with,
    /// or that it is no longer available if the user can no longer view it. Null if it is not a document.
    public UserMessage note(final AiChatAttachment attachment) {
        if (attachment == null || attachment.getAttachmentType() != AiAttachmentType.DOCUMENT) {
            return null;
        }
        final DocRef docRef = attachment.getDocRef();
        if (docRef == null || !documentAccess.canView(docRef)) {
            return unavailableNote(attachment.getDescription());
        }
        return referenceNote(docRef, attachment.getDocPath());
    }

    /// @param docRef The document.
    /// @param path   Its folder's path, or null.
    /// @return The note the model is given about a document attached to the chat.
    static UserMessage referenceNote(final DocRef docRef, final String path) {
        final String use = ExplorerConstants.isFolder(docRef)
                ? "Use folder_contents with its uuid to see what is in it (its documents, and the streams, "
                  + "processor filters and tasks of its feeds and pipelines).]"
                : "Use document_info, read_document or document_documentation with its uuid to see it.]";
        return new UserMessage("[Referenced document: " + DocumentReferenceContext.describe(docRef)
                               + " (uuid " + docRef.getUuid() + ", " + NullSafe.nonBlankStringElse(path, "/")
                               + "). " + use);
    }

    /// @param description What the document was, as it was attached.
    /// @return The note the model is given about a document attached to the chat that the user can no longer view.
    static UserMessage unavailableNote(final String description) {
        return new UserMessage("[The referenced document " + NullSafe.nonBlankStringElse(description, "?")
                               + " is no longer available (the user may no longer view it, or it has "
                               + "been deleted). Do not guess what it held; if you need it, ask the user.]");
    }
}
