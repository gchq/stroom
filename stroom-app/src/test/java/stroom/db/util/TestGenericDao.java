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

package stroom.db.util;

import stroom.job.impl.db.JobDbConnProvider;
import stroom.job.impl.db.jooq.tables.records.JobRecord;
import stroom.job.shared.Job;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.AuditUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.job.impl.db.jooq.Tables.JOB;

public class TestGenericDao extends AbstractCoreIntegrationTest {

    public static final String JOB_NAME = "Test job";

    @Inject
    private JobDbConnProvider jobDbConnProvider;

    @BeforeEach
    void beforeEach() {
        JooqUtil.context(jobDbConnProvider, context -> {
            context.deleteFrom(JOB)
                    .where(JOB.NAME.eq(JOB_NAME));
        });
    }

    private boolean isJobPresent() {
        return JooqUtil.contextResult(jobDbConnProvider, context ->
                context.selectCount()
                        .from(JOB)
                        .where(JOB.NAME.eq(JOB_NAME))
                        .fetchOne(0, int.class) > 0);
    }

    @Test
    void testCreateAndFetch() {

        final var genericDao = getGenericDao();

        assertThat(isJobPresent())
                .isFalse();

        final Job job = new Job();
        job.setName(JOB_NAME);
        job.setEnabled(true);
        AuditUtil.stamp(() -> "TestUser", job);

        assertThat(job.getId())
                .isNull();
        assertThat(job.getVersion())
                .isNull();

        final Job persistedJob = genericDao.create(job);
        final int id = persistedJob.getId();

        assertThat(persistedJob.getName())
                .isEqualTo(JOB_NAME);
        assertThat(persistedJob.getId())
                .isNotNull();
        assertThat(persistedJob.getVersion())
                .isNotNull();

        final Optional<Job> optJob = getGenericDao().fetch(id);

        assertThat(optJob)
                .isPresent();

        assertThat(optJob.get())
                .isEqualTo(persistedJob);
    }

    @Test
    void testTryCreate() {

        final var genericDao = getGenericDao();

        assertThat(isJobPresent())
                .isFalse();

        final Job job = new Job();
        job.setName(JOB_NAME);
        job.setEnabled(true);
        AuditUtil.stamp(() -> "TestUser", job);

        assertThat(job.getId())
                .isNull();
        assertThat(job.getVersion())
                .isNull();

        final Job persistedJob = genericDao.tryCreate(job, JOB.NAME);

        assertThat(persistedJob.getId())
                .isNotNull();
        assertThat(persistedJob.getVersion())
                .isNotNull();

        final Job job2 = new Job();
        job2.setName(JOB_NAME);
        job2.setEnabled(true);
        AuditUtil.stamp(() -> "TestUser", job2);

        final Job persistedJob2 = genericDao.tryCreate(job2, JOB.NAME);

        // The job exists so it should just re-fetch the db record.
        assertThat(persistedJob2)
                .isEqualTo(persistedJob);
    }

    @Test
    void testTryCreate_withOnCreateAction() {

        final var genericDao = getGenericDao();

        assertThat(isJobPresent())
                .isFalse();

        final Job job = new Job();
        job.setName(JOB_NAME);
        job.setEnabled(true);
        AuditUtil.stamp(() -> "TestUser", job);

        assertThat(job.getId())
                .isNull();
        assertThat(job.getVersion())
                .isNull();

        final AtomicBoolean didCreateHappen = new AtomicBoolean(false);

        final Job persistedJob = genericDao.tryCreate(job, JOB.NAME, rec -> didCreateHappen.set(true));

        assertThat(persistedJob.getId())
                .isNotNull();
        assertThat(persistedJob.getVersion())
                .isNotNull();
        assertThat(didCreateHappen)
                .isTrue();

        final Job job2 = new Job();
        job2.setName(JOB_NAME);
        job2.setEnabled(true);
        AuditUtil.stamp(() -> "TestUser", job2);

        didCreateHappen.set(false);

        final Job persistedJob2 = genericDao.tryCreate(job2, JOB.NAME, rec -> didCreateHappen.set(true));

        // The job exists so it should just re-fetch the db record.
        assertThat(persistedJob2)
                .isEqualTo(persistedJob);
        assertThat(didCreateHappen)
                .isFalse();
    }

    @Test
    void testUpdate() {
        final var genericDao = getGenericDao();

        assertThat(isJobPresent())
                .isFalse();

        final Job job = new Job();
        job.setName(JOB_NAME);
        job.setEnabled(true);
        AuditUtil.stamp(() -> "TestUser", job);

        assertThat(job.getId())
                .isNull();
        assertThat(job.getVersion())
                .isNull();

        final Job persistedJob = genericDao.create(job);
        final int id = persistedJob.getId();
        final int originalVersion = persistedJob.getVersion();

        assertThat(genericDao.fetch(id))
                .isPresent();

        persistedJob.setEnabled(false);

        final Job persistedJob2 = genericDao.update(persistedJob);

        assertThat(persistedJob2.getId())
                .isEqualTo(persistedJob.getId());
        assertThat(persistedJob2.isEnabled())
                .isFalse();
        assertThat(persistedJob2.getVersion())
                .isNotEqualTo(originalVersion);
    }

    @Test
    void testDelete() {
        final var genericDao = getGenericDao();

        assertThat(isJobPresent())
                .isFalse();

        final Job job = new Job();
        job.setName(JOB_NAME);
        job.setEnabled(true);
        AuditUtil.stamp(() -> "TestUser", job);

        assertThat(job.getId())
                .isNull();
        assertThat(job.getVersion())
                .isNull();

        final Job persistedJob = genericDao.create(job);
        final int id = persistedJob.getId();

        assertThat(genericDao.fetch(id))
                .isPresent();

        genericDao.delete(id);

        assertThat(genericDao.fetch(id))
                .isEmpty();
    }

    private GenericDao<JobRecord, Job, Integer> getGenericDao() {
        // Use the job table as it is fairly simple and has a mappable Pojo
        return new GenericDao<>(jobDbConnProvider, JOB, JOB.ID, Job.class);
    }
}
