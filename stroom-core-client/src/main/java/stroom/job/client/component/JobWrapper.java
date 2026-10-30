/*
 * Copyright 2019 Crown Copyright
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

package stroom.job.client.component;

import stroom.job.shared.Job;

import java.util.Objects;

/// A row of the jobs list ([JobList]): a job, the same row as long as its id is the same.
public class JobWrapper {

    private Job job;

    /// @param job The job.
    public JobWrapper(final Job job) {
        this.job = job;
    }

    /// @return The job.
    public Job getJob() {
        return job;
    }

    /// @param job The job, as changed.
    public void setJob(final Job job) {
        this.job = job;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final JobWrapper that = (JobWrapper) o;
        return Objects.equals(job.getId(), that.job.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(job.getId());
    }
}
