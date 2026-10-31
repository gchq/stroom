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

import stroom.ai.impl.AiWorkspaceDao;
import stroom.ai.impl.db.AiDbConnProvider;
import stroom.ai.shared.AiAttachmentStatus;
import stroom.ai.shared.AiAttachmentType;
import stroom.db.util.JooqUtil;

import jakarta.inject.Inject;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static stroom.ai.impl.db.jooq.tables.AiChat.AI_CHAT;
import static stroom.ai.impl.db.jooq.tables.AiChatAttachment.AI_CHAT_ATTACHMENT;
import static stroom.ai.impl.db.jooq.tables.AiChatFileChunk.AI_CHAT_FILE_CHUNK;
import static stroom.ai.impl.db.jooq.tables.AiChatMessage.AI_CHAT_MESSAGE;

public class AiWorkspaceDaoImpl implements AiWorkspaceDao {

    private static final List<Field<?>> FILE_INFO_FIELDS = List.of(
            AI_CHAT_ATTACHMENT.ID,
            AI_CHAT_ATTACHMENT.FK_AI_CHAT_ID,
            AI_CHAT_ATTACHMENT.PATH,
            AI_CHAT_ATTACHMENT.STATUS,
            AI_CHAT_ATTACHMENT.SOURCE_DOC_UUID,
            AI_CHAT_ATTACHMENT.EXPIRE_TIME_MS,
            AI_CHAT_ATTACHMENT.ERROR_MESSAGE,
            AI_CHAT_ATTACHMENT.SIZE_BYTES);

    private final AiDbConnProvider aiDbConnProvider;

    @Inject
    AiWorkspaceDaoImpl(final AiDbConnProvider aiDbConnProvider) {
        this.aiDbConnProvider = aiDbConnProvider;
    }

    @Override
    public int createFile(final int chatId,
                          final AiAttachmentType type,
                          final String path,
                          final Integer sourceMessageId) {
        final long now = System.currentTimeMillis();
        final Integer id = JooqUtil.contextResult(aiDbConnProvider, context -> context
                .insertInto(AI_CHAT_ATTACHMENT)
                .set(AI_CHAT_ATTACHMENT.FK_AI_CHAT_ID, chatId)
                .set(AI_CHAT_ATTACHMENT.CREATE_TIME_MS, now)
                .set(AI_CHAT_ATTACHMENT.UPDATE_TIME_MS, now)
                .set(AI_CHAT_ATTACHMENT.STATUS, (int) AiAttachmentStatus.READY.getPrimitiveValue())
                .set(AI_CHAT_ATTACHMENT.ATTACHMENT_TYPE, (int) type.getPrimitiveValue())
                .set(AI_CHAT_ATTACHMENT.PATH, path)
                .set(AI_CHAT_ATTACHMENT.SIZE_BYTES, 0L)
                .set(AI_CHAT_ATTACHMENT.FK_SOURCE_MESSAGE_ID, sourceMessageId)
                .returning(AI_CHAT_ATTACHMENT.ID)
                .fetchOne(AI_CHAT_ATTACHMENT.ID));
        return Objects.requireNonNull(id, "Null file id");
    }

