package stroom.planb.impl;

import stroom.docref.DocRef;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;

import java.util.List;

public class StateMaintenanceExecutor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StateMaintenanceExecutor.class);

    public static final String TASK_NAME = "State Store Maintenance";

    private final PlanBDocStoreImpl stateDocStore;
    private final SecurityContext securityContext;
    private final TaskContextFactory taskContextFactory;

    @Inject
    public StateMaintenanceExecutor(final PlanBDocStoreImpl stateDocStore,
                                    final SecurityContext securityContext,
                                    final TaskContextFactory taskContextFactory) {
        this.stateDocStore = stateDocStore;
        this.securityContext = securityContext;
        this.taskContextFactory = taskContextFactory;
    }

    public void exec() {
        securityContext.asProcessingUser(() -> {
            taskContextFactory.context("State Condenser", taskContext -> {
                taskContext.info(() -> "Getting state stores");
                final List<DocRef> list = stateDocStore.list();
                for (final DocRef docRef : list) {
//                    try {
//                        final PlanBDoc doc = stateDocStore.readDocument(docRef);
//                        final Provider<CqlSession> cqlSessionProvider =
//                                cqlSessionFactory.getSessionProvider(doc.getScyllaDbRef());
//                        final String tableName = doc.getName();
//                        if (doc.isCondense()) {
//                            taskContext.info(() -> "Condensing " + tableName);
//                            final SimpleDuration duration = SimpleDuration
//                                    .builder()
//                                    .time(doc.getCondenseAge())
//                                    .timeUnit(doc.getCondenseTimeUnit())
//                                    .build();
//                            final Instant oldest = SimpleDurationUtil.minus(Instant.now(), duration);
//                            switch (doc.getStateType()) {
//                                case TEMPORAL_STATE -> new TemporalStateDao(cqlSessionProvider, tableName)
//                                        .condense(oldest);
//                                case TEMPORAL_RANGED_STATE -> new TemporalRangedStateDao(cqlSessionProvider,
//                                tableName)
//                                        .condense(oldest);
//                                case SESSION -> new SessionDao(cqlSessionProvider, tableName)
//                                        .condense(oldest);
//                            }
//                        }
//
//                        if (!doc.isRetainForever()) {
//                            taskContext.info(() -> "State Retention " + tableName);
//                            final SimpleDuration duration = SimpleDuration
//                                    .builder()
//                                    .time(doc.getRetainAge())
//                                    .timeUnit(doc.getRetainTimeUnit())
//                                    .build();
//                            final Instant oldest = SimpleDurationUtil.minus(Instant.now(), duration);
//                            final AbstractStateDao<?> stateDao =
//                                    DaoFactory.create(cqlSessionProvider, doc.getStateType(), tableName);
//                            stateDao.removeOldData(oldest);
//                        }
//                    } catch (final Exception e) {
//                        LOGGER.error(e::getMessage, e);
//                    }
                }
            });
        });
    }
}
