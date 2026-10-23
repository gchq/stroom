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

import stroom.ai.impl.AiToolPolicyDao;
import stroom.ai.impl.db.AiDbConnProvider;
import stroom.ai.impl.db.jooq.tables.records.AiToolPolicyRecord;
import stroom.ai.shared.AiToolAccess;
import stroom.ai.shared.AiToolCategory;
import stroom.ai.shared.AiToolCategoryGrant;
import stroom.ai.shared.AiToolPolicy;
import stroom.db.util.JooqUtil;
import stroom.util.exception.DataChangedException;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import org.jooq.DSLContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static stroom.ai.impl.db.jooq.tables.AiToolPolicy.AI_TOOL_POLICY;
import static stroom.ai.impl.db.jooq.tables.AiToolPolicyCategory.AI_TOOL_POLICY_CATEGORY;
import static stroom.ai.impl.db.jooq.tables.AiToolPolicyMember.AI_TOOL_POLICY_MEMBER;

public class AiToolPolicyDaoImpl implements AiToolPolicyDao {

    private final AiDbConnProvider aiDbConnProvider;

    @Inject
    AiToolPolicyDaoImpl(final AiDbConnProvider aiDbConnProvider) {
        this.aiDbConnProvider = aiDbConnProvider;
    }

    @Override
    public List<AiToolPolicy> list() {
        return JooqUtil.contextResult(aiDbConnProvider, context -> {
            final List<AiToolPolicyRecord> records = context
                    .selectFrom(AI_TOOL_POLICY)
                    .orderBy(AI_TOOL_POLICY.NAME)
                    .fetch();
            final Map<Integer, List<UserRef>> members = new HashMap<>();
            context.select(AI_TOOL_POLICY_MEMBER.FK_AI_TOOL_POLICY_ID, AI_TOOL_POLICY_MEMBER.USER_UUID)
                    .from(AI_TOOL_POLICY_MEMBER)
                    .orderBy(AI_TOOL_POLICY_MEMBER.ID)
                    .fetch()
                    .forEach(row -> members.computeIfAbsent(row.value1(), id -> new ArrayList<>())
                            .add(userRef(row.value2())));
            final Map<Integer, List<AiToolCategoryGrant>> grants = new HashMap<>();
            context.select(AI_TOOL_POLICY_CATEGORY.FK_AI_TOOL_POLICY_ID,
                            AI_TOOL_POLICY_CATEGORY.CATEGORY,
                            AI_TOOL_POLICY_CATEGORY.ACCESS)
                    .from(AI_TOOL_POLICY_CATEGORY)
                    .fetch()
                    .forEach(row -> grants.computeIfAbsent(row.value1(), id -> new ArrayList<>())
                            .add(grant(row.value2(), row.value3())));
            return records.stream()
                    .map(record -> toPolicy(record,
                            members.getOrDefault(record.getId(), List.of()),
                            grants.getOrDefault(record.getId(), List.of())))
                    .toList();
        });
    }

    @Override
    public Optional<AiToolPolicy> fetch(final int id) {
        return JooqUtil.contextResult(aiDbConnProvider, context -> context
                .selectFrom(AI_TOOL_POLICY)
                .where(AI_TOOL_POLICY.ID.eq(id))
                .fetchOptional()
                .map(record -> toPolicy(record, members(context, id), grants(context, id))));
    }

    @Override
    public AiToolPolicy create(final AiToolPolicy policy, final String user) {
        final long now = System.currentTimeMillis();
        final int id = JooqUtil.transactionResult(aiDbConnProvider, context -> {
            final int newId = context.insertInto(AI_TOOL_POLICY)
                    .set(AI_TOOL_POLICY.VERSION, 1)
                    .set(AI_TOOL_POLICY.CREATE_TIME_MS, now)
                    .set(AI_TOOL_POLICY.CREATE_USER, user)
                    .set(AI_TOOL_POLICY.UPDATE_TIME_MS, now)
                    .set(AI_TOOL_POLICY.UPDATE_USER, user)
                    .set(AI_TOOL_POLICY.NAME, policy.getName())
                    .set(AI_TOOL_POLICY.DESCRIPTION, policy.getDescription())
                    .set(AI_TOOL_POLICY.ENABLED, policy.isEnabled())
                    .set(AI_TOOL_POLICY.ALL_USERS, policy.isAllUsers())
                    .returning(AI_TOOL_POLICY.ID)
                    .fetchOne(AI_TOOL_POLICY.ID);
            writeMembersAndCategories(context, newId, policy);
            return newId;
        });
        return fetch(id).orElseThrow();
    }

