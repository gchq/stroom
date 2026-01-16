/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.processor.impl.db;

import stroom.db.util.JooqUtil;
import stroom.node.shared.NodeGroup;
import stroom.processor.impl.ProcessorProfileDao;
import stroom.processor.shared.FindProcessorProfileRequest;
import stroom.processor.shared.ProcessorProfile;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import org.jooq.Condition;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static stroom.processor.impl.db.jooq.tables.ProcessorProfile.PROCESSOR_PROFILE;

class ProcessorProfileDaoImpl implements ProcessorProfileDao {

    private static final RecordToProcessorProfileMapper RECORD_TO_PROCESSOR_MAPPER =
            new RecordToProcessorProfileMapper();

    private final ProcessorDbConnProvider processorDbConnProvider;

    @Inject
    ProcessorProfileDaoImpl(final ProcessorDbConnProvider processorDbConnProvider) {
        this.processorDbConnProvider = processorDbConnProvider;
    }

    @Override
    public ResultPage<ProcessorProfile> find(final FindProcessorProfileRequest request) {
        final List<Condition> conditions = new ArrayList<>();
        if (NullSafe.isNonBlankString(request.getFilter())) {
            conditions.add(NODE_GROUP.NAME.startsWith(request.getFilter()));
        }
        final int offset = JooqUtil.getOffset(request.getPageRequest());
        final int limit = JooqUtil.getLimit(request.getPageRequest(), true);

        final List<NodeGroup> list = JooqUtil.contextResult(nodeDbConnProvider, context -> context
                        .select()
                        .from(NODE_GROUP)
                        .where(conditions)
                        .orderBy(NODE_GROUP.NAME)
                        .offset(offset)
                        .limit(limit)
                        .fetch())
                .map(RECORD_TO_NODE_GROUP_MAPPER::apply);
        return ResultPage.createCriterialBasedList(list, request);
    }

    @Override
    public ProcessorProfile getOrCreate(final ProcessorProfile processorProfile) {
        final ProcessorProfileData processorProfileData = new ProcessorProfileData(
                processorProfile.getProfilePeriods(),
                processorProfile.getTimeZone());
        final String json = JsonUtil.writeValueAsString(processorProfileData);

        final Optional<Integer> optional = JooqUtil.onDuplicateKeyIgnore(() ->
                JooqUtil.contextResult(processorDbConnProvider, context -> context
                        .insertInto(PROCESSOR_PROFILE,
                                PROCESSOR_PROFILE.VERSION,
                                PROCESSOR_PROFILE.CREATE_USER,
                                PROCESSOR_PROFILE.CREATE_TIME_MS,
                                PROCESSOR_PROFILE.UPDATE_USER,
                                PROCESSOR_PROFILE.UPDATE_TIME_MS,
                                PROCESSOR_PROFILE.NAME,
                                PROCESSOR_PROFILE.NODE_GROUP_NAME,
                                PROCESSOR_PROFILE.DATA)
                        .values(1,
                                processorProfile.getCreateUser(),
                                processorProfile.getCreateTimeMs(),
                                processorProfile.getUpdateUser(),
                                processorProfile.getUpdateTimeMs(),
                                processorProfile.getName(),
                                processorProfile.getNodeGroupName(),
                                json)
                        .returning(PROCESSOR_PROFILE.ID)
                        .fetchOptional(PROCESSOR_PROFILE.ID)));
        return optional.map(id ->
                processorProfile.copy().id(id).version(1).build()).orElse(fetchByName(processorProfile.getName()));
    }

    @Override
    public ProcessorProfile fetch(final int id) {
        return JooqUtil.contextResult(processorDbConnProvider, context -> context
                        .select()
                        .from(PROCESSOR_PROFILE)
                        .where(PROCESSOR_PROFILE.ID.eq(id))
                        .fetchOptional())
                .map(RECORD_TO_PROCESSOR_MAPPER)
                .orElse(null);
    }

    @Override
    public ProcessorProfile fetchByName(final String name) {
        return JooqUtil.contextResult(processorDbConnProvider, context -> context
                        .select()
                        .from(PROCESSOR_PROFILE)
                        .where(PROCESSOR_PROFILE.NAME.eq(name))
                        .fetchOptional())
                .map(RECORD_TO_PROCESSOR_MAPPER)
                .orElse(null);
    }

    @Override
    public ProcessorProfile update(final ProcessorProfile processorProfile) {
        final ProcessorProfileData processorProfileData = new ProcessorProfileData(
                processorProfile.getProfilePeriods(),
                processorProfile.getTimeZone());
        final String json = JsonUtil.writeValueAsString(processorProfileData);

        final int result = JooqUtil.contextResult(processorDbConnProvider, context -> context
                .update(PROCESSOR_PROFILE)
                .set(PROCESSOR_PROFILE.VERSION, PROCESSOR_PROFILE.VERSION.plus(1))
                .set(PROCESSOR_PROFILE.UPDATE_USER, processorProfile.getUpdateUser())
                .set(PROCESSOR_PROFILE.UPDATE_TIME_MS, processorProfile.getUpdateTimeMs())
                .set(PROCESSOR_PROFILE.NAME, processorProfile.getName())
                .set(PROCESSOR_PROFILE.NODE_GROUP_NAME, processorProfile.getNodeGroupName())
                .set(PROCESSOR_PROFILE.DATA, json)
                .where(PROCESSOR_PROFILE.ID.eq(processorProfile.getId()))
                .and(PROCESSOR_PROFILE.VERSION.eq(processorProfile.getVersion()))
                .execute());
        if (result > 0) {
            return fetch(processorProfile.getId());
        }
        throw new RuntimeException("Version incorrect");
    }

    @Override
    public void delete(final int id) {
        JooqUtil.context(processorDbConnProvider, context -> context
                .deleteFrom(PROCESSOR_PROFILE)
                .where(PROCESSOR_PROFILE.ID.eq(id))
                .execute());
    }
}
