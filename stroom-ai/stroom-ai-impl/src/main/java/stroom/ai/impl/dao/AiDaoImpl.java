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

package stroom.ai.impl.dao;

import stroom.ai.api.SimpleTokenCountEstimator;
import stroom.ai.impl.AiDao;
import stroom.ai.impl.db.AiDbConnProvider;
import stroom.ai.impl.db.jooq.tables.records.AiChatMessageRecord;
import stroom.ai.shared.AiAgentMode;
import stroom.ai.shared.AiAttachmentStatus;
import stroom.ai.shared.AiAttachmentType;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatAttachment;
import stroom.ai.shared.AiChatHistoryFields;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiChatRunStatus;
import stroom.ai.shared.AiChatStatus;
import stroom.ai.shared.AiMemoryScope;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.FindAiChatHistoryCriteria;
import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.JooqUtil;
import stroom.query.api.ExpressionOperator;
import stroom.query.common.v2.FieldProviderImpl;
import stroom.query.common.v2.SimpleStringExpressionParser;
import stroom.query.common.v2.SimpleStringExpressionParser.FieldProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.InsertSetMoreStep;
import org.jooq.Record;
import org.jooq.Record3;
import org.jooq.Record6;
import org.jooq.impl.DSL;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static stroom.ai.impl.db.jooq.tables.AiChat.AI_CHAT;
import static stroom.ai.impl.db.jooq.tables.AiChatAttachment.AI_CHAT_ATTACHMENT;
import static stroom.ai.impl.db.jooq.tables.AiChatFileChunk.AI_CHAT_FILE_CHUNK;
import static stroom.ai.impl.db.jooq.tables.AiChatMessage.AI_CHAT_MESSAGE;
import static stroom.ai.impl.db.jooq.tables.AiChatRun.AI_CHAT_RUN;
import static stroom.ai.impl.db.jooq.tables.AiMemory.AI_MEMORY;
import static stroom.ai.impl.db.jooq.tables.AiToolApproval.AI_TOOL_APPROVAL;

