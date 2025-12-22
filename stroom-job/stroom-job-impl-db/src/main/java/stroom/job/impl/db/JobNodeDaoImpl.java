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

package stroom.job.impl.db;

import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.job.impl.JobNodeDao;
import stroom.job.impl.db.jooq.tables.records.JobNodeRecord;
import stroom.job.shared.BatchScheduleRequest;
import stroom.job.shared.FindJobNodeCriteria;
import stroom.job.shared.Job;
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNode.JobType;
import stroom.job.shared.JobNodeListResponse;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.HasIntCrud;
import stroom.util.shared.scheduler.Schedule;

import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import static stroom.job.impl.db.jooq.tables.Job.JOB;
import static stroom.job.impl.db.jooq.tables.JobNode.JOB_NODE;

public class JobNodeDaoImpl implements JobNodeDao, HasIntCrud<JobNode> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JobNodeDaoImpl.class);

    private static final Map<String, Field<?>> FIELD_MAP = Map.of(
            FindJobNodeCriteria.FIELD_ID_ID, JOB_NODE.ID,
            FindJobNodeCriteria.FIELD_JOB_NAME, JOB.NAME,
            FindJobNodeCriteria.FIELD_ID_NODE, JOB_NODE.NODE_NAME,
            FindJobNodeCriteria.FIELD_ID_ENABLED, JOB_NODE.ENABLED,
            FindJobNodeCriteria.FIELD_ID_LAST_EXECUTED, JOB_NODE.UPDATE_TIME_MS);

    private static final Function<Record, Job> RECORD_TO_JOB_MAPPER = record -> {
        final Job job = new Job();
        job.setId(record.get(JOB.ID));
        job.setVersion(record.get(JOB.VERSION));
        job.setCreateTimeMs(record.get(JOB.CREATE_TIME_MS));
        job.setCreateUser(record.get(JOB.CREATE_USER));
        job.setUpdateTimeMs(record.get(JOB.UPDATE_TIME_MS));
        job.setUpdateUser(record.get(JOB.UPDATE_USER));
        job.setName(record.get(JOB.NAME));
        job.setEnabled(record.get(JOB.ENABLED));
        return job;
    };

    private static final Function<Record, JobNode> RECORD_TO_JOB_NODE_MAPPER = record -> {
        final JobNode jobNode = new JobNode();
        jobNode.setId(record.get(JOB_NODE.ID));
        jobNode.setVersion(record.get(JOB_NODE.VERSION));
        jobNode.setCreateTimeMs(record.get(JOB_NODE.CREATE_TIME_MS));
        jobNode.setCreateUser(record.get(JOB_NODE.CREATE_USER));
        jobNode.setUpdateTimeMs(record.get(JOB_NODE.UPDATE_TIME_MS));
        jobNode.setUpdateUser(record.get(JOB_NODE.UPDATE_USER));
        jobNode.setJobType(JobType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(record.get(JOB_NODE.JOB_TYPE)));
        jobNode.setNodeName(record.get(JOB_NODE.NODE_NAME));
        jobNode.setTaskLimit(record.get(JOB_NODE.TASK_LIMIT));
        jobNode.setSchedule(record.get(JOB_NODE.SCHEDULE));
        jobNode.setEnabled(record.get(JOB_NODE.ENABLED));
        return jobNode;
    };

    private static final BiFunction<JobNode, JobNodeRecord, JobNodeRecord> JOB_NODE_TO_RECORD_MAPPER =
            (jobNode, record) -> {
                record.from(jobNode);
                record.set(JOB_NODE.JOB_ID, jobNode.getJob().getId());
                record.set(JOB_NODE.JOB_TYPE,
                        jobNode.getJobType() != null
                                ? jobNode.getJobType().getPrimitiveValue()
                                : JobType.UNKNOWN.getPrimitiveValue());
                return record;
            };

    private final JobDbConnProvider jobDbConnProvider;
    private final GenericDao<JobNodeRecord, JobNode, Integer> genericDao;
    private final SecurityContext securityContext;

    @Inject
    JobNodeDaoImpl(final JobDbConnProvider jobDbConnProvider,
                   final SecurityContext securityContext) {

        this.jobDbConnProvider = jobDbConnProvider;
        this.genericDao = new GenericDao<>(
                jobDbConnProvider,
                JOB_NODE,
                JOB_NODE.ID,
                JOB_NODE_TO_RECORD_MAPPER,
                RECORD_TO_JOB_NODE_MAPPER);
        this.securityContext = securityContext;
    }

    @Override
    public JobNode create(@NotNull final JobNode jobNode) {
        final JobNode result = genericDao.create(jobNode);
        result.setJob(jobNode.getJob());
        return result;
    }

    @Override
    public JobNode update(@NotNull final JobNode jobNode) {
        Objects.requireNonNull(jobNode, "Null JobNode");
        Objects.requireNonNull(jobNode.getJob(), "Null JobNode Job");

        final JobNode result = genericDao.update(jobNode);
        result.setJob(jobNode.getJob());
        return result;
    }

    @Override
    public boolean delete(final int id) {
        return genericDao.delete(id);
    }

    @Override
    public Optional<JobNode> fetch(final int id) {
        return JooqUtil.contextResult(jobDbConnProvider, context -> context
                        .select()
                        .from(JOB_NODE)
                        .join(JOB).on(JOB_NODE.JOB_ID.eq(JOB.ID))
                        .where(JOB_NODE.ID.eq(id))
                        .fetchOptional())
                .map(record -> {
                    final Job job = RECORD_TO_JOB_MAPPER.apply(record);
                    final JobNode jobNode = RECORD_TO_JOB_NODE_MAPPER.apply(record);
                    jobNode.setJob(job);
                    return jobNode;
                });
    }

    public JobNodeListResponse find(final FindJobNodeCriteria criteria) {
        final Collection<Condition> conditions = JooqUtil.conditions(
                JooqUtil.getStringCondition(JOB.NAME, criteria.getJobName()),
                JooqUtil.getStringCondition(JOB_NODE.NODE_NAME, criteria.getNodeName()),
                JooqUtil.getBooleanCondition(JOB_NODE.ENABLED, criteria.getJobNodeEnabled()));

        final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(FIELD_MAP, criteria, JOB_NODE.NODE_NAME);

        final int offset = JooqUtil.getOffset(criteria.getPageRequest());
        final int limit = JooqUtil.getLimit(criteria.getPageRequest(), true);
        final List<JobNode> list = JooqUtil.contextResult(jobDbConnProvider, context -> context
                        .select()
                        .from(JOB_NODE)
                        .join(JOB).on(JOB_NODE.JOB_ID.eq(JOB.ID))
                        .where(conditions)
                        .orderBy(orderFields)
                        .limit(offset, limit)
                        .fetch())
                .map(record -> {
                    final Job job = RECORD_TO_JOB_MAPPER.apply(record);
                    final JobNode jobNode = RECORD_TO_JOB_NODE_MAPPER.apply(record);
                    jobNode.setJob(job);
                    return jobNode;
                });
        return JobNodeListResponse.createUnboundedJobNodeResponse(list);
    }

    @Override
    public boolean isEnabled(final String jobName, final String nodeName) {
        final int count = JooqUtil.contextResult(jobDbConnProvider, context -> context
                .selectCount()
                .from(JOB_NODE)
                .join(JOB).on(JOB_NODE.JOB_ID.eq(JOB.ID))
                .where(JOB_NODE.NODE_NAME.eq(nodeName))
                .and(JOB.NAME.eq(jobName))
                .and(JOB.ENABLED.eq(true))
                .and(JOB_NODE.ENABLED.eq(true))
                .fetchOne(0, int.class));
        final boolean isEnabled = count > 0;
        LOGGER.debug("jobName: '{}', nodeName: '{}', isEnabled: {}", jobName, nodeName, isEnabled);
        return isEnabled;
    }

    public void updateSchedule(final BatchScheduleRequest batchScheduleRequest) {
        final Schedule schedule = batchScheduleRequest.getSchedule();
        final String expression = schedule.getExpression();
        final Set<Integer> jobNodeIds = batchScheduleRequest.getJobNodeIds();

        final long updateMs = Instant.now().toEpochMilli();
        final String updateUser = securityContext.getUserIdentityForAudit();

        final Integer count = JooqUtil.contextResult(jobDbConnProvider, context -> context
                .update(JOB_NODE)
                .set(JOB_NODE.SCHEDULE, expression)
                .set(JOB_NODE.UPDATE_TIME_MS, updateMs)
                .set(JOB_NODE.UPDATE_USER, updateUser)
                .where(JOB_NODE.ID.in(jobNodeIds))
                .execute());

        LOGGER.debug("Updated {} rows with expression '{}', ids: {}", count, expression, jobNodeIds);
    }
}
