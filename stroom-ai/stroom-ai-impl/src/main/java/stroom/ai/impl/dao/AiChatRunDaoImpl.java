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

import stroom.ai.impl.AiChatRunDao;
import stroom.ai.impl.db.AiDbConnProvider;
import stroom.ai.shared.AiChatRun;
import stroom.ai.shared.AiChatRunStatus;
import stroom.ai.shared.AiChatRunStopReason;
import stroom.db.util.JooqUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import org.jooq.DSLContext;
import org.jooq.Record;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static stroom.ai.impl.db.jooq.tables.AiChat.AI_CHAT;
import static stroom.ai.impl.db.jooq.tables.AiChatRun.AI_CHAT_RUN;

public class AiChatRunDaoImpl implements AiChatRunDao {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1024;

    private static final List<Byte> ACTIVE_STATUSES = Arrays.stream(AiChatRunStatus.values())
            .filter(AiChatRunStatus::isActive)
            .map(AiChatRunStatus::getPrimitiveValue)
            .toList();

    private static final Function<Record, AiChatRun> RECORD_TO_RUN = record ->
            new AiChatRun(
                    record.get(AI_CHAT_RUN.ID),
                    record.get(AI_CHAT_RUN.FK_AI_CHAT_ID),
                    AiChatRunStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(record.get(AI_CHAT_RUN.STATUS)),
                    NullSafe.get(record.get(AI_CHAT_RUN.STOP_REASON),
                            AiChatRunStopReason.PRIMITIVE_VALUE_CONVERTER::fromPrimitiveValue),
                    record.get(AI_CHAT_RUN.START_TIME_MS),
                    record.get(AI_CHAT_RUN.END_TIME_MS),
                    record.get(AI_CHAT_RUN.UNATTENDED),
                    record.get(AI_CHAT_RUN.ERROR_MESSAGE));

    private final AiDbConnProvider aiDbConnProvider;

    @Inject
    AiChatRunDaoImpl(final AiDbConnProvider aiDbConnProvider) {
        this.aiDbConnProvider = aiDbConnProvider;
    }

    @Override
    public AiChatRun create(final int chatId,
                            final String userUuid,
                            final AiChatRunStatus status,
                            final String nodeName,
                            final String sessionIdHash) {
        final Integer id = JooqUtil.contextResult(aiDbConnProvider, context ->
                insert(context, chatId, userUuid, status, nodeName, sessionIdHash));
        return get(id).orElseThrow();
    }

    @Override
    public Optional<AiChatRun> createIfNoneActive(final int chatId,
                                                  final String userUuid,
                                                  final AiChatRunStatus status,
                                                  final String nodeName,
                                                  final String sessionIdHash) {
        final Integer id = JooqUtil.transactionResult(aiDbConnProvider, context -> {
            // Lock the chat so that a run cannot be started by someone else while this one is checked.
            context.select(AI_CHAT.ID)
                    .from(AI_CHAT)
                    .where(AI_CHAT.ID.eq(chatId))
                    .forUpdate()
                    .fetchOptional()
                    .orElseThrow(() -> new IllegalArgumentException("Chat " + chatId + " not found"));
            final boolean active = context.fetchExists(context
                    .selectOne()
                    .from(AI_CHAT_RUN)
                    .where(AI_CHAT_RUN.FK_AI_CHAT_ID.eq(chatId))
                    .and(AI_CHAT_RUN.STATUS.in(ACTIVE_STATUSES)));
            return active
                    ? null
                    : insert(context, chatId, userUuid, status, nodeName, sessionIdHash);
        });
        return id == null
                ? Optional.empty()
                : get(id);
    }

    private Integer insert(final DSLContext context,
                           final int chatId,
                           final String userUuid,
                           final AiChatRunStatus status,
                           final String nodeName,
                           final String sessionIdHash) {
        final long now = System.currentTimeMillis();
        final Integer id = context
                .insertInto(AI_CHAT_RUN)
                .set(AI_CHAT_RUN.VERSION, 1)
                .set(AI_CHAT_RUN.CREATE_TIME_MS, now)
                .set(AI_CHAT_RUN.UPDATE_TIME_MS, now)
                .set(AI_CHAT_RUN.FK_AI_CHAT_ID, chatId)
                .set(AI_CHAT_RUN.USER_UUID, userUuid)
                .set(AI_CHAT_RUN.STATUS, status.getPrimitiveValue())
                .set(AI_CHAT_RUN.NODE_NAME, nodeName)
                .set(AI_CHAT_RUN.SESSION_ID_HASH, sessionIdHash)
                .set(AI_CHAT_RUN.HEARTBEAT_MS, now)
                .set(AI_CHAT_RUN.START_TIME_MS, now)
                .returning(AI_CHAT_RUN.ID)
                .fetchOne(AI_CHAT_RUN.ID);
        return Objects.requireNonNull(id, "Null run id");
    }

