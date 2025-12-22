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
import stroom.job.impl.FindJobCriteria;
import stroom.job.impl.JobDao;
import stroom.job.impl.db.jooq.tables.records.JobRecord;
import stroom.job.shared.Job;
import stroom.util.shared.HasIntCrud;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.impl.DSL;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.jooq.impl.DSL.select;
import static stroom.job.impl.db.jooq.Tables.JOB;
import static stroom.job.impl.db.jooq.Tables.JOB_NODE;

/**
 * This class is very slim because it uses the GenericDao.
 * Why event use this class? Why not use the GenericDao directly in the service class?
 * Some reasons:
 * 1. Hides knowledge of Jooq classes from the service
 * 2. Hides connection provider and GenericDao instantiation -- the service class just gets a working thing injected.
 * 3. It allows the DAO to be easily extended.
 * <p>
 * //TODO gh-1072 Maybe the interface could implement the standard methods below? Then this would be even slimmer.
 */
public class JobDaoImpl implements JobDao, HasIntCrud<Job> {
//    private static final Logger LOGGER = LoggerFactory.getLogger(JobDao.class);

    private static final Map<String, Field<?>> FIELD_MAP = Map.of(
            FindJobCriteria.FIELD_ID, JOB.ID,
            FindJobCriteria.FIELD_NAME, JOB.NAME);

    private final GenericDao<JobRecord, Job, Integer> genericDao;
    private final JobDbConnProvider jobDbConnProvider;

    @Inject
    JobDaoImpl(final JobDbConnProvider jobDbConnProvider) {
        genericDao = new GenericDao<>(jobDbConnProvider, JOB, JOB.ID, Job.class);
        this.jobDbConnProvider = jobDbConnProvider;
    }

    @Override
    public Job create(@NotNull final Job job) {
        return genericDao.create(job);
    }

    @Override
    public Job update(@NotNull final Job job) {
        return genericDao.update(job);
    }

    @Override
    public boolean delete(final int id) {
        return genericDao.delete(id);
    }

    @Override
    public Optional<Job> fetch(final int id) {
        return genericDao.fetch(id);
    }

    @Override
    public ResultPage<Job> find(final FindJobCriteria criteria) {
        final Collection<Condition> conditions = JooqUtil.conditions(
                JooqUtil.getStringCondition(JOB.NAME, criteria.getName()));

        final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(FIELD_MAP, criteria);
        final int offset = JooqUtil.getOffset(criteria.getPageRequest());
        final int limit = JooqUtil.getLimit(criteria.getPageRequest(), true);
        final List<Job> list = JooqUtil.contextResult(jobDbConnProvider, context -> context
                        .select()
                        .from(JOB)
                        .where(conditions)
                        .orderBy(orderFields)
                        .limit(offset, limit)
                        .fetch())
                .into(Job.class);
        return ResultPage.createCriterialBasedList(list, criteria);
    }

    @Override
    public int deleteOrphans() {
        return JooqUtil.contextResult(jobDbConnProvider, context -> context
                .deleteFrom(JOB)
                .where(JOB.ID.notIn(
                        context.select(JOB_NODE.JOB_ID)
                                .from(JOB_NODE)))
                .execute());
    }

    @Override
    public int setJobsEnabled(final String nodeName,
                              final boolean enabled,
                              final Set<String> includeJobs,
                              final Set<String> excludeJobs) {
        return JooqUtil.contextResult(jobDbConnProvider, context -> context
                .update(JOB_NODE)
                .set(JOB_NODE.ENABLED, enabled)
                .where(JOB_NODE.NODE_NAME.eq(nodeName)
                        .and(JOB_NODE.JOB_ID.in(
                                select(JOB.ID).from(JOB)
                                        .where(JOB.NAME.in(includeJobs)
                                                .or(DSL.condition(includeJobs.size() == 0)))
                        )).and(JOB_NODE.JOB_ID.notIn(
                                select(JOB.ID).from(JOB)
                                        .where(JOB.NAME.in(excludeJobs)
                                                .and(DSL.condition(excludeJobs.size() > 0)))
                        )))
                .execute()
        );
    }
}
