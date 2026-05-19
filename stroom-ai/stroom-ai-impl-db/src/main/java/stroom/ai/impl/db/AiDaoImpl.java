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
import stroom.util.shared.UserRef;

import java.util.List;


public class AiDaoImpl implements AiDao {

    @Override
    public AiChat newChat(final UserRef userRef) {
        return null;
    }

    @Override
    public void storeMessage(final int chatId, final AiChatMessage message) {

    }

    @Override
    public List<AiChatMessage> getMessages(final int chatId) {
        return List.of();
    }
    ////    private static final Logger LOGGER = LoggerFactory.getLogger(JobDao.class);
//
//    public static final Function<Record, Job> RECORD_TO_JOB_MAPPER = record -> Job
//            .builder()
//            .id(record.get(JOB.ID))
//            .version(record.get(JOB.VERSION))
//            .createTimeMs(record.get(JOB.CREATE_TIME_MS))
//            .createUser(record.get(JOB.CREATE_USER))
//            .updateTimeMs(record.get(JOB.UPDATE_TIME_MS))
//            .updateUser(record.get(JOB.UPDATE_USER))
//            .name(record.get(JOB.NAME))
//            .enabled(record.get(JOB.ENABLED))
//            .build();
//
//    public static final BiFunction<Job, JobRecord, JobRecord> JOB_TO_RECORD_MAPPER =
//            (job, record) -> {
//                record.set(JOB.ID, job.getId());
//                record.set(JOB.VERSION, job.getVersion());
//                record.set(JOB.CREATE_TIME_MS, job.getCreateTimeMs());
//                record.set(JOB.CREATE_USER, job.getCreateUser());
//                record.set(JOB.UPDATE_TIME_MS, job.getUpdateTimeMs());
//                record.set(JOB.UPDATE_USER, job.getUpdateUser());
//                record.set(JOB.NAME, job.getName());
//                record.set(JOB.ENABLED, job.isEnabled());
//                return record;
//            };
//
//    private static final Map<String, Field<?>> FIELD_MAP = Map.of(
//            FindJobCriteria.FIELD_ID, JOB.ID,
//            FindJobCriteria.FIELD_NAME, JOB.NAME);
//
//    private final GenericDao<JobRecord, Job, Integer> genericDao;
//    private final JobDbConnProvider jobDbConnProvider;
//
//    @Inject
//    AiDaoImpl(final JobDbConnProvider jobDbConnProvider) {
//        genericDao = new GenericDao<>(
//                jobDbConnProvider,
//                JOB,
//                JOB.ID,
//                JOB_TO_RECORD_MAPPER,
//                RECORD_TO_JOB_MAPPER);
//        this.jobDbConnProvider = jobDbConnProvider;
//    }
//
//    @Override
//    public Job create(@NotNull final Job job) {
//        return genericDao.create(job);
//    }
//
//    @Override
//    public Job update(@NotNull final Job job) {
//        return genericDao.update(job);
//    }
//
//    @Override
//    public boolean delete(final int id) {
//        return genericDao.delete(id);
//    }
//
//    @Override
//    public Optional<Job> fetch(final int id) {
//        return genericDao.fetch(id);
//    }
//
//    @Override
//    public ResultPage<Job> find(final FindJobCriteria criteria) {
//        final Collection<Condition> conditions = JooqUtil.conditions(
//                JooqUtil.getStringCondition(JOB.NAME, criteria.getName()));
//
//        final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(FIELD_MAP, criteria);
//        final int offset = JooqUtil.getOffset(criteria.getPageRequest());
//        final int limit = JooqUtil.getLimit(criteria.getPageRequest(), true);
//        final List<Job> list = JooqUtil.contextResult(jobDbConnProvider, context -> context
//                        .select()
//                        .from(JOB)
//                        .where(conditions)
//                        .orderBy(orderFields)
//                        .limit(offset, limit)
//                        .fetch())
//                .map(RECORD_TO_JOB_MAPPER::apply);
//        return ResultPage.createCriterialBasedList(list, criteria);
//    }
//
//    @Override
//    public int deleteOrphans() {
//        return JooqUtil.contextResult(jobDbConnProvider, context -> context
//                .deleteFrom(JOB)
//                .where(JOB.ID.notIn(
//                        context.select(JOB_NODE.JOB_ID)
//                                .from(JOB_NODE)))
//                .execute());
//    }
//
//    @Override
//    public int setJobsEnabled(final String nodeName,
//                              final boolean enabled,
//                              final Set<String> includeJobs,
//                              final Set<String> excludeJobs) {
//        return JooqUtil.contextResult(jobDbConnProvider, context -> context
//                .update(JOB_NODE)
//                .set(JOB_NODE.ENABLED, enabled)
//                .where(JOB_NODE.NODE_NAME.eq(nodeName)
//                        .and(JOB_NODE.JOB_ID.in(
//                                DSL.select(JOB.ID).from(JOB)
//                                        .where(JOB.NAME.in(includeJobs)
//                                                .or(DSL.condition(includeJobs.size() == 0)))
//                        )).and(JOB_NODE.JOB_ID.notIn(
//                                DSL.select(JOB.ID).from(JOB)
//                                        .where(JOB.NAME.in(excludeJobs)
//                                                .and(DSL.condition(excludeJobs.size() > 0)))
//                        )))
//                .execute()
//        );
//    }
}