    @Override
    public Optional<AiChatRun> get(final int runId) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                        .select()
                        .from(AI_CHAT_RUN)
                        .where(AI_CHAT_RUN.ID.eq(runId))
                        .fetchOptional())
                .map(RECORD_TO_RUN);
    }

    @Override
    public Optional<AiChatRun> getLatest(final int chatId) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                        .select()
                        .from(AI_CHAT_RUN)
                        .where(AI_CHAT_RUN.FK_AI_CHAT_ID.eq(chatId))
                        .orderBy(AI_CHAT_RUN.ID.desc())
                        .limit(1)
                        .fetchOptional())
                .map(RECORD_TO_RUN);
    }

    @Override
    public Optional<AiChatRun> getActive(final int chatId) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                        .select()
                        .from(AI_CHAT_RUN)
                        .where(AI_CHAT_RUN.FK_AI_CHAT_ID.eq(chatId))
                        .and(AI_CHAT_RUN.STATUS.in(ACTIVE_STATUSES))
                        .orderBy(AI_CHAT_RUN.ID.desc())
                        .limit(1)
                        .fetchOptional())
                .map(RECORD_TO_RUN);
    }

    @Override
    public boolean compareAndSetStatus(final int runId,
                                       final Collection<AiChatRunStatus> expected,
                                       final AiChatRunStatus newStatus,
                                       final AiChatRunStopReason stopReason,
                                       final String errorMessage) {
        final long now = System.currentTimeMillis();
        final List<Byte> expectedValues = expected.stream()
                .map(AiChatRunStatus::getPrimitiveValue)
                .toList();
        final int count = JooqUtil.contextResult(aiDbConnProvider, context -> context
                .update(AI_CHAT_RUN)
                .set(AI_CHAT_RUN.STATUS, newStatus.getPrimitiveValue())
                .set(AI_CHAT_RUN.STOP_REASON, NullSafe.get(stopReason, AiChatRunStopReason::getPrimitiveValue))
                .set(AI_CHAT_RUN.ERROR_MESSAGE, truncate(errorMessage))
                .set(AI_CHAT_RUN.END_TIME_MS, newStatus.isActive()
                        ? null
                        : now)
                .set(AI_CHAT_RUN.UPDATE_TIME_MS, now)
                .set(AI_CHAT_RUN.VERSION, AI_CHAT_RUN.VERSION.plus(1))
                .where(AI_CHAT_RUN.ID.eq(runId))
                .and(AI_CHAT_RUN.STATUS.in(expectedValues))
                .execute());
        return count == 1;
    }

    @Override
    public void heartbeat(final Collection<Integer> runIds) {
        if (runIds.isEmpty()) {
            return;
        }
        JooqUtil.context(aiDbConnProvider, context -> context
                .update(AI_CHAT_RUN)
                .set(AI_CHAT_RUN.HEARTBEAT_MS, System.currentTimeMillis())
                .where(AI_CHAT_RUN.ID.in(runIds))
                .and(AI_CHAT_RUN.STATUS.in(ACTIVE_STATUSES))
                .execute());
    }

    @Override
    public Map<Integer, AiChatRunStatus> getStatuses(final Collection<Integer> runIds) {
        if (runIds.isEmpty()) {
            return Map.of();
        }
        final Map<Integer, AiChatRunStatus> statuses = new HashMap<>();
        JooqUtil.contextResult(aiDbConnProvider, context -> context
                        .select(AI_CHAT_RUN.ID, AI_CHAT_RUN.STATUS)
                        .from(AI_CHAT_RUN)
                        .where(AI_CHAT_RUN.ID.in(runIds))
                        .fetch())
                .forEach(record -> statuses.put(record.value1(),
                        AiChatRunStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(record.value2())));
        return statuses;
    }

    @Override
    public List<AiChatRun> interruptStale(final long staleBeforeMs) {
        final List<AiChatRun> stale = JooqUtil.contextResult(aiDbConnProvider, context -> context
                        .select()
                        .from(AI_CHAT_RUN)
                        .where(AI_CHAT_RUN.STATUS.in(ACTIVE_STATUSES))
                        .and(AI_CHAT_RUN.HEARTBEAT_MS.lt(staleBeforeMs))
                        .fetch())
                .map(RECORD_TO_RUN::apply);
        final List<AiChatRun> interrupted = new ArrayList<>();
        final long now = System.currentTimeMillis();
        for (final AiChatRun run : stale) {
            // Only if it is still stale, in case its node has just sent a heartbeat.
            final int count = JooqUtil.contextResult(aiDbConnProvider, context -> context
                    .update(AI_CHAT_RUN)
                    .set(AI_CHAT_RUN.STATUS, AiChatRunStatus.INTERRUPTED.getPrimitiveValue())
                    .set(AI_CHAT_RUN.STOP_REASON, AiChatRunStopReason.NODE_LOST.getPrimitiveValue())
                    .set(AI_CHAT_RUN.END_TIME_MS, now)
                    .set(AI_CHAT_RUN.UPDATE_TIME_MS, now)
                    .set(AI_CHAT_RUN.VERSION, AI_CHAT_RUN.VERSION.plus(1))
                    .where(AI_CHAT_RUN.ID.eq(run.getId()))
                    .and(AI_CHAT_RUN.STATUS.in(ACTIVE_STATUSES))
                    .and(AI_CHAT_RUN.HEARTBEAT_MS.lt(staleBeforeMs))
                    .execute());
            if (count == 1) {
                interrupted.add(run);
            }
        }
        return interrupted;
    }

    @Override
    public List<AiChatRun> getActiveBySession(final String sessionIdHash) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                        .select()
                        .from(AI_CHAT_RUN)
                        .where(AI_CHAT_RUN.SESSION_ID_HASH.eq(sessionIdHash))
                        .and(AI_CHAT_RUN.STATUS.in(ACTIVE_STATUSES))
                        .fetch())
                .map(RECORD_TO_RUN::apply);
    }

    @Override
    public void correctStopReason(final String sessionIdHash,
                                  final AiChatRunStopReason from,
                                  final AiChatRunStopReason to,
                                  final long endedAfterMs) {
        JooqUtil.context(aiDbConnProvider, context -> context
                .update(AI_CHAT_RUN)
                .set(AI_CHAT_RUN.STOP_REASON, to.getPrimitiveValue())
                .set(AI_CHAT_RUN.UPDATE_TIME_MS, System.currentTimeMillis())
                .where(AI_CHAT_RUN.SESSION_ID_HASH.eq(sessionIdHash))
                .and(AI_CHAT_RUN.STATUS.eq(AiChatRunStatus.STOPPED.getPrimitiveValue()))
                .and(AI_CHAT_RUN.STOP_REASON.eq(from.getPrimitiveValue()))
                .and(AI_CHAT_RUN.END_TIME_MS.gt(endedAfterMs))
                .execute());
    }

    @Override
    public void setUnattended(final int runId, final boolean unattended) {
        JooqUtil.context(aiDbConnProvider, context -> context
                .update(AI_CHAT_RUN)
                .set(AI_CHAT_RUN.UNATTENDED, unattended)
                .set(AI_CHAT_RUN.UPDATE_TIME_MS, System.currentTimeMillis())
                .where(AI_CHAT_RUN.ID.eq(runId))
                .execute());
    }

    @Override
    public int countWorking(final String userUuid) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                .fetchCount(AI_CHAT_RUN,
                        AI_CHAT_RUN.USER_UUID.eq(userUuid)
                                .and(AI_CHAT_RUN.STATUS.in(
                                        AiChatRunStatus.RUNNING.getPrimitiveValue(),
                                        AiChatRunStatus.STOPPING.getPrimitiveValue()))));
    }

    @Override
    public void addUsage(final int runId, final long inputTokens, final long outputTokens, final int toolCalls) {
        JooqUtil.context(aiDbConnProvider, context -> context
                .update(AI_CHAT_RUN)
                .set(AI_CHAT_RUN.INPUT_TOKENS, AI_CHAT_RUN.INPUT_TOKENS.plus(inputTokens))
                .set(AI_CHAT_RUN.OUTPUT_TOKENS, AI_CHAT_RUN.OUTPUT_TOKENS.plus(outputTokens))
                .set(AI_CHAT_RUN.TOOL_CALL_COUNT, AI_CHAT_RUN.TOOL_CALL_COUNT.plus(toolCalls))
                .where(AI_CHAT_RUN.ID.eq(runId))
                .execute());
    }

    @Override
    public void deleteByChat(final int chatId) {
        JooqUtil.context(aiDbConnProvider, context -> context
                .deleteFrom(AI_CHAT_RUN)
                .where(AI_CHAT_RUN.FK_AI_CHAT_ID.eq(chatId))
                .execute());
    }

    private static String truncate(final String errorMessage) {
        if (errorMessage == null || errorMessage.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return errorMessage;
        }
        return errorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH - 1) + "…";
    }
}
