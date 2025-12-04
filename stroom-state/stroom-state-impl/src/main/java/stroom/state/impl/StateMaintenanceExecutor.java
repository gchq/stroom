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

package stroom.state.impl;

import stroom.docref.DocRef;
import stroom.security.api.SecurityContext;
import stroom.state.impl.dao.AbstractStateDao;
import stroom.state.impl.dao.DaoFactory;
import stroom.state.impl.dao.SessionDao;
import stroom.state.impl.dao.TemporalRangedStateDao;
import stroom.state.impl.dao.TemporalStateDao;
import stroom.state.shared.StateDoc;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.time.SimpleDurationUtil;

import com.datastax.oss.driver.api.core.CqlSession;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Instant;
import java.util.List;

public class StateMaintenanceExecutor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StateMaintenanceExecutor.class);

    public static final String TASK_NAME = "State Store Maintenance";

    private final StateDocStoreImpl stateDocStore;
    private final SecurityContext securityContext;
    private final TaskContextFactory taskContextFactory;
    private final CqlSessionFactory cqlSessionFactory;

    @Inject
    public StateMaintenanceExecutor(final StateDocStoreImpl stateDocStore,
                                    final SecurityContext securityContext,
                                    final TaskContextFactory taskContextFactory,
                                    final CqlSessionFactory cqlSessionFactory) {
        this.stateDocStore = stateDocStore;
        this.securityContext = securityContext;
        this.taskContextFactory = taskContextFactory;
        this.cqlSessionFactory = cqlSessionFactory;
    }

    public void exec() {
        securityContext.asProcessingUser(() -> {
            taskContextFactory.context("State Condenser", taskContext -> {
                taskContext.info(() -> "Getting state stores");
                final List<DocRef> list = stateDocStore.list();
                for (final DocRef docRef : list) {
                    try {
                        final StateDoc doc = stateDocStore.readDocument(docRef);
                        final Provider<CqlSession> cqlSessionProvider =
                                cqlSessionFactory.getSessionProvider(doc.getScyllaDbRef());
                        final String tableName = doc.getName();
                        if (doc.isCondense()) {
                            taskContext.info(() -> "Condensing " + tableName);
                            final SimpleDuration duration = SimpleDuration
                                    .builder()
                                    .time(doc.getCondenseAge())
                                    .timeUnit(doc.getCondenseTimeUnit())
                                    .build();
                            final Instant oldest = SimpleDurationUtil.minus(Instant.now(), duration);
                            switch (doc.getStateType()) {
                                case TEMPORAL_STATE -> new TemporalStateDao(cqlSessionProvider, tableName)
                                        .condense(oldest);
                                case TEMPORAL_RANGED_STATE -> new TemporalRangedStateDao(cqlSessionProvider, tableName)
                                        .condense(oldest);
                                case SESSION -> new SessionDao(cqlSessionProvider, tableName)
                                        .condense(oldest);
                            }
                        }

                        if (!doc.isRetainForever()) {
                            taskContext.info(() -> "State Retention " + tableName);
                            final SimpleDuration duration = SimpleDuration
                                    .builder()
                                    .time(doc.getRetainAge())
                                    .timeUnit(doc.getRetainTimeUnit())
                                    .build();
                            final Instant oldest = SimpleDurationUtil.minus(Instant.now(), duration);
                            final AbstractStateDao<?> stateDao =
                                    DaoFactory.create(cqlSessionProvider, doc.getStateType(), tableName);
                            stateDao.removeOldData(oldest);
                        }
                    } catch (final Exception e) {
                        LOGGER.error(e::getMessage, e);
                    }
                }
            });
        });
    }
}
