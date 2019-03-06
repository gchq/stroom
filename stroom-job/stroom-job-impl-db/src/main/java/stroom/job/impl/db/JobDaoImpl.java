/*
 * Copyright 2016 Crown Copyright
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

import org.jooq.Condition;
import org.jooq.OrderField;
import org.jooq.TableField;
import stroom.db.util.AuditUtil;
import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.job.impl.JobDao;
import stroom.job.impl.db.jooq.tables.records.JobRecord;
import stroom.job.shared.FindJobCriteria;
import stroom.job.shared.Job;
import stroom.security.SecurityContext;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.HasIntCrud;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    private static final Map<String, TableField> TABLE_FIELD_MAP = Map.of(
            FindJobCriteria.FIELD_ID, JOB.ID,
            FindJobCriteria.FIELD_NAME, JOB.NAME);

    private final GenericDao<JobRecord, Job, Integer> dao;
    private final ConnectionProvider connectionProvider;
    private final SecurityContext securityContext;

    @Inject
    JobDaoImpl(final ConnectionProvider connectionProvider,
               final SecurityContext securityContext) {
        dao = new GenericDao<>(JOB, JOB.ID, Job.class, connectionProvider);
        this.connectionProvider = connectionProvider;
        this.securityContext = securityContext;
    }

    @Override
    public Job create(@Nonnull final Job job) {
        AuditUtil.stamp(securityContext.getUserId(), job);
        return dao.create(job);
    }

    @Override
    public Job update(@Nonnull final Job job) {
        AuditUtil.stamp(securityContext.getUserId(), job);
        return dao.update(job);
    }

    @Override
    public boolean delete(int id) {
        return dao.delete(id);
    }

    @Override
    public Optional<Job> fetch(int id) {
        return dao.fetch(id);
    }

    @Override
    public BaseResultList<Job> find(FindJobCriteria criteria) {
        final Collection<Condition> conditions = new ArrayList<>();
        JooqUtil.getStringCondition(JOB.NAME, criteria.getName()).ifPresent(conditions::add);

        final OrderField[] orderFields = JooqUtil.getOrderFields(TABLE_FIELD_MAP, criteria);

        final List<Job> list = JooqUtil.contextResult(connectionProvider, context -> context
                .select()
                .from(JOB)
                .where(conditions)
                .orderBy(orderFields)
                .limit(JooqUtil.getLimit(criteria.getPageRequest()))
                .offset(JooqUtil.getOffset(criteria.getPageRequest()))
                .fetch()
                .into(Job.class));

        return BaseResultList.createUnboundedList(list);
    }

    @Override
    public int deleteOrphans() {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .deleteFrom(JOB)
                .where(JOB.ID.notIn(
                        context.select(JOB_NODE.JOB_ID)
                                .from(JOB_NODE)))
                .execute());
    }


//    private GenericDao<JobRecord, Job, Integer> dao;
//
//    @Inject
//    JobDao(final ConnectionProvider connectionProvider) {
//        dao = new GenericDao<>(JOB, JOB.ID, Job.class, connectionProvider);
//    }
//
//    @Override
//    public Job create(final Job job) {
//        return dao.create(job);
//    }
//
//    @Override
//    public Job update(final Job job) {
//        return dao.update(job);
//    }
//
//    @Override
//    public boolean delete(int id) {
//        return dao.delete(id);
//    }
//
//    @Override
//    public Optional<Job> fetch(int id) {
//        return dao.fetch(id);
//    }

}