public class AiDaoImpl implements AiDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AiDaoImpl.class);
    // Escapes % and _ in a LIKE pattern.
    private static final char LIKE_ESCAPE = '!';
    private static final SimpleTokenCountEstimator TOKEN_COUNT_ESTIMATOR = new SimpleTokenCountEstimator();

    private static final Function<Record, AiChat> RECORD_TO_AI_CHAT = record ->
            new AiChat(
                    record.get(AI_CHAT.ID),
                    record.get(AI_CHAT.CREATE_TIME_MS),
                    record.get(AI_CHAT.UPDATE_TIME_MS),
                    record.get(AI_CHAT.USER_UUID),
                    record.get(AI_CHAT.TITLE));

    private static final Function<Record, AiChatMessage> RECORD_TO_AI_CHAT_MESSAGE = record ->
            new AiChatMessage(
                    record.get(AI_CHAT_MESSAGE.ID),
                    record.get(AI_CHAT_MESSAGE.FK_AI_CHAT_ID),
                    record.get(AI_CHAT_MESSAGE.CREATE_TIME_MS),
                    AiMessageType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(
                            record.get(AI_CHAT_MESSAGE.MESSAGE_TYPE).byteValue()),
                    record.get(AI_CHAT_MESSAGE.FK_ATTACHMENT_ID),
                    record.get(AI_CHAT_MESSAGE.MESSAGE),
                    record.get(AI_CHAT_MESSAGE.TURN_ID),
                    record.get(AI_CHAT_MESSAGE.TOKEN_COUNT));

    private final AiDbConnProvider aiDbConnProvider;
    private final ExpressionMapper expressionMapper;

    @Inject
    AiDaoImpl(final AiDbConnProvider aiDbConnProvider,
              final ExpressionMapperFactory expressionMapperFactory) {
        this.aiDbConnProvider = aiDbConnProvider;
        expressionMapper = expressionMapperFactory.create();
        expressionMapper.map(AiChatHistoryFields.NAME_FIELD, AI_CHAT.TITLE, string -> string);
    }

    @Override
    public AiChat createChat(final UserRef userRef) {
        final long now = System.currentTimeMillis();
        final String userName = userRef.toDisplayString();
        final String userUuid = userRef.getUuid();
        final String title = DEFAULT_CHAT_TITLE;

        final int id = JooqUtil.contextResult(aiDbConnProvider, context -> context
                .insertInto(AI_CHAT)
                .set(AI_CHAT.VERSION, 1)
                .set(AI_CHAT.CREATE_TIME_MS, now)
                .set(AI_CHAT.CREATE_USER, userName)
                .set(AI_CHAT.UPDATE_TIME_MS, now)
                .set(AI_CHAT.UPDATE_USER, userName)
                .set(AI_CHAT.USER_UUID, userUuid)
                .set(AI_CHAT.TITLE, title)
                .returning(AI_CHAT.ID)
                .fetchOne(AI_CHAT.ID));

        return new AiChat(id, now, now, userUuid, title);
    }

    @Override
    public ResultPage<AiChat> listChats(final UserRef userRef, final FindAiChatHistoryCriteria criteria) {
        final List<Condition> conditions = new ArrayList<>();
        conditions.add(AI_CHAT.USER_UUID.eq(userRef.getUuid()));

        final FieldProvider fieldProvider = new FieldProviderImpl(
                List.of(AiChatHistoryFields.NAME),
                List.of(AiChatHistoryFields.NAME));
        try {
            final Optional<ExpressionOperator> optionalExpressionOperator = SimpleStringExpressionParser
                    .create(fieldProvider, criteria.getFilter());
            optionalExpressionOperator.ifPresent(expressionOperator ->
                    conditions.add(expressionMapper.apply(expressionOperator)));
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            return ResultPage.empty();
        }

        final int offset = JooqUtil.getOffset(criteria.getPageRequest());
        final int limit = JooqUtil.getLimit(criteria.getPageRequest(), true);

        final List<AiChat> list = JooqUtil.contextResult(aiDbConnProvider, context -> context
                        .select()
                        .from(AI_CHAT)
                        .where(conditions)
                        .orderBy(AI_CHAT.UPDATE_TIME_MS.desc())
                        .limit(offset, limit)
                        .fetch())
                .map(RECORD_TO_AI_CHAT::apply);
        return ResultPage.createCriterialBasedList(list, criteria);
    }

    @Override
    public Optional<AiChat> getChat(final int chatId) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                        .select()
                        .from(AI_CHAT)
                        .where(AI_CHAT.ID.eq(chatId))
                        .fetchOptional())
                .map(RECORD_TO_AI_CHAT);
    }

    @Override
    public void updateChatTitle(final int chatId, final String title) {
        JooqUtil.context(aiDbConnProvider, context -> context
                .update(AI_CHAT)
                .set(AI_CHAT.TITLE, title)
                .set(AI_CHAT.TITLE_SET_BY_USER, (byte) 1)
                .set(AI_CHAT.UPDATE_TIME_MS, System.currentTimeMillis())
                .where(AI_CHAT.ID.eq(chatId))
                .execute());
    }

    @Override
    public boolean setAutoTitle(final int chatId, final String title) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                .update(AI_CHAT)
                .set(AI_CHAT.TITLE, title)
                .where(AI_CHAT.ID.eq(chatId))
                .and(AI_CHAT.TITLE_SET_BY_USER.eq((byte) 0))
                .execute()) > 0;
    }

    @Override
    public boolean isTitleSetByUser(final int chatId) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                .select(AI_CHAT.TITLE_SET_BY_USER)
                .from(AI_CHAT)
                .where(AI_CHAT.ID.eq(chatId))
                .fetchOptional(AI_CHAT.TITLE_SET_BY_USER)
                .map(value -> value != 0)
                .orElse(false));
    }

    @Override
    public void resetChatTitle(final int chatId) {
        JooqUtil.context(aiDbConnProvider, context -> context
                .update(AI_CHAT)
                .set(AI_CHAT.TITLE, DEFAULT_CHAT_TITLE)
                .set(AI_CHAT.TITLE_SET_BY_USER, (byte) 0)
                .where(AI_CHAT.ID.eq(chatId))
                .execute());
    }

    @Override
    public void deleteChat(final int chatId) {
        JooqUtil.transaction(aiDbConnProvider, context -> {
            // Null out attachment references in messages (FK constraint).
            context.update(AI_CHAT_MESSAGE)
                    .setNull(AI_CHAT_MESSAGE.FK_ATTACHMENT_ID)
                    .where(AI_CHAT_MESSAGE.FK_AI_CHAT_ID.eq(chatId))
                    .execute();
            // Delete messages.
            context.deleteFrom(AI_CHAT_MESSAGE)
                    .where(AI_CHAT_MESSAGE.FK_AI_CHAT_ID.eq(chatId))
                    .execute();
            // Delete the chat's tool approvals.
            context.deleteFrom(AI_TOOL_APPROVAL)
                    .where(AI_TOOL_APPROVAL.FK_AI_CHAT_ID.eq(chatId))
                    .execute();
            // Delete the chat's memory.
            context.deleteFrom(AI_MEMORY)
                    .where(AI_MEMORY.SCOPE.eq(AiMemoryScope.CHAT.getPrimitiveValue()))
                    .and(AI_MEMORY.SCOPE_KEY.eq(String.valueOf(chatId)))
                    .execute();
            // Delete attachments and their stored content.
            deleteChunksOfChat(context, chatId);
            context.deleteFrom(AI_CHAT_ATTACHMENT)
                    .where(AI_CHAT_ATTACHMENT.FK_AI_CHAT_ID.eq(chatId))
                    .execute();
            // Delete agent runs.
            context.deleteFrom(AI_CHAT_RUN)
                    .where(AI_CHAT_RUN.FK_AI_CHAT_ID.eq(chatId))
                    .execute();
            // Delete the chat.
            context.deleteFrom(AI_CHAT)
                    .where(AI_CHAT.ID.eq(chatId))
                    .execute();
        });
    }

    @Override
    public AiChatMessage storeMessage(final int chatId,
                                      final AiMessageType messageType,
                                      final String message) {
        return storeMessage(chatId, messageType, null, message);
    }

    @Override
    public AiChatMessage storeMessage(final int chatId,
                                      final AiMessageType messageType,
                                      final Integer attachmentId,
                                      final String message) {
        final long now = System.currentTimeMillis();
        // The column is not nullable, and errors can come from exceptions without a message.
        final String text = NullSafe.string(message);
        final int tokenCount = TOKEN_COUNT_ESTIMATOR.estimateTokenCountInText(text);
        return JooqUtil.transactionResult(aiDbConnProvider, context -> {
            // A question starts a new turn, everything else belongs to the latest question in the chat.
            final boolean startsTurn = messageType == AiMessageType.USER_MESSAGE;
            final int turnId = startsTurn
                    ? 0
                    : getLatestTurnId(context, chatId);

            InsertSetMoreStep<AiChatMessageRecord> insert = context
                    .insertInto(AI_CHAT_MESSAGE)
                    .set(AI_CHAT_MESSAGE.FK_AI_CHAT_ID, chatId)
                    .set(AI_CHAT_MESSAGE.CREATE_TIME_MS, now)
                    .set(AI_CHAT_MESSAGE.MESSAGE_TYPE, (int) messageType.getPrimitiveValue())
                    .set(AI_CHAT_MESSAGE.MESSAGE, text)
                    .set(AI_CHAT_MESSAGE.TURN_ID, turnId)
                    .set(AI_CHAT_MESSAGE.TOKEN_COUNT, tokenCount);

            if (attachmentId != null) {
                insert = insert.set(AI_CHAT_MESSAGE.FK_ATTACHMENT_ID, attachmentId);
            }

            final Integer id = insert
                    .returning(AI_CHAT_MESSAGE.ID)
                    .fetchOne(AI_CHAT_MESSAGE.ID);

            Objects.requireNonNull(id, "Null chat message id");

            final int finalTurnId;
            if (startsTurn) {
                context
                        .update(AI_CHAT_MESSAGE)
                        .set(AI_CHAT_MESSAGE.TURN_ID, id)
                        .where(AI_CHAT_MESSAGE.ID.eq(id))
                        .execute();
                finalTurnId = id;
            } else {
                finalTurnId = turnId;
            }

            // Also update the parent chat's update_time_ms.
            context
                    .update(AI_CHAT)
                    .set(AI_CHAT.UPDATE_TIME_MS, now)
                    .where(AI_CHAT.ID.eq(chatId))
                    .execute();

            return new AiChatMessage(
                    id, chatId, now, messageType, attachmentId, text, finalTurnId, tokenCount);
        });
    }

    private int getLatestTurnId(final DSLContext context, final int chatId) {
        final Integer latest = context
                .select(DSL.max(AI_CHAT_MESSAGE.ID))
                .from(AI_CHAT_MESSAGE)
                .where(AI_CHAT_MESSAGE.FK_AI_CHAT_ID.eq(chatId))
                .and(AI_CHAT_MESSAGE.MESSAGE_TYPE.eq((int) AiMessageType.USER_MESSAGE.getPrimitiveValue()))
                .fetchOne(0, Integer.class);
        return Objects.requireNonNullElse(latest, 0);
    }

    @Override
    public List<AiChatMessage> getMessages(final int chatId) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                        .select()
                        .from(AI_CHAT_MESSAGE)
                        .where(AI_CHAT_MESSAGE.FK_AI_CHAT_ID.eq(chatId))
                        .orderBy(AI_CHAT_MESSAGE.ID.asc())
                        .fetch())
                .map(RECORD_TO_AI_CHAT_MESSAGE::apply);
    }

    @Override
    public List<AiChatMessage> getMessagesSince(final int chatId, final int lastSeenMessageId) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                        .select()
                        .from(AI_CHAT_MESSAGE)
                        .where(AI_CHAT_MESSAGE.FK_AI_CHAT_ID.eq(chatId))
                        .and(AI_CHAT_MESSAGE.ID.gt(lastSeenMessageId))
                        .orderBy(AI_CHAT_MESSAGE.ID.asc())
                        .fetch())
                .map(RECORD_TO_AI_CHAT_MESSAGE::apply);
    }

    @Override
    public Optional<AiChatMessage> getWorkingMessage(final int chatId) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                        .select()
                        .from(AI_CHAT_MESSAGE)
                        .where(AI_CHAT_MESSAGE.FK_AI_CHAT_ID.eq(chatId))
                        .and(AI_CHAT_MESSAGE.MESSAGE_TYPE.eq((int) AiMessageType.WORKING.getPrimitiveValue()))
                        .orderBy(AI_CHAT_MESSAGE.ID.desc())
                        .limit(1)
                        .fetchOptional())
                .map(RECORD_TO_AI_CHAT_MESSAGE::apply);
    }

    @Override
    public void deleteWorkingMessages(final int chatId) {
        JooqUtil.context(aiDbConnProvider, context -> context
                .deleteFrom(AI_CHAT_MESSAGE)
                .where(AI_CHAT_MESSAGE.FK_AI_CHAT_ID.eq(chatId))
                .and(AI_CHAT_MESSAGE.MESSAGE_TYPE.in(
                        (int) AiMessageType.WORKING.getPrimitiveValue(),
                        (int) AiMessageType.STREAMING_ANSWER.getPrimitiveValue(),
                        (int) AiMessageType.STREAMING_THINKING.getPrimitiveValue()))
                .execute());
    }

    @Override
    public List<AiChatMessage> getStreamingMessages(final int chatId) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                        .select()
                        .from(AI_CHAT_MESSAGE)
                        .where(AI_CHAT_MESSAGE.FK_AI_CHAT_ID.eq(chatId))
                        .and(AI_CHAT_MESSAGE.MESSAGE_TYPE.in(
                                (int) AiMessageType.STREAMING_ANSWER.getPrimitiveValue(),
                                (int) AiMessageType.STREAMING_THINKING.getPrimitiveValue()))
                        .orderBy(AI_CHAT_MESSAGE.ID)
                        .fetch())
                .map(RECORD_TO_AI_CHAT_MESSAGE::apply);
    }

    @Override
    public void updateMessageText(final int messageId, final String message) {
        JooqUtil.context(aiDbConnProvider, context -> context
                .update(AI_CHAT_MESSAGE)
                .set(AI_CHAT_MESSAGE.MESSAGE, message)
                .where(AI_CHAT_MESSAGE.ID.eq(messageId))
                .execute());
    }

    @Override
    public void deleteMessage(final int messageId) {
        JooqUtil.transaction(aiDbConnProvider, context -> {
            // Null out any attachment reference first (FK RESTRICT).
            context.update(AI_CHAT_MESSAGE)
                    .setNull(AI_CHAT_MESSAGE.FK_ATTACHMENT_ID)
                    .where(AI_CHAT_MESSAGE.ID.eq(messageId))
                    .execute();
            context.deleteFrom(AI_CHAT_MESSAGE)
                    .where(AI_CHAT_MESSAGE.ID.eq(messageId))
                    .execute();
        });
    }

    @Override
    public void deleteAttachment(final int attachmentId) {
        JooqUtil.transaction(aiDbConnProvider, context -> {
            // Delete the message that references this attachment.
            context.deleteFrom(AI_CHAT_MESSAGE)
                    .where(AI_CHAT_MESSAGE.FK_ATTACHMENT_ID.eq(attachmentId))
                    .execute();
            // Delete the attachment's stored content.
            context.deleteFrom(AI_CHAT_FILE_CHUNK)
                    .where(AI_CHAT_FILE_CHUNK.FK_ATTACHMENT_ID.eq(attachmentId))
                    .execute();
            // Delete the attachment itself.
            context.deleteFrom(AI_CHAT_ATTACHMENT)
                    .where(AI_CHAT_ATTACHMENT.ID.eq(attachmentId))
                    .execute();
        });
    }

    @Override
    public void deleteAllChatMessagesAndAttachments(final int chatId) {
        JooqUtil.transaction(aiDbConnProvider, context -> {
            // Null out attachment references in messages (FK RESTRICT).
            context.update(AI_CHAT_MESSAGE)
                    .setNull(AI_CHAT_MESSAGE.FK_ATTACHMENT_ID)
                    .where(AI_CHAT_MESSAGE.FK_AI_CHAT_ID.eq(chatId))
                    .execute();
            // Delete all messages.
            context.deleteFrom(AI_CHAT_MESSAGE)
                    .where(AI_CHAT_MESSAGE.FK_AI_CHAT_ID.eq(chatId))
                    .execute();
            // Delete the stored content of all attachments.
            deleteChunksOfChat(context, chatId);
            // Delete all attachments.
            context.deleteFrom(AI_CHAT_ATTACHMENT)
                    .where(AI_CHAT_ATTACHMENT.FK_AI_CHAT_ID.eq(chatId))
                    .execute();
        });
    }

    // ---------------------------------------------------------------------
    // Attachment operations
    // ---------------------------------------------------------------------

    private static final Function<Record, AiChatAttachment> RECORD_TO_ATTACHMENT = record ->
            new AiChatAttachment(
                    record.get(AI_CHAT_ATTACHMENT.ID),
                    record.get(AI_CHAT_ATTACHMENT.FK_AI_CHAT_ID),
                    record.get(AI_CHAT_ATTACHMENT.CREATE_TIME_MS),
                    record.get(AI_CHAT_ATTACHMENT.UPDATE_TIME_MS),
                    AiAttachmentStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(
                            record.get(AI_CHAT_ATTACHMENT.STATUS).byteValue()),
                    AiAttachmentType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(
                            record.get(AI_CHAT_ATTACHMENT.ATTACHMENT_TYPE).byteValue()),
                    record.get(AI_CHAT_ATTACHMENT.DESCRIPTION),
                    record.get(AI_CHAT_ATTACHMENT.ROW_COUNT),
                    record.get(AI_CHAT_ATTACHMENT.TRUNCATED),
                    record.get(AI_CHAT_ATTACHMENT.ERROR_MESSAGE));

    @Override
    public AiChatAttachment createAttachment(final int chatId,
                                             final AiAttachmentType type,
                                             final String contextJson) {
        final long now = System.currentTimeMillis();
        final int statusValue = AiAttachmentStatus.PENDING.getPrimitiveValue();
        final int typeValue = type.getPrimitiveValue();

        final int id = JooqUtil.contextResult(aiDbConnProvider, context -> context
                .insertInto(AI_CHAT_ATTACHMENT)
                .set(AI_CHAT_ATTACHMENT.FK_AI_CHAT_ID, chatId)
                .set(AI_CHAT_ATTACHMENT.CREATE_TIME_MS, now)
                .set(AI_CHAT_ATTACHMENT.UPDATE_TIME_MS, now)
                .set(AI_CHAT_ATTACHMENT.STATUS, statusValue)
                .set(AI_CHAT_ATTACHMENT.ATTACHMENT_TYPE, typeValue)
                .set(AI_CHAT_ATTACHMENT.CONTEXT_JSON, contextJson)
                .returning(AI_CHAT_ATTACHMENT.ID)
                .fetchOne(AI_CHAT_ATTACHMENT.ID));

        return new AiChatAttachment(id, chatId, now, now,
                AiAttachmentStatus.PENDING, type, null, null, false, null);
    }

    @Override
    public void updateAttachmentStatus(final int attachmentId,
                                       final AiAttachmentStatus status,
                                       final Integer rowCount,
                                       final String description,
                                       final String errorMessage,
                                       final boolean truncated) {
        final long now = System.currentTimeMillis();
        JooqUtil.context(aiDbConnProvider, context -> context
                .update(AI_CHAT_ATTACHMENT)
                .set(AI_CHAT_ATTACHMENT.STATUS, (int) status.getPrimitiveValue())
                .set(AI_CHAT_ATTACHMENT.UPDATE_TIME_MS, now)
                .set(AI_CHAT_ATTACHMENT.ROW_COUNT, rowCount)
                .set(AI_CHAT_ATTACHMENT.DESCRIPTION, description)
                .set(AI_CHAT_ATTACHMENT.ERROR_MESSAGE, errorMessage)
                .set(AI_CHAT_ATTACHMENT.TRUNCATED, truncated)
                .where(AI_CHAT_ATTACHMENT.ID.eq(attachmentId))
                .execute());
    }

    @Override
    public Optional<AiChatAttachment> getAttachment(final int attachmentId) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                        .select(
                                AI_CHAT_ATTACHMENT.ID,
                                AI_CHAT_ATTACHMENT.FK_AI_CHAT_ID,
                                AI_CHAT_ATTACHMENT.CREATE_TIME_MS,
                                AI_CHAT_ATTACHMENT.UPDATE_TIME_MS,
                                AI_CHAT_ATTACHMENT.STATUS,
                                AI_CHAT_ATTACHMENT.ATTACHMENT_TYPE,
                                AI_CHAT_ATTACHMENT.DESCRIPTION,
                                AI_CHAT_ATTACHMENT.ROW_COUNT,
                                AI_CHAT_ATTACHMENT.TRUNCATED,
                                AI_CHAT_ATTACHMENT.ERROR_MESSAGE)
                        .from(AI_CHAT_ATTACHMENT)
                        .where(AI_CHAT_ATTACHMENT.ID.eq(attachmentId))
                        .fetchOptional())
                .map(RECORD_TO_ATTACHMENT);
    }

    @Override
    public List<AiChatAttachment> getAttachmentsByChatId(final int chatId) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                        .select(
                                AI_CHAT_ATTACHMENT.ID,
                                AI_CHAT_ATTACHMENT.FK_AI_CHAT_ID,
                                AI_CHAT_ATTACHMENT.CREATE_TIME_MS,
                                AI_CHAT_ATTACHMENT.UPDATE_TIME_MS,
                                AI_CHAT_ATTACHMENT.STATUS,
                                AI_CHAT_ATTACHMENT.ATTACHMENT_TYPE,
                                AI_CHAT_ATTACHMENT.DESCRIPTION,
                                AI_CHAT_ATTACHMENT.ROW_COUNT,
                                AI_CHAT_ATTACHMENT.TRUNCATED,
                                AI_CHAT_ATTACHMENT.ERROR_MESSAGE)
                        .from(AI_CHAT_ATTACHMENT)
                        .where(AI_CHAT_ATTACHMENT.FK_AI_CHAT_ID.eq(chatId))
                        .orderBy(AI_CHAT_ATTACHMENT.CREATE_TIME_MS.asc())
                        .fetch())
                .map(RECORD_TO_ATTACHMENT::apply);
    }

    @Override
    public void recordPoll(final int chatId, final long minIntervalMs) {
        final long now = System.currentTimeMillis();
        JooqUtil.context(aiDbConnProvider, context -> context
                .update(AI_CHAT)
                .set(AI_CHAT.LAST_POLL_MS, now)
                .where(AI_CHAT.ID.eq(chatId))
                .and(AI_CHAT.LAST_POLL_MS.isNull().or(AI_CHAT.LAST_POLL_MS.lt(now - minIntervalMs)))
                .execute());
    }

    @Override
    public Long getLastPollMs(final String userUuid) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                .select(DSL.max(AI_CHAT.LAST_POLL_MS))
                .from(AI_CHAT)
                .where(AI_CHAT.USER_UUID.eq(userUuid))
                .fetchOne(0, Long.class));
    }

    private static void deleteChunksOfChat(final DSLContext context, final int chatId) {
        context.deleteFrom(AI_CHAT_FILE_CHUNK)
                .where(AI_CHAT_FILE_CHUNK.FK_ATTACHMENT_ID.in(context
                        .select(AI_CHAT_ATTACHMENT.ID)
                        .from(AI_CHAT_ATTACHMENT)
                        .where(AI_CHAT_ATTACHMENT.FK_AI_CHAT_ID.eq(chatId))))
                .execute();
    }

    @Override
    public void recordUsage(final int chatId,
                            final String modelDocUuid,
                            final int contextWindowTokens,
                            final long inputTokens,
                            final long outputTokens) {
        JooqUtil.context(aiDbConnProvider, context -> context
                .update(AI_CHAT)
                .set(AI_CHAT.MODEL_DOC_UUID, modelDocUuid)
                .set(AI_CHAT.CONTEXT_WINDOW_TOKENS, contextWindowTokens > 0
                        ? contextWindowTokens
                        : null)
                .set(AI_CHAT.LAST_INPUT_TOKENS, (int) Math.min(Integer.MAX_VALUE, inputTokens))
                .set(AI_CHAT.TOTAL_INPUT_TOKENS, AI_CHAT.TOTAL_INPUT_TOKENS.plus(inputTokens))
                .set(AI_CHAT.TOTAL_OUTPUT_TOKENS, AI_CHAT.TOTAL_OUTPUT_TOKENS.plus(outputTokens))
                .where(AI_CHAT.ID.eq(chatId))
                .execute());
    }

    @Override
    public void recordModel(final int chatId, final String modelDocUuid, final int contextWindowTokens) {
        JooqUtil.context(aiDbConnProvider, context -> context
                .update(AI_CHAT)
                .set(AI_CHAT.MODEL_DOC_UUID, modelDocUuid)
                .set(AI_CHAT.CONTEXT_WINDOW_TOKENS, contextWindowTokens > 0
                        ? contextWindowTokens
                        : null)
                .where(AI_CHAT.ID.eq(chatId))
                .execute());
    }

    @Override
    public ChatUsage getUsage(final int chatId) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                        .select(AI_CHAT.MODEL_DOC_UUID,
                                AI_CHAT.CONTEXT_WINDOW_TOKENS,
                                AI_CHAT.LAST_INPUT_TOKENS,
                                AI_CHAT.TOTAL_INPUT_TOKENS,
                                AI_CHAT.TOTAL_OUTPUT_TOKENS)
                        .from(AI_CHAT)
                        .where(AI_CHAT.ID.eq(chatId))
                        .fetchOptional())
                .map(record -> new ChatUsage(record.value1(), record.value2(), record.value3(),
                        record.value4(), record.value5()))
                .orElse(new ChatUsage(null, null, null, 0, 0));
    }

    @Override
    public List<Integer> findChatsNotUpdatedSince(final long timeMs, final int limit) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                .select(AI_CHAT.ID)
                .from(AI_CHAT)
                .where(AI_CHAT.UPDATE_TIME_MS.lt(timeMs))
                .orderBy(AI_CHAT.UPDATE_TIME_MS)
                .limit(limit)
                .fetch(AI_CHAT.ID));
    }

    @Override
    public List<String> getChatOwners() {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                .selectDistinct(AI_CHAT.USER_UUID)
                .from(AI_CHAT)
                .fetch(AI_CHAT.USER_UUID));
    }

    @Override
    public List<Integer> getChatIds(final String userUuid) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                .select(AI_CHAT.ID)
                .from(AI_CHAT)
                .where(AI_CHAT.USER_UUID.eq(userUuid))
                .fetch(AI_CHAT.ID));
    }

    @Override
    public boolean compareAndSetMessageText(final int messageId, final String expected, final String message) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                .update(AI_CHAT_MESSAGE)
                .set(AI_CHAT_MESSAGE.MESSAGE, message)
                .where(AI_CHAT_MESSAGE.ID.eq(messageId))
                .and(AI_CHAT_MESSAGE.MESSAGE.eq(expected))
                .execute()) == 1;
    }

    @Override
    public boolean isApproveEverything(final int chatId) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                .select(AI_CHAT.AUTO_APPROVE)
                .from(AI_CHAT)
                .where(AI_CHAT.ID.eq(chatId))
                .fetchOptional(AI_CHAT.AUTO_APPROVE)
                .orElse(false));
    }

    @Override
    public AiAgentMode getAgentMode(final int chatId) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                .select(AI_CHAT.AGENT_MODE)
                .from(AI_CHAT)
                .where(AI_CHAT.ID.eq(chatId))
                .fetchOptional(AI_CHAT.AGENT_MODE)
                .map(value -> AiAgentMode.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(value, AiAgentMode.AGENT))
                .orElse(AiAgentMode.AGENT));
    }

    @Override
    public void setAgentMode(final int chatId, final AiAgentMode mode) {
        JooqUtil.context(aiDbConnProvider, context -> context
                .update(AI_CHAT)
                .set(AI_CHAT.AGENT_MODE, mode.getPrimitiveValue())
                .where(AI_CHAT.ID.eq(chatId))
                .execute());
    }

    @Override
    public void setApproveEverything(final int chatId, final boolean enabled) {
        JooqUtil.context(aiDbConnProvider, context -> context
                .update(AI_CHAT)
                .set(AI_CHAT.AUTO_APPROVE, enabled)
                .where(AI_CHAT.ID.eq(chatId))
                .execute());
    }

    @Override
    public List<AiChatMessage> getMessagesOfTypeBefore(final AiMessageType messageType, final long before) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                        .select()
                        .from(AI_CHAT_MESSAGE)
                        .where(AI_CHAT_MESSAGE.MESSAGE_TYPE.eq((int) messageType.getPrimitiveValue()))
                        .and(AI_CHAT_MESSAGE.CREATE_TIME_MS.lt(before))
                        .orderBy(AI_CHAT_MESSAGE.ID.asc())
                        .fetch())
                .map(RECORD_TO_AI_CHAT_MESSAGE::apply);
    }

    /// Messages that count towards a chat being unread: what the agent says, and what it asks the user.
    private static final List<Integer> UNREAD_TYPES = Stream.of(
                    AiMessageType.AI_RESPONSE,
                    AiMessageType.ERROR,
                    AiMessageType.APPROVAL_REQUEST,
                    AiMessageType.MEMORY_UPDATE)
            .map(type -> (int) type.getPrimitiveValue())
            .toList();

    @Override
    public void markRead(final int chatId, final int messageId) {
        JooqUtil.context(aiDbConnProvider, context -> context
                .update(AI_CHAT)
                .set(AI_CHAT.LAST_READ_MESSAGE_ID, DSL.greatest(DSL.coalesce(AI_CHAT.LAST_READ_MESSAGE_ID, 0),
                        DSL.val(messageId)))
                .set(AI_CHAT.LAST_OPENED_MS, System.currentTimeMillis())
                .where(AI_CHAT.ID.eq(chatId))
                .execute());
    }

    @Override
    public int markAllRead(final String userUuid) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> {
            final Field<Integer> lastMessage = DSL.field(DSL.select(DSL.max(AI_CHAT_MESSAGE.ID))
                    .from(AI_CHAT_MESSAGE)
                    .where(AI_CHAT_MESSAGE.FK_AI_CHAT_ID.eq(AI_CHAT.ID))
                    .and(AI_CHAT_MESSAGE.MESSAGE_TYPE.in(UNREAD_TYPES)));
            return context
                    .update(AI_CHAT)
                    .set(AI_CHAT.LAST_READ_MESSAGE_ID, lastMessage)
                    .where(AI_CHAT.USER_UUID.eq(userUuid))
                    .and(lastMessage.gt(DSL.coalesce(AI_CHAT.LAST_READ_MESSAGE_ID, 0)))
                    .execute();
        });
    }

    @Override
    public List<ChatSearchMatch> searchChats(final String userUuid, final String text, final int limit) {
        final String pattern = "%" + escapeLike(text) + "%";
        return JooqUtil.contextResult(aiDbConnProvider, context -> {
            // A question of the chat's that contains the text.
            final Condition questionMatches = AI_CHAT_MESSAGE.FK_AI_CHAT_ID.eq(AI_CHAT.ID)
                    .and(AI_CHAT_MESSAGE.MESSAGE_TYPE.eq((int) AiMessageType.USER_MESSAGE.getPrimitiveValue()))
                    .and(AI_CHAT_MESSAGE.MESSAGE.like(pattern, LIKE_ESCAPE));
            final List<Record3<Integer, String, Long>> chats = context
                    .select(AI_CHAT.ID, AI_CHAT.TITLE, AI_CHAT.UPDATE_TIME_MS)
                    .from(AI_CHAT)
                    .where(AI_CHAT.USER_UUID.eq(userUuid))
                    .and(AI_CHAT.TITLE.like(pattern, LIKE_ESCAPE)
                            .or(DSL.exists(DSL.selectOne().from(AI_CHAT_MESSAGE).where(questionMatches))))
                    .orderBy(AI_CHAT.UPDATE_TIME_MS.desc())
                    .limit(limit)
                    .fetch();
            if (chats.isEmpty()) {
                return List.of();
            }
            // The latest matching question of each chat found.
            final Map<Integer, String> questions = new HashMap<>();
            context.select(AI_CHAT_MESSAGE.FK_AI_CHAT_ID, AI_CHAT_MESSAGE.MESSAGE)
                    .from(AI_CHAT_MESSAGE)
                    .where(AI_CHAT_MESSAGE.FK_AI_CHAT_ID.in(chats.stream().map(Record3::value1).toList()))
                    .and(AI_CHAT_MESSAGE.MESSAGE_TYPE.eq((int) AiMessageType.USER_MESSAGE.getPrimitiveValue()))
                    .and(AI_CHAT_MESSAGE.MESSAGE.like(pattern, LIKE_ESCAPE))
                    .orderBy(AI_CHAT_MESSAGE.ID.desc())
                    .fetch()
                    .forEach(r -> questions.putIfAbsent(r.value1(), r.value2()));
            return chats.stream()
                    .map(r -> new ChatSearchMatch(r.value1(), r.value2(), r.value3(), questions.get(r.value1())))
                    .toList();
        });
    }

    /// @return The text with the characters special to LIKE escaped with [#LIKE_ESCAPE], so they match
    /// themselves.
    static String escapeLike(final String text) {
        return text.replace(String.valueOf(LIKE_ESCAPE), String.valueOf(LIKE_ESCAPE) + LIKE_ESCAPE)
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
    }

    @Override
    public List<AiChatStatus> getChatStatuses(final String userUuid, final int limit) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> {
            final List<Record> chats = context
                    .select(AI_CHAT.ID, AI_CHAT.TITLE, AI_CHAT.UPDATE_TIME_MS, AI_CHAT.LAST_READ_MESSAGE_ID,
                            AI_CHAT.AUTO_APPROVE, AI_CHAT.LAST_OPENED_MS, AI_CHAT.AGENT_MODE)
                    .from(AI_CHAT)
                    .where(AI_CHAT.USER_UUID.eq(userUuid))
                    .orderBy(AI_CHAT.UPDATE_TIME_MS.desc())
                    .limit(limit)
                    .fetch()
                    .stream()
                    .map(r -> (Record) r)
                    .toList();
            final List<Integer> ids = chats.stream().map(r -> r.get(AI_CHAT.ID)).toList();
            final Map<Integer, Byte> runStatuses = new HashMap<>();
            context.select(AI_CHAT_RUN.FK_AI_CHAT_ID, AI_CHAT_RUN.STATUS)
                    .from(AI_CHAT_RUN)
                    .where(AI_CHAT_RUN.ID.in(DSL.select(DSL.max(AI_CHAT_RUN.ID))
                            .from(AI_CHAT_RUN)
                            .where(AI_CHAT_RUN.FK_AI_CHAT_ID.in(ids))
                            .groupBy(AI_CHAT_RUN.FK_AI_CHAT_ID)))
                    .fetch()
                    .forEach(r -> runStatuses.put(r.value1(), r.value2()));
            final Map<Integer, Integer> lastMessages = new HashMap<>();
            context.select(AI_CHAT_MESSAGE.FK_AI_CHAT_ID, DSL.max(AI_CHAT_MESSAGE.ID))
                    .from(AI_CHAT_MESSAGE)
                    .where(AI_CHAT_MESSAGE.FK_AI_CHAT_ID.in(ids))
                    .and(AI_CHAT_MESSAGE.MESSAGE_TYPE.in(UNREAD_TYPES))
                    .groupBy(AI_CHAT_MESSAGE.FK_AI_CHAT_ID)
                    .fetch()
                    .forEach(r -> lastMessages.put(r.value1(), r.value2()));
            return chats.stream()
                    .map(r -> {
                        final int id = r.get(AI_CHAT.ID);
                        final int lastRead = Objects.requireNonNullElse(r.get(AI_CHAT.LAST_READ_MESSAGE_ID), 0);
                        final Byte runStatus = runStatuses.get(id);
                        return new AiChatStatus(
                                id,
                                r.get(AI_CHAT.TITLE),
                                r.get(AI_CHAT.UPDATE_TIME_MS),
                                runStatus == null
                                        ? null
                                        : AiChatRunStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(runStatus),
                                lastMessages.getOrDefault(id, 0) > lastRead,
                                Boolean.TRUE.equals(r.get(AI_CHAT.AUTO_APPROVE)),
                                r.get(AI_CHAT.LAST_OPENED_MS),
                                AiAgentMode.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(
                                        r.get(AI_CHAT.AGENT_MODE), AiAgentMode.AGENT));
                    })
                    .toList();
        });
    }

    @Override
    public long getStatusVersion(final String userUuid) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> {
            // Each chat's mode and auto-approve setting, weighted by its id, so that changing either in any one
            // chat changes the sum.
            final Field<BigDecimal> settings = DSL.sum(AI_CHAT.ID.mul(
                    AI_CHAT.AGENT_MODE.cast(Integer.class).plus(1)
                            .plus(AI_CHAT.AUTO_APPROVE.cast(Integer.class).mul(10))));
            // The titles too, as an automatic title doesn't change when the chat was last active.
            final Field<BigDecimal> titles = DSL.sum(DSL.field("CRC32({0})", Long.class, AI_CHAT.TITLE));
            final Record6<Long, Long, Integer, Integer, BigDecimal, BigDecimal> chats = context
                    .select(DSL.max(AI_CHAT.UPDATE_TIME_MS),
                            DSL.max(AI_CHAT.LAST_OPENED_MS),
                            DSL.max(AI_CHAT.LAST_READ_MESSAGE_ID),
                            DSL.count(),
                            settings,
                            titles)
                    .from(AI_CHAT)
                    .where(AI_CHAT.USER_UUID.eq(userUuid))
                    .fetchOne();
            final Long runs = context
                    .select(DSL.max(AI_CHAT_RUN.UPDATE_TIME_MS))
                    .from(AI_CHAT_RUN)
                    .where(AI_CHAT_RUN.USER_UUID.eq(userUuid))
                    .fetchOne(0, Long.class);
            // Changes when anything that a status shows changes: a chat updated, read, added or deleted, its mode
            // or auto-approve changed, or a run changed.
            long version = 17;
            for (final Object part : Arrays.asList(chats.value1(), chats.value2(), chats.value3(), chats.value4(),
                    chats.value5(), chats.value6(), runs)) {
                version = version * 1_000_003L + Objects.hashCode(part);
            }
            return version;
        });
    }
}
