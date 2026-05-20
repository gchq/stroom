/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.ai.impl.db;

import stroom.ai.impl.AiDao;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.db.util.JooqUtil;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import org.jooq.Record;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static stroom.ai.impl.db.jooq.tables.AiChat.AI_CHAT;
import static stroom.ai.impl.db.jooq.tables.AiChatMessage.AI_CHAT_MESSAGE;

public class AiDaoImpl implements AiDao {

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
                    record.get(AI_CHAT_MESSAGE.MESSAGE));

    private final AiDbConnProvider aiDbConnProvider;

    @Inject
    AiDaoImpl(final AiDbConnProvider aiDbConnProvider) {
        this.aiDbConnProvider = aiDbConnProvider;
    }

    @Override
    public AiChat createChat(final UserRef userRef) {
        final long now = System.currentTimeMillis();
        final String userName = userRef.toDisplayString();
        final String userUuid = userRef.getUuid();
        final String title = "New Conversation";

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
                .fetchOne()
                .getId());

        return new AiChat(id, now, now, userUuid, title);
    }

    @Override
    public List<AiChat> listChats(final String userUuid) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                        .select()
                        .from(AI_CHAT)
                        .where(AI_CHAT.USER_UUID.eq(userUuid))
                        .orderBy(AI_CHAT.UPDATE_TIME_MS.desc())
                        .fetch())
                .map(RECORD_TO_AI_CHAT::apply);
    }

    @Override
    public Optional<AiChat> getChat(final int chatId) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                        .select()
                        .from(AI_CHAT)
                        .where(AI_CHAT.ID.eq(chatId))
                        .fetchOptional())
                .map(RECORD_TO_AI_CHAT::apply);
    }

    @Override
    public void updateChatTitle(final int chatId, final String title) {
        JooqUtil.context(aiDbConnProvider, context -> context
                .update(AI_CHAT)
                .set(AI_CHAT.TITLE, title)
                .set(AI_CHAT.UPDATE_TIME_MS, System.currentTimeMillis())
                .where(AI_CHAT.ID.eq(chatId))
                .execute());
    }

    @Override
    public void deleteChat(final int chatId) {
        JooqUtil.context(aiDbConnProvider, context -> context
                .deleteFrom(AI_CHAT)
                .where(AI_CHAT.ID.eq(chatId))
                .execute());
    }

    @Override
    public AiChatMessage storeMessage(final int chatId,
                                      final AiMessageType messageType,
                                      final String message) {
        final long now = System.currentTimeMillis();
        final int id = JooqUtil.contextResult(aiDbConnProvider, context -> context
                .insertInto(AI_CHAT_MESSAGE)
                .set(AI_CHAT_MESSAGE.FK_AI_CHAT_ID, chatId)
                .set(AI_CHAT_MESSAGE.CREATE_TIME_MS, now)
                .set(AI_CHAT_MESSAGE.MESSAGE_TYPE, (int) messageType.getPrimitiveValue())
                .set(AI_CHAT_MESSAGE.MESSAGE, message)
                .returning(AI_CHAT_MESSAGE.ID)
                .fetchOne()
                .getId());

        // Also update the parent chat's update_time_ms.
        JooqUtil.context(aiDbConnProvider, context -> context
                .update(AI_CHAT)
                .set(AI_CHAT.UPDATE_TIME_MS, now)
                .where(AI_CHAT.ID.eq(chatId))
                .execute());

        return new AiChatMessage(id, chatId, now, messageType, message);
    }

    @Override
    public List<AiChatMessage> getMessages(final int chatId) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                        .select()
                        .from(AI_CHAT_MESSAGE)
                        .where(AI_CHAT_MESSAGE.FK_AI_CHAT_ID.eq(chatId))
                        .orderBy(AI_CHAT_MESSAGE.CREATE_TIME_MS.asc())
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
                        .orderBy(AI_CHAT_MESSAGE.CREATE_TIME_MS.asc())
                        .fetch())
                .map(RECORD_TO_AI_CHAT_MESSAGE::apply);
    }
}
