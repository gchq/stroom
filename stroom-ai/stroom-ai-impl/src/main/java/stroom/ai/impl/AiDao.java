package stroom.ai.impl;

import stroom.ai.shared.AiAttachmentStatus;
import stroom.ai.shared.AiAttachmentType;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatAttachment;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.util.shared.FindNamedEntityCriteria;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import java.util.List;
import java.util.Optional;

public interface AiDao {

    AiChat createChat(UserRef userRef);

    ResultPage<AiChat> listChats(UserRef userRef, FindNamedEntityCriteria criteria);

    Optional<AiChat> getChat(int chatId);

    void updateChatTitle(int chatId, String title);

    void deleteChat(int chatId);

    AiChatMessage storeMessage(int chatId, AiMessageType messageType, String message);

    AiChatMessage storeMessage(int chatId, AiMessageType messageType, Integer attachmentId, String message);

    List<AiChatMessage> getMessages(int chatId);

    List<AiChatMessage> getMessagesSince(int chatId, int lastSeenMessageId);

    void updateMessageText(int messageId, String message);

    void deleteMessage(int messageId);

    // ---- Attachment operations ----

    AiChatAttachment createAttachment(int chatId, AiAttachmentType type, String contextJson);

    void updateAttachmentStatus(int attachmentId, AiAttachmentStatus status,
                                String dataMarkdown, Integer rowCount,
                                String description, String errorMessage);

    Optional<AiChatAttachment> getAttachment(int attachmentId);

    List<AiChatAttachment> getAttachmentsByChatId(int chatId);

    /**
     * Loads the large {@code data_markdown} column separately from the attachment metadata.
     */
    String getAttachmentData(int attachmentId);
}