    @Override
    public Optional<Integer> findFile(final int chatId, final String path) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                .select(AI_CHAT_ATTACHMENT.ID)
                .from(AI_CHAT_ATTACHMENT)
                .where(AI_CHAT_ATTACHMENT.FK_AI_CHAT_ID.eq(chatId))
                .and(AI_CHAT_ATTACHMENT.PATH.eq(path))
                .orderBy(AI_CHAT_ATTACHMENT.ID.desc())
                .limit(1)
                .fetchOptional(AI_CHAT_ATTACHMENT.ID));
    }

    @Override
    public void storeContent(final int fileId,
                             final String path,
                             final List<Chunk> chunks,
                             final long sizeBytes) {
        JooqUtil.transaction(aiDbConnProvider, context -> {
            context.deleteFrom(AI_CHAT_FILE_CHUNK)
                    .where(AI_CHAT_FILE_CHUNK.FK_ATTACHMENT_ID.eq(fileId))
                    .execute();
            for (int seq = 0; seq < chunks.size(); seq++) {
                final Chunk chunk = chunks.get(seq);
                context.insertInto(AI_CHAT_FILE_CHUNK)
                        .set(AI_CHAT_FILE_CHUNK.FK_ATTACHMENT_ID, fileId)
                        .set(AI_CHAT_FILE_CHUNK.SEQ, seq)
                        .set(AI_CHAT_FILE_CHUNK.FIRST_LINE, chunk.firstLine())
                        .set(AI_CHAT_FILE_CHUNK.LINE_COUNT, chunk.lineCount())
                        .set(AI_CHAT_FILE_CHUNK.RAW_SIZE, chunk.rawSize())
                        .set(AI_CHAT_FILE_CHUNK.DATA, chunk.data())
                        .execute();
            }
            context.update(AI_CHAT_ATTACHMENT)
                    .set(AI_CHAT_ATTACHMENT.PATH, path)
                    .set(AI_CHAT_ATTACHMENT.SIZE_BYTES, sizeBytes)
                    .set(AI_CHAT_ATTACHMENT.UPDATE_TIME_MS, System.currentTimeMillis())
                    .where(AI_CHAT_ATTACHMENT.ID.eq(fileId))
                    .execute();
        });
    }

    @Override
    public List<ChunkInfo> getChunkInfo(final int fileId) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                        .select(AI_CHAT_FILE_CHUNK.SEQ,
                                AI_CHAT_FILE_CHUNK.FIRST_LINE,
                                AI_CHAT_FILE_CHUNK.LINE_COUNT,
                                AI_CHAT_FILE_CHUNK.RAW_SIZE)
                        .from(AI_CHAT_FILE_CHUNK)
                        .where(AI_CHAT_FILE_CHUNK.FK_ATTACHMENT_ID.eq(fileId))
                        .orderBy(AI_CHAT_FILE_CHUNK.SEQ)
                        .fetch())
                .map(record -> new ChunkInfo(record.value1(), record.value2(), record.value3(), record.value4()));
    }

    @Override
    public Optional<byte[]> getChunkData(final int fileId, final int seq) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                .select(AI_CHAT_FILE_CHUNK.DATA)
                .from(AI_CHAT_FILE_CHUNK)
                .where(AI_CHAT_FILE_CHUNK.FK_ATTACHMENT_ID.eq(fileId))
                .and(AI_CHAT_FILE_CHUNK.SEQ.eq(seq))
                .fetchOptional(AI_CHAT_FILE_CHUNK.DATA));
    }

    @Override
    public long getSize(final int fileId) {
        final Long size = JooqUtil.contextResult(aiDbConnProvider, context -> context
                .select(AI_CHAT_ATTACHMENT.SIZE_BYTES)
                .from(AI_CHAT_ATTACHMENT)
                .where(AI_CHAT_ATTACHMENT.ID.eq(fileId))
                .fetchOptional(AI_CHAT_ATTACHMENT.SIZE_BYTES)
                .orElse(null));
        return Objects.requireNonNullElse(size, 0L);
    }

    @Override
    public void setRetention(final int fileId, final String sourceDocUuid, final Long expireTimeMs) {
        JooqUtil.context(aiDbConnProvider, context -> context
                .update(AI_CHAT_ATTACHMENT)
                .set(AI_CHAT_ATTACHMENT.SOURCE_DOC_UUID, sourceDocUuid)
                .set(AI_CHAT_ATTACHMENT.EXPIRE_TIME_MS, expireTimeMs)
                .where(AI_CHAT_ATTACHMENT.ID.eq(fileId))
                .execute());
    }

    @Override
    public Optional<FileInfo> getFileInfo(final int fileId) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                        .select(FILE_INFO_FIELDS)
                        .from(AI_CHAT_ATTACHMENT)
                        .where(AI_CHAT_ATTACHMENT.ID.eq(fileId))
                        .fetchOptional())
                .map(AiWorkspaceDaoImpl::toFileInfo);
    }

    @Override
    public List<FileInfo> listFiles(final int chatId) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                        .select(FILE_INFO_FIELDS)
                        .from(AI_CHAT_ATTACHMENT)
                        .where(AI_CHAT_ATTACHMENT.FK_AI_CHAT_ID.eq(chatId))
                        .and(AI_CHAT_ATTACHMENT.PATH.isNotNull())
                        .orderBy(AI_CHAT_ATTACHMENT.PATH)
                        .fetch())
                .map(AiWorkspaceDaoImpl::toFileInfo);
    }

    @Override
    public void deleteFile(final int fileId) {
        JooqUtil.transaction(aiDbConnProvider, context -> {
            context.deleteFrom(AI_CHAT_FILE_CHUNK)
                    .where(AI_CHAT_FILE_CHUNK.FK_ATTACHMENT_ID.eq(fileId))
                    .execute();
            // A message may refer to the file (e.g. the tool call that made it).
            context.update(AI_CHAT_MESSAGE)
                    .setNull(AI_CHAT_MESSAGE.FK_ATTACHMENT_ID)
                    .where(AI_CHAT_MESSAGE.FK_ATTACHMENT_ID.eq(fileId))
                    .execute();
            context.deleteFrom(AI_CHAT_ATTACHMENT)
                    .where(AI_CHAT_ATTACHMENT.ID.eq(fileId))
                    .execute();
        });
    }

    @Override
    public long getTotalSizeForUser(final String userUuid) {
        final BigDecimal total = JooqUtil.contextResult(aiDbConnProvider, context -> context
                .select(DSL.sum(AI_CHAT_ATTACHMENT.SIZE_BYTES))
                .from(AI_CHAT_ATTACHMENT)
                .join(AI_CHAT).on(AI_CHAT.ID.eq(AI_CHAT_ATTACHMENT.FK_AI_CHAT_ID))
                .where(AI_CHAT.USER_UUID.eq(userUuid))
                .fetchOne(0, BigDecimal.class));
        return total == null
                ? 0
                : total.longValue();
    }

    private static FileInfo toFileInfo(final Record record) {
        return new FileInfo(
                record.get(AI_CHAT_ATTACHMENT.ID),
                record.get(AI_CHAT_ATTACHMENT.FK_AI_CHAT_ID),
                record.get(AI_CHAT_ATTACHMENT.PATH),
                AiAttachmentStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(
                        record.get(AI_CHAT_ATTACHMENT.STATUS).byteValue()),
                record.get(AI_CHAT_ATTACHMENT.SOURCE_DOC_UUID),
                record.get(AI_CHAT_ATTACHMENT.EXPIRE_TIME_MS),
                record.get(AI_CHAT_ATTACHMENT.ERROR_MESSAGE),
                Objects.requireNonNullElse(record.get(AI_CHAT_ATTACHMENT.SIZE_BYTES), 0L));
    }

    @Override
    public List<Integer> findExpired(final long nowMs, final int limit) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                .select(AI_CHAT_ATTACHMENT.ID)
                .from(AI_CHAT_ATTACHMENT)
                .where(AI_CHAT_ATTACHMENT.EXPIRE_TIME_MS.le(nowMs))
                .and(AI_CHAT_ATTACHMENT.STATUS.ne((int) AiAttachmentStatus.UNAVAILABLE.getPrimitiveValue()))
                .orderBy(AI_CHAT_ATTACHMENT.EXPIRE_TIME_MS)
                .limit(limit)
                .fetch(AI_CHAT_ATTACHMENT.ID));
    }

    @Override
    public void makeUnavailable(final int fileId, final String reason) {
        JooqUtil.transaction(aiDbConnProvider, context -> {
            context.deleteFrom(AI_CHAT_FILE_CHUNK)
                    .where(AI_CHAT_FILE_CHUNK.FK_ATTACHMENT_ID.eq(fileId))
                    .execute();
            context.update(AI_CHAT_ATTACHMENT)
                    .set(AI_CHAT_ATTACHMENT.STATUS, (int) AiAttachmentStatus.UNAVAILABLE.getPrimitiveValue())
                    .set(AI_CHAT_ATTACHMENT.ERROR_MESSAGE, reason)
                    .set(AI_CHAT_ATTACHMENT.SIZE_BYTES, 0L)
                    .set(AI_CHAT_ATTACHMENT.UPDATE_TIME_MS, System.currentTimeMillis())
                    .where(AI_CHAT_ATTACHMENT.ID.eq(fileId))
                    .execute();
        });
    }

    @Override
    public List<Integer> findAvailable(final List<Integer> fileIds) {
        if (fileIds.isEmpty()) {
            return List.of();
        }
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                .select(AI_CHAT_ATTACHMENT.ID)
                .from(AI_CHAT_ATTACHMENT)
                .where(AI_CHAT_ATTACHMENT.ID.in(fileIds))
                .and(AI_CHAT_ATTACHMENT.STATUS.ne((int) AiAttachmentStatus.UNAVAILABLE.getPrimitiveValue()))
                .fetch(AI_CHAT_ATTACHMENT.ID));
    }

    @Override
    public long getTotalSize(final int chatId) {
        final Long total = JooqUtil.contextResult(aiDbConnProvider, context -> context
                .select(DSL.sum(AI_CHAT_ATTACHMENT.SIZE_BYTES))
                .from(AI_CHAT_ATTACHMENT)
                .where(AI_CHAT_ATTACHMENT.FK_AI_CHAT_ID.eq(chatId))
                .fetchOne(0, Long.class));
        return Objects.requireNonNullElse(total, 0L);
    }
}
