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

import stroom.ai.impl.AiToolApprovalDao;
import stroom.ai.impl.db.AiDbConnProvider;
import stroom.ai.impl.db.jooq.tables.records.AiToolApprovalRecord;
import stroom.ai.shared.AiToolApprovalDecision;
import stroom.db.util.JooqUtil;

import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

import static stroom.ai.impl.db.jooq.tables.AiToolApproval.AI_TOOL_APPROVAL;

public class AiToolApprovalDaoImpl implements AiToolApprovalDao {

    private final AiDbConnProvider aiDbConnProvider;

    @Inject
    AiToolApprovalDaoImpl(final AiDbConnProvider aiDbConnProvider) {
        this.aiDbConnProvider = aiDbConnProvider;
    }

    @Override
    public Optional<Approval> find(final int chatId, final String serverKey, final String toolName) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                        .selectFrom(AI_TOOL_APPROVAL)
                        .where(AI_TOOL_APPROVAL.FK_AI_CHAT_ID.eq(chatId))
                        .and(AI_TOOL_APPROVAL.SERVER_KEY.eq(serverKey))
                        .and(AI_TOOL_APPROVAL.TOOL_NAME.eq(toolName))
                        .fetchOptional())
                .map(AiToolApprovalDaoImpl::toApproval);
    }

    @Override
    public void save(final String userUuid,
                     final int chatId,
                     final String serverKey,
                     final String toolName,
                     final String toolHash,
                     final AiToolApprovalDecision decision,
                     final String updateUser) {
        final long now = System.currentTimeMillis();
        JooqUtil.context(aiDbConnProvider, context -> context
                .insertInto(AI_TOOL_APPROVAL)
                .set(AI_TOOL_APPROVAL.VERSION, 1)
                .set(AI_TOOL_APPROVAL.CREATE_TIME_MS, now)
                .set(AI_TOOL_APPROVAL.CREATE_USER, updateUser)
                .set(AI_TOOL_APPROVAL.UPDATE_TIME_MS, now)
                .set(AI_TOOL_APPROVAL.UPDATE_USER, updateUser)
                .set(AI_TOOL_APPROVAL.USER_UUID, userUuid)
                .set(AI_TOOL_APPROVAL.FK_AI_CHAT_ID, chatId)
                .set(AI_TOOL_APPROVAL.SERVER_KEY, serverKey)
                .set(AI_TOOL_APPROVAL.TOOL_NAME, toolName)
                .set(AI_TOOL_APPROVAL.TOOL_HASH, toolHash)
                .set(AI_TOOL_APPROVAL.DECISION, decision.getPrimitiveValue())
                .onDuplicateKeyUpdate()
                .set(AI_TOOL_APPROVAL.VERSION, AI_TOOL_APPROVAL.VERSION.plus(1))
                .set(AI_TOOL_APPROVAL.UPDATE_TIME_MS, now)
                .set(AI_TOOL_APPROVAL.UPDATE_USER, updateUser)
                .set(AI_TOOL_APPROVAL.TOOL_HASH, toolHash)
                .set(AI_TOOL_APPROVAL.DECISION, decision.getPrimitiveValue())
                .execute());
    }

    @Override
    public void delete(final int id) {
        JooqUtil.context(aiDbConnProvider, context -> context
                .deleteFrom(AI_TOOL_APPROVAL)
                .where(AI_TOOL_APPROVAL.ID.eq(id))
                .execute());
    }

    @Override
    public List<Approval> list(final int chatId) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                        .selectFrom(AI_TOOL_APPROVAL)
                        .where(AI_TOOL_APPROVAL.FK_AI_CHAT_ID.eq(chatId))
                        .orderBy(AI_TOOL_APPROVAL.SERVER_KEY, AI_TOOL_APPROVAL.TOOL_NAME)
                        .fetch())
                .map(AiToolApprovalDaoImpl::toApproval);
    }

    private static Approval toApproval(final AiToolApprovalRecord record) {
        return new Approval(
                record.getId(),
                record.getServerKey(),
                record.getToolName(),
                record.getToolHash(),
                AiToolApprovalDecision.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(record.getDecision()));
    }
}
