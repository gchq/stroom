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

import stroom.db.util.GenericDao;
import stroom.job.impl.db.jooq.tables.records.JobRecord;
import stroom.util.shared.HasIntCrud;

import javax.inject.Inject;
import java.util.Optional;

import static stroom.job.impl.db.jooq.Tables.JOB;

/**
 * This class is very slim because it uses the GenericDao.
 * Why event use this class? Why not use the GenericDao directly in the service class?
 * Some reasons:
 * 1. Hides knowledge of Jooq classes from the service
 * 2. Hides connection provider and GenericDao instantiation -- the service class just gets a working thing injected.
 * 3. It allows the DAO to be easily extended.
 *
 * //TODO gh-1072 Maybe the interface could implement the standard methods below? Then this would be even slimmer.
 */
public class JobDao implements HasIntCrud<Job> {
    private GenericDao<JobRecord, Job, Integer> dao;

    @Inject
    JobDao(final ConnectionProvider connectionProvider) {
        dao = new GenericDao<>(JOB, JOB.ID, Job.class, connectionProvider);
    }

    @Override
    public Job create(final Job job) {
        return dao.create(job);
    }

    @Override
    public Job update(final Job job) {
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

}
