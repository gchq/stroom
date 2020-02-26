package stroom.task.impl;

import stroom.cluster.task.api.ClusterCallEntry;
import stroom.cluster.task.api.ClusterDispatchAsyncHelper;
import stroom.cluster.task.api.DefaultClusterResultCollector;
import stroom.cluster.task.api.TargetType;
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
import stroom.task.shared.FindTaskProgressCriteria;
import stroom.task.shared.TaskProgress;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

class SearchableTaskProgress implements Searchable {
    private static final DocRef TASK_MANAGER_PSEUDO_DOC_REF = new DocRef("Searchable", "Task Manager", "Task Manager");

    private final ClusterDispatchAsyncHelper dispatchHelper;
    private final SecurityContext securityContext;
    private final ExpressionMatcherFactory expressionMatcherFactory;

    @Inject
    SearchableTaskProgress(final ClusterDispatchAsyncHelper dispatchHelper,
                           final SecurityContext securityContext,
                           final ExpressionMatcherFactory expressionMatcherFactory) {
        this.dispatchHelper = dispatchHelper;
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
            final FindTaskProgressClusterTask clusterTask = new FindTaskProgressClusterTask("Search Task Progress", new FindTaskProgressCriteria());
            final DefaultClusterResultCollector<ResultPage<TaskProgress>> collector = dispatchHelper
                    .execAsync(clusterTask, TargetType.ACTIVE);

            final ExpressionMatcher expressionMatcher = expressionMatcherFactory.create(TaskManagerFields.getFieldMap());
            collector.getResponseMap().values()
                    .stream()
                    .filter(value -> value.getResult() != null)
                    .map(ClusterCallEntry::getResult)
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
                    .filter(attributeMap -> {
                        return expressionMatcher.match(attributeMap, criteria.getExpression());
                    })
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
}
