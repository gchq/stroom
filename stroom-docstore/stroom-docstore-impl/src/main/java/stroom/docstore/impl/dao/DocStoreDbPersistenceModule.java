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

package stroom.docstore.impl.dao;

import stroom.docstore.api.DocDependencyService;
import stroom.docstore.impl.DocStoreConfig;
import stroom.docstore.impl.Persistence;
import stroom.docstore.impl.db.jooq.tables.Doc;
import stroom.job.api.ScheduledJobsBinder;
import stroom.util.RunnableWrapper;
import stroom.util.shared.scheduler.CronExpressions;

import com.google.inject.AbstractModule;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

public class DocStoreDbPersistenceModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(Persistence.class).to(DBPersistence.class);
        bind(DocDependencyDao.class);
        bind(DocDependencyService.class).to(DocDependencyServiceImpl.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(PhysicalDeleteOldDocs.class, builder -> builder
                        .name("Doc Store - Physical Delete")
                        .description("Physically deletes documents that have been soft-deleted " +
                                     "for longer than the configured retention period. " +
                                     "Removes all associated data, audit, and snapshot rows.")
                        .cronSchedule(CronExpressions.EVERY_DAY_AT_MIDNIGHT.getExpression()));
    }

    private static class PhysicalDeleteOldDocs extends RunnableWrapper {

        @Inject
        PhysicalDeleteOldDocs(final DBPersistence dbPersistence,
                              final Provider<DocStoreConfig> docStoreConfigProvider) {
            super(() -> dbPersistence.physicalDelete(
                    docStoreConfigProvider.get().getPhysicalDeleteAge().getDuration()));
        }
    }
}
