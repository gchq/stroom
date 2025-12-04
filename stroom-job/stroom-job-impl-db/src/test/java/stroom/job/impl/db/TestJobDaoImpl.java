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

import stroom.job.impl.FindJobCriteria;
import stroom.job.shared.Job;
import stroom.security.mock.MockSecurityContextModule;
import stroom.test.common.util.db.DbTestModule;
import stroom.util.AuditUtil;
import stroom.util.exception.DataChangedException;

import com.google.inject.Guice;
import com.google.inject.Inject;
import org.apache.commons.lang3.RandomStringUtils;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestJobDaoImpl {

    @Inject
    private JobDaoImpl dao;

    @BeforeEach
    void beforeEach() {
        Guice.createInjector(
                new JobDbModule(),
                new JobDaoModule(),
                new MockSecurityContextModule(),
                new DbTestModule())
                .injectMembers(this);
        cleanup();
    }

    @Test
    void basicCreation() {
        // Given/when
        final Job job = createStandardJob();

        // Then
        assertThat(job.getId()).isNotNull();
        assertThat(job.getVersion()).isNotNull();
        assertThat(job.getName()).isEqualTo("Some name");
        assertThat(job.isEnabled()).isTrue();

        final Job loadedJob = dao.fetch(job.getId()).get();
        assertThat(loadedJob.getId()).isEqualTo(job.getId());
        assertThat(loadedJob.getVersion()).isNotNull();
        assertThat(loadedJob.getName()).isEqualTo("Some name");
        assertThat(loadedJob.isEnabled()).isTrue();
    }

    @Test
    void descriptionTooLong() {
        // Given
        final Job job = new Job();
        job.setEnabled(true);
        job.setName(RandomStringUtils.randomAlphabetic(256));

        // When/then
        assertThrows(DataAccessException.class, () -> dao.create(job));
    }

    @Test
    void badFetch() {
        // Given/when
        final Optional<Job> job = dao.fetch(11111);
        // Then
        assertThat(job.isPresent()).isFalse();
    }

    @Test
    void update() {
        // Given
        final Job job = createStandardJob();
        final int version = job.getVersion();
        job.setName("Different name");
        job.setEnabled(false);

        // When
        final Job updatedJob = dao.update(job);

        // Then
        assertThat(updatedJob.getId()).isEqualTo(job.getId());
        assertThat(updatedJob.getVersion()).isEqualTo(version + 1);
        assertThat(updatedJob.getName()).isEqualTo("Different name");
        assertThat(updatedJob.isEnabled()).isFalse();

        // Then
        final Job fetchedUpdatedJob = dao.fetch(updatedJob.getId()).get();
        assertThat(fetchedUpdatedJob.getId()).isEqualTo(job.getId());
        assertThat(fetchedUpdatedJob.getVersion()).isEqualTo(version + 1);
        assertThat(fetchedUpdatedJob.getName()).isEqualTo("Different name");
        assertThat(fetchedUpdatedJob.isEnabled()).isFalse();
    }

    @Test
    void delete() {
        // Given
        final Job job = createStandardJob();

        // When
        final boolean didDeleteSucceed = dao.delete(job.getId());

        // Then
        assertThat(didDeleteSucceed).isTrue();
        final Optional<Job> optionalJob = dao.fetch(job.getId());
        assertThat(optionalJob.isPresent()).isFalse();
    }

    @Test
    void badDelete() {
        // Given/when
        final boolean didDeleteSucceed = dao.delete(111111);
        // Then
        assertThat(didDeleteSucceed).isFalse();
    }

    @Test
    void checkOcc() {
        // Given
        final Job job = createStandardJob();
        final Job copy1 = dao.fetch(job.getId()).get();
        final Job copy2 = dao.fetch(job.getId()).get();

        copy1.setName("change 1");
        dao.update(copy1);

        copy2.setName("change 2");

        // When/then
        assertThrows(DataChangedException.class, () -> dao.update(copy2));
    }

    private Job createStandardJob() {
        final Job job = new Job();
        AuditUtil.stamp(() -> "test", job);
        job.setEnabled(true);
        job.setName("Some name");
        final Job createdJob = dao.create(job);
        return createdJob;
    }

    private void cleanup() {
        // Cleanup
        final List<Job> jobs = dao.find(new FindJobCriteria()).getValues();
        jobs.forEach(job -> dao.delete(job.getId()));
    }
}
