package stroom.task.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cluster.task.api.NodeNotFoundException;
import stroom.cluster.task.api.NullClusterStateException;
import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValInteger;
import stroom.dashboard.expression.v1.ValLong;
import stroom.dashboard.expression.v1.ValNull;
import stroom.dashboard.expression.v1.ValString;
import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.DataSource;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.expression.matcher.ExpressionMatcher;
import stroom.expression.matcher.ExpressionMatcherFactory;
import stroom.searchable.api.Searchable;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.task.api.TaskContext;
import stroom.task.shared.TaskProgressResponse;
import stroom.task.shared.TaskResource;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

class SearchableTaskProgress implements Searchable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchableTaskProgress.class);
    private static final DocRef TASK_MANAGER_PSEUDO_DOC_REF = new DocRef("Searchable", "Task Manager", "Task Manager");

    private final Executor executor;
    private final Provider<TaskContext> taskContextProvider;
    private final TargetNodeSetFactory targetNodeSetFactory;
    private final TaskResource taskResource;
    private final SecurityContext securityContext;
    private final ExpressionMatcherFactory expressionMatcherFactory;

    @Inject
    SearchableTaskProgress(final Executor executor,
                           final Provider<TaskContext> taskContextProvider,
                           final TargetNodeSetFactory targetNodeSetFactory,
                           final TaskResource taskResource,
                           final SecurityContext securityContext,
                           final ExpressionMatcherFactory expressionMatcherFactory) {
        this.executor = executor;
        this.taskContextProvider = taskContextProvider;
        this.targetNodeSetFactory = targetNodeSetFactory;
        this.taskResource = taskResource;
        this.securityContext = securityContext;
        this.expressionMatcherFactory = expressionMatcherFactory;
    }

    @Override
    public DocRef getDocRef() {
        if (securityContext.hasAppPermission(PermissionNames.MANAGE_TASKS_PERMISSION)) {
            return TASK_MANAGER_PSEUDO_DOC_REF;
        }
        return null;
    }

    @Override
    public DataSource getDataSource() {
        return new DataSource(TaskManagerFields.getFields());
    }

    @Override
    public void search(final ExpressionCriteria criteria, final AbstractField[] fields, final Consumer<Val[]> consumer) {
        securityContext.secure(PermissionNames.MANAGE_TASKS_PERMISSION, () -> {
            final Map<String, TaskProgressResponse> nodeResponses = searchAllNodes();

            final ExpressionMatcher expressionMatcher = expressionMatcherFactory.create(TaskManagerFields.getFieldMap());
            nodeResponses.values()
                    .stream()
                    .map(ResultPage::getValues)
                    .flatMap(List::stream)
                    .map(taskProgress -> {
                        final Map<String, Object> attributeMap = new HashMap<>();
                        attributeMap.put(TaskManagerFields.FIELD_NODE, taskProgress.getNodeName());
                        attributeMap.put(TaskManagerFields.FIELD_NAME, taskProgress.getTaskName());
                        attributeMap.put(TaskManagerFields.FIELD_USER, taskProgress.getUserName());
                        attributeMap.put(TaskManagerFields.FIELD_SUBMIT_TIME, taskProgress.getSubmitTimeMs());
                        attributeMap.put(TaskManagerFields.FIELD_AGE, taskProgress.getAgeMs());
                        attributeMap.put(TaskManagerFields.FIELD_INFO, taskProgress.getTaskInfo());
                        return attributeMap;
                    })
                    .filter(attributeMap -> expressionMatcher.match(attributeMap, criteria.getExpression()))
                    .forEach(attributeMap -> {
                        final Val[] arr = new Val[fields.length];
                        for (int i = 0; i < fields.length; i++) {
                            Val val = ValNull.INSTANCE;
                            Object o = attributeMap.get(fields[i].getName());
                            if (o != null) {
                                if (o instanceof String) {
                                    val = ValString.create((String) o);
                                } else if (o instanceof Long) {
                                    val = ValLong.create((long) o);
                                } else if (o instanceof Integer) {
                                    val = ValInteger.create((int) o);
                                }
                            }
                            arr[i] = val;
                        }
                        consumer.accept(arr);
                    });
        });
    }

    private Map<String, TaskProgressResponse> searchAllNodes() {
        final Map<String, TaskProgressResponse> nodeResponses = new ConcurrentHashMap<>();

        final TaskContext taskContext = taskContextProvider.get();
        taskContext.setName("Search Task Progress");

        try {
            // Get the nodes that we are going to send the entity event to.
            final Set<String> targetNodes = targetNodeSetFactory.getEnabledActiveTargetNodeSet();

            final CountDownLatch countDownLatch = new CountDownLatch(targetNodes.size());

            // Only send the event to remote nodes and not this one.
            // Send the entity event.
            targetNodes.forEach(nodeName -> {
                Runnable runnable = () -> {
                    taskContext.setName("Getting progress from node '" + nodeName + "'");
                    final TaskProgressResponse response = taskResource.list(nodeName);
                    nodeResponses.putIfAbsent(nodeName, response);
                };
                runnable = taskContext.sub(runnable);
                CompletableFuture
                        .runAsync(runnable, executor)
                        .whenComplete((r, t) -> {
                            countDownLatch.countDown();
                        });
            });

            // Wait for all requests to complete.
            countDownLatch.await();

        } catch (final NullClusterStateException | NodeNotFoundException | InterruptedException e) {
            LOGGER.warn(e.getMessage());
            LOGGER.debug(e.getMessage(), e);
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return nodeResponses;
    }
}
