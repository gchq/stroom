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

package stroom.task.impl;

import stroom.cluster.task.api.NodeNotFoundException;
import stroom.cluster.task.api.NullClusterStateException;
import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.expression.matcher.ExpressionMatcher;
import stroom.expression.matcher.ExpressionMatcherFactory;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.FieldInfoResultPageFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValDuration;
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValuesConsumer;
import stroom.searchable.api.Searchable;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.shared.TaskProgress;
import stroom.task.shared.TaskProgressResponse;
import stroom.task.shared.TaskResource;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

class SearchableTaskProgress implements Searchable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchableTaskProgress.class);
    private static final DocRef TASK_MANAGER_PSEUDO_DOC_REF = new DocRef("TaskManager", "TaskManager", "Task Manager");

    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final TargetNodeSetFactory targetNodeSetFactory;
    private final TaskResource taskResource;
    private final SecurityContext securityContext;
    private final ExpressionMatcherFactory expressionMatcherFactory;
    private final FieldInfoResultPageFactory fieldInfoResultPageFactory;

    @Inject
    SearchableTaskProgress(final Executor executor,
                           final TaskContextFactory taskContextFactory,
                           final TargetNodeSetFactory targetNodeSetFactory,
                           final TaskResource taskResource,
                           final SecurityContext securityContext,
                           final ExpressionMatcherFactory expressionMatcherFactory,
                           final FieldInfoResultPageFactory fieldInfoResultPageFactory) {
        this.executor = executor;
        this.taskContextFactory = taskContextFactory;
        this.targetNodeSetFactory = targetNodeSetFactory;
        this.taskResource = taskResource;
        this.securityContext = securityContext;
        this.expressionMatcherFactory = expressionMatcherFactory;
        this.fieldInfoResultPageFactory = fieldInfoResultPageFactory;
    }

    @Override
    public String getDataSourceType() {
        return TASK_MANAGER_PSEUDO_DOC_REF.getType();
    }

    @Override
    public List<DocRef> getDataSourceDocRefs() {
        if (securityContext.hasAppPermission(AppPermission.MANAGE_TASKS_PERMISSION)) {
            return Collections.singletonList(TASK_MANAGER_PSEUDO_DOC_REF);
        }
        return Collections.emptyList();
    }

    @Override
    public Optional<QueryField> getTimeField(final DocRef docRef) {
        return Optional.of(TaskManagerFields.SUBMIT_TIME);
    }

    @Override
    public ResultPage<QueryField> getFieldInfo(final FindFieldCriteria criteria) {
        if (!TASK_MANAGER_PSEUDO_DOC_REF.equals(criteria.getDataSourceRef())) {
            return ResultPage.empty();
        }
        return fieldInfoResultPageFactory.create(criteria, getFields());
    }

    private List<QueryField> getFields() {
        return TaskManagerFields.getFields();
    }

    @Override
    public int getFieldCount(final DocRef docRef) {
        return NullSafe.size(getFields());
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final DateTimeSettings dateTimeSettings,
                       final ValuesConsumer consumer) {
        securityContext.secure(AppPermission.MANAGE_TASKS_PERMISSION, () -> {
            final Map<String, TaskProgressResponse> nodeResponses = searchAllNodes();

            final ExpressionMatcher expressionMatcher = expressionMatcherFactory.create(
                    TaskManagerFields.getFieldMap());

            nodeResponses.values()
                    .stream()
                    .map(ResultPage::getValues)
                    .flatMap(List::stream)
                    .map(taskProgress -> {
                        final Map<String, Object> attributeMap = new HashMap<>();
                        attributeMap.put(TaskManagerFields.FIELD_NODE, taskProgress.getNodeName());
                        attributeMap.put(TaskManagerFields.FIELD_NAME, taskProgress.getTaskName());
                        attributeMap.put(TaskManagerFields.FIELD_USER, NullSafe
                                .get(taskProgress, TaskProgress::getUserRef, UserRef::getDisplayName));
                        attributeMap.put(TaskManagerFields.FIELD_SUBMIT_TIME, taskProgress.getSubmitTimeMs());
                        attributeMap.put(TaskManagerFields.FIELD_AGE, taskProgress.getAgeMs());
                        attributeMap.put(TaskManagerFields.FIELD_INFO, taskProgress.getTaskInfo());
                        return attributeMap;
                    })
                    .filter(attributeMap -> expressionMatcher.match(attributeMap, criteria.getExpression()))
                    .forEach(attributeMap -> {
                        final String[] fields = fieldIndex.getFields();
                        final Val[] arr = new Val[fields.length];
                        for (int i = 0; i < fields.length; i++) {
                            final String fieldName = fields[i];
                            Val val = ValNull.INSTANCE;
                            if (fieldName != null) {
                                final Object o = attributeMap.get(fieldName);
                                if (o != null) {
                                    if (o instanceof String) {
                                        val = ValString.create((String) o);
                                    } else if (o instanceof Long) {
                                        final long aLong = (long) o;
                                        if (TaskManagerFields.FIELD_SUBMIT_TIME.equals(fieldName)) {
                                            val = ValDate.create(aLong);
                                        } else if (TaskManagerFields.FIELD_AGE.equals(fieldName)) {
                                            val = ValDuration.create(aLong);
                                        } else {
                                            val = ValLong.create(aLong);
                                        }
                                    } else if (o instanceof Integer) {
                                        val = ValInteger.create((int) o);
                                    }
                                }
                            }
                            arr[i] = val;
                        }
                        consumer.accept(Val.of(arr));
                    });
        });
    }

    private Map<String, TaskProgressResponse> searchAllNodes() {
        final Function<TaskContext, Map<String, TaskProgressResponse>> function = taskContext -> {
            final Map<String, TaskProgressResponse> nodeResponses = new ConcurrentHashMap<>();

            try {
                // Get the nodes that we are going to send the entity event to.
                final Set<String> targetNodes = targetNodeSetFactory.getEnabledActiveTargetNodeSet();

                final CountDownLatch countDownLatch = new CountDownLatch(targetNodes.size());

                // Only send the event to remote nodes and not this one.
                // Send the entity event.
                targetNodes.forEach(nodeName -> {
                    final Supplier<TaskProgressResponse> supplier = taskContextFactory.childContextResult(taskContext,
                            "Getting progress from node '" + nodeName + "'",
                            tc ->
                                    taskResource.list(nodeName));
                    CompletableFuture
                            .supplyAsync(supplier, executor)
                            .whenComplete((r, t) -> {
                                if (r != null) {
                                    nodeResponses.putIfAbsent(nodeName, r);
                                }
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
        };
        return taskContextFactory.contextResult("Search Task Progress", function).get();
    }
}
