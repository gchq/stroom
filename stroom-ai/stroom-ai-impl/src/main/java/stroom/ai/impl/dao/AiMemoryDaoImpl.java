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

import stroom.ai.impl.AiMemoryDao;
import stroom.ai.impl.db.AiDbConnProvider;
import stroom.ai.impl.db.jooq.tables.records.AiMemoryRecord;
import stroom.ai.shared.AiMemory;
import stroom.ai.shared.AiMemoryScope;
import stroom.db.util.JooqUtil;

import jakarta.inject.Inject;
import org.jooq.exception.DataAccessException;
import org.jooq.exception.SQLStateClass;

import java.util.List;
import java.util.Optional;

import static stroom.ai.impl.db.jooq.tables.AiMemory.AI_MEMORY;

public class AiMemoryDaoImpl implements AiMemoryDao {

    private final AiDbConnProvider aiDbConnProvider;

    @Inject
    AiMemoryDaoImpl(final AiDbConnProvider aiDbConnProvider) {
        this.aiDbConnProvider = aiDbConnProvider;
    }

    @Override
    public List<AiMemory> list(final String userUuid, final AiMemoryScope scope, final String scopeKey) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                        .selectFrom(AI_MEMORY)
                        .where(AI_MEMORY.USER_UUID.eq(userUuid))
                        .and(AI_MEMORY.SCOPE.eq(scope.getPrimitiveValue()))
                        .and(AI_MEMORY.SCOPE_KEY.eq(scopeKey))
                        .orderBy(AI_MEMORY.NAME)
                        .fetch())
                .map(AiMemoryDaoImpl::toMemory);
    }

    @Override
    public Optional<AiMemory> get(final String userUuid,
                                  final AiMemoryScope scope,
                                  final String scopeKey,
                                  final String name) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                        .selectFrom(AI_MEMORY)
                        .where(AI_MEMORY.USER_UUID.eq(userUuid))
                        .and(AI_MEMORY.SCOPE.eq(scope.getPrimitiveValue()))
                        .and(AI_MEMORY.SCOPE_KEY.eq(scopeKey))
                        .and(AI_MEMORY.NAME.eq(name))
                        .fetchOptional())
                .map(AiMemoryDaoImpl::toMemory);
    }

    @Override
    public Optional<AiMemory> get(final int id) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                        .selectFrom(AI_MEMORY)
                        .where(AI_MEMORY.ID.eq(id))
                        .fetchOptional())
                .map(AiMemoryDaoImpl::toMemory);
    }

    @Override
    public Optional<AiMemory> create(final String userUuid,
                                     final AiMemoryScope scope,
                                     final String scopeKey,
                                     final String name,
                                     final String content,
                                     final String updateUser) {
        final long now = System.currentTimeMillis();
        try {
            final Integer id = JooqUtil.contextResult(aiDbConnProvider, context -> context
                    .insertInto(AI_MEMORY)
                    .set(AI_MEMORY.VERSION, 1)
                    .set(AI_MEMORY.CREATE_TIME_MS, now)
                    .set(AI_MEMORY.CREATE_USER, updateUser)
                    .set(AI_MEMORY.UPDATE_TIME_MS, now)
                    .set(AI_MEMORY.UPDATE_USER, updateUser)
                    .set(AI_MEMORY.USER_UUID, userUuid)
                    .set(AI_MEMORY.SCOPE, scope.getPrimitiveValue())
                    .set(AI_MEMORY.SCOPE_KEY, scopeKey)
                    .set(AI_MEMORY.NAME, name)
                    .set(AI_MEMORY.CONTENT, content)
                    .returning(AI_MEMORY.ID)
                    .fetchOne(AI_MEMORY.ID));
            return get(id);
        } catch (final DataAccessException e) {
            // One with the name already exists.
            if (e.sqlStateClass() == SQLStateClass.C23_INTEGRITY_CONSTRAINT_VIOLATION) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public Optional<AiMemory> update(final int id,
                                     final int expectedVersion,
                                     final String content,
                                     final String updateUser) {
        final int count = JooqUtil.contextResult(aiDbConnProvider, context -> context
                .update(AI_MEMORY)
                .set(AI_MEMORY.VERSION, expectedVersion + 1)
                .set(AI_MEMORY.CONTENT, content)
                .set(AI_MEMORY.UPDATE_TIME_MS, System.currentTimeMillis())
                .set(AI_MEMORY.UPDATE_USER, updateUser)
                .where(AI_MEMORY.ID.eq(id))
                .and(AI_MEMORY.VERSION.eq(expectedVersion))
                .execute());
        return count == 1
                ? get(id)
                : Optional.empty();
    }

    @Override
    public boolean delete(final int id, final int expectedVersion) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                .deleteFrom(AI_MEMORY)
                .where(AI_MEMORY.ID.eq(id))
                .and(AI_MEMORY.VERSION.eq(expectedVersion))
                .execute()) == 1;
    }

    @Override
    public void deleteChatMemory(final int chatId) {
        JooqUtil.context(aiDbConnProvider, context -> context
                .deleteFrom(AI_MEMORY)
                .where(AI_MEMORY.SCOPE.eq(AiMemoryScope.CHAT.getPrimitiveValue()))
                .and(AI_MEMORY.SCOPE_KEY.eq(String.valueOf(chatId)))
                .execute());
    }

    private static AiMemory toMemory(final AiMemoryRecord record) {
        return new AiMemory(
                record.getId(),
                record.getVersion(),
                AiMemoryScope.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(record.getScope()),
                record.getScopeKey(),
                record.getName(),
                record.getContent(),
                record.getUpdateTimeMs(),
                record.getUpdateUser());
    }
}