    @Override
    public AiToolPolicy update(final AiToolPolicy policy, final String user) {
        final int id = policy.getId();
        JooqUtil.transaction(aiDbConnProvider, context -> {
            final int updated = context.update(AI_TOOL_POLICY)
                    .set(AI_TOOL_POLICY.VERSION, AI_TOOL_POLICY.VERSION.plus(1))
                    .set(AI_TOOL_POLICY.UPDATE_TIME_MS, System.currentTimeMillis())
                    .set(AI_TOOL_POLICY.UPDATE_USER, user)
                    .set(AI_TOOL_POLICY.NAME, policy.getName())
                    .set(AI_TOOL_POLICY.DESCRIPTION, policy.getDescription())
                    .set(AI_TOOL_POLICY.ENABLED, policy.isEnabled())
                    .set(AI_TOOL_POLICY.ALL_USERS, policy.isAllUsers())
                    .where(AI_TOOL_POLICY.ID.eq(id))
                    .and(AI_TOOL_POLICY.VERSION.eq(policy.getVersion()))
                    .execute();
            if (updated == 0) {
                throw new DataChangedException("Tool policy '" + policy.getName() + "' has been changed or "
                                               + "deleted by someone else since it was read. Reload it and "
                                               + "make the change again.");
            }
            deleteMembersAndCategories(context, id);
            writeMembersAndCategories(context, id, policy);
        });
        return fetch(id).orElseThrow();
    }

    @Override
    public boolean delete(final int id) {
        return JooqUtil.transactionResult(aiDbConnProvider, context -> {
            deleteMembersAndCategories(context, id);
            return context.deleteFrom(AI_TOOL_POLICY)
                           .where(AI_TOOL_POLICY.ID.eq(id))
                           .execute() > 0;
        });
    }

    private static void writeMembersAndCategories(final DSLContext context, final int id, final AiToolPolicy policy) {
        policy.getMembers().stream()
                .map(UserRef::getUuid)
                .distinct()
                .forEach(uuid -> context.insertInto(AI_TOOL_POLICY_MEMBER)
                        .set(AI_TOOL_POLICY_MEMBER.FK_AI_TOOL_POLICY_ID, id)
                        .set(AI_TOOL_POLICY_MEMBER.USER_UUID, uuid)
                        .execute());
        policy.getGrants().forEach(grant -> context.insertInto(AI_TOOL_POLICY_CATEGORY)
                .set(AI_TOOL_POLICY_CATEGORY.FK_AI_TOOL_POLICY_ID, id)
                .set(AI_TOOL_POLICY_CATEGORY.CATEGORY, grant.getCategory().getPrimitiveValue())
                .set(AI_TOOL_POLICY_CATEGORY.ACCESS, grant.getAccess().getPrimitiveValue())
                .execute());
    }

    private static void deleteMembersAndCategories(final DSLContext context, final int id) {
        context.deleteFrom(AI_TOOL_POLICY_MEMBER)
                .where(AI_TOOL_POLICY_MEMBER.FK_AI_TOOL_POLICY_ID.eq(id))
                .execute();
        context.deleteFrom(AI_TOOL_POLICY_CATEGORY)
                .where(AI_TOOL_POLICY_CATEGORY.FK_AI_TOOL_POLICY_ID.eq(id))
                .execute();
    }

    private static List<UserRef> members(final DSLContext context, final int id) {
        return context.select(AI_TOOL_POLICY_MEMBER.USER_UUID)
                .from(AI_TOOL_POLICY_MEMBER)
                .where(AI_TOOL_POLICY_MEMBER.FK_AI_TOOL_POLICY_ID.eq(id))
                .orderBy(AI_TOOL_POLICY_MEMBER.ID)
                .fetch(AI_TOOL_POLICY_MEMBER.USER_UUID)
                .stream()
                .map(AiToolPolicyDaoImpl::userRef)
                .toList();
    }

    private static List<AiToolCategoryGrant> grants(final DSLContext context, final int id) {
        return context.select(AI_TOOL_POLICY_CATEGORY.CATEGORY, AI_TOOL_POLICY_CATEGORY.ACCESS)
                .from(AI_TOOL_POLICY_CATEGORY)
                .where(AI_TOOL_POLICY_CATEGORY.FK_AI_TOOL_POLICY_ID.eq(id))
                .fetch()
                .map(row -> grant(row.value1(), row.value2()));
    }

    private static AiToolCategoryGrant grant(final Byte category, final Byte access) {
        return AiToolCategoryGrant.of(
                AiToolCategory.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(category),
                AiToolAccess.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(access));
    }

    private static UserRef userRef(final String uuid) {
        return UserRef.builder().uuid(uuid).build();
    }

    private static AiToolPolicy toPolicy(final AiToolPolicyRecord record,
                                         final List<UserRef> members,
                                         final List<AiToolCategoryGrant> grants) {
        return AiToolPolicy.builder()
                .id(record.getId())
                .version(record.getVersion())
                .createTimeMs(record.getCreateTimeMs())
                .createUser(record.getCreateUser())
                .updateTimeMs(record.getUpdateTimeMs())
                .updateUser(record.getUpdateUser())
                .name(record.getName())
                .description(record.getDescription())
                .enabled(Boolean.TRUE.equals(record.getEnabled()))
                .allUsers(Boolean.TRUE.equals(record.getAllUsers()))
                .members(members)
                .grants(grants.stream()
                        .sorted(Comparator.comparing(AiToolCategoryGrant::getCategory))
                        .toList())
                .build();
    }
}
