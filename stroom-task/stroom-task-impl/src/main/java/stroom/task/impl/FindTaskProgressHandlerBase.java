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

package stroom.task.impl;

import com.google.common.base.Strings;
import stroom.cluster.task.api.ClusterCallEntry;
import stroom.cluster.task.api.ClusterDispatchAsyncHelper;
import stroom.cluster.task.api.DefaultClusterResultCollector;
import stroom.cluster.task.api.TargetType;
import stroom.docref.SharedObject;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.shared.Action;
import stroom.task.shared.FindTaskProgressCriteria;
import stroom.task.shared.Task;
import stroom.task.shared.TaskId;
import stroom.task.shared.TaskProgress;
import stroom.util.date.DateUtil;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.Expander;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultList;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

abstract class FindTaskProgressHandlerBase<T extends Task<R>, R extends SharedObject>
        extends AbstractTaskHandler<T, R> {
    private final ClusterDispatchAsyncHelper dispatchHelper;

    @Inject
    FindTaskProgressHandlerBase(final ClusterDispatchAsyncHelper dispatchHelper) {
        this.dispatchHelper = dispatchHelper;
    }

    BaseResultList<TaskProgress> doExec(final Action<?> action, final FindTaskProgressCriteria criteria) {
        // Validate criteria.
        criteria.validateSortField();

        final PageRequest originalPageRequest = criteria.getPageRequest();
        try {
            // Don't page limit the first query
            criteria.setPageRequest(new PageRequest());

            final FindTaskProgressClusterTask clusterTask = new FindTaskProgressClusterTask(action.getTaskName(), criteria);
            final DefaultClusterResultCollector<ResultList<TaskProgress>> collector = dispatchHelper
                    .execAsync(clusterTask, TargetType.ACTIVE);

            final Map<TaskId, TaskProgress> totalMap = collector.getResponseMap().values()
                    .stream()
                    .filter(value -> value.getResult() != null)
                    .map(ClusterCallEntry::getResult)
                    .map(ResultList::getValues)
                    .flatMap(List::stream)
                    .collect(Collectors.toMap(TaskProgress::getId, Function.identity()));

            final List<TaskProgress> resultList = createList(totalMap, criteria);

            return BaseResultList.createCriterialBasedList(resultList, criteria);

        } finally {
            criteria.setPageRequest(originalPageRequest);
        }
    }

    List<TaskProgress> createList(final Map<TaskId, TaskProgress> taskProgressMap, final FindTaskProgressCriteria criteria) {
        final Map<TaskId, TaskProgress> completeIdMap = new HashMap<>(taskProgressMap);
        final Map<TaskId, Set<TaskProgress>> childMap = new HashMap<>();

        taskProgressMap.forEach((taskId, taskProgress) -> {
            childMap.computeIfAbsent(taskProgress.getId().getParentId(), k -> new HashSet<>()).add(taskProgress);

            // Build relationships to parents creating dummy dead
            // parents if necessary.
            while (taskProgress.getId().getParentId() != null) {
                final TaskProgress child = taskProgress;
                final TaskId parentId = child.getId().getParentId();
                taskProgress = completeIdMap.computeIfAbsent(parentId, k -> {
                    // Add dummy parent nodes if we need to.

                    // If we have no record of this parent then create a
                    // dummy dead one.
                    final TaskProgress parent = new TaskProgress();
                    parent.setId(parentId);
                    parent.setSubmitTimeMs(child.getSubmitTimeMs());
                    parent.setTimeNowMs(child.getTimeNowMs());
                    parent.setTaskName("<<dead>>");
                    return parent;
                });

                childMap.computeIfAbsent(taskProgress.getId().getParentId(), k -> new HashSet<>()).add(taskProgress);
            }
        });

        // Filter the child map.
        Map<TaskId, Set<TaskProgress>> filteredMap = childMap;
        if (!Strings.isNullOrEmpty(criteria.getNameFilter())) {
            final String name = criteria.getNameFilter().toLowerCase();
            filteredMap = filter(completeIdMap, childMap, name);
        }

        final List<TaskProgress> returnList = new ArrayList<>();
        buildTree(filteredMap, null, -1, returnList, criteria);

        return returnList;
    }

    private Map<TaskId, Set<TaskProgress>> filter(final Map<TaskId, TaskProgress> completeIdMap,
                                                  final Map<TaskId, Set<TaskProgress>> childMapIn,
                                                  final String name) {
        final Map<TaskId, Set<TaskProgress>> childMapOut = new HashMap<>();

        childMapIn.forEach((taskId, set) -> set.forEach(taskProgress -> {
            if (checkName(taskProgress, name)) {
                childMapOut.computeIfAbsent(taskId, k -> new HashSet<>()).add(taskProgress);

                // Add children
                addChildren(taskProgress.getId(), childMapIn, childMapOut);

                // Add parents.
                TaskId parent = taskId;
                while (parent != null) {
                    childMapOut.computeIfAbsent(parent.getParentId(), k -> new HashSet<>()).add(completeIdMap.get(parent));
                    parent = parent.getParentId();
                }
            }
        }));

        return childMapOut;
    }

    private void addChildren(final TaskId parentId, final Map<TaskId, Set<TaskProgress>> childMapIn, final Map<TaskId, Set<TaskProgress>> childMapOut) {
        final Set<TaskProgress> children = childMapIn.get(parentId);
        if (children != null) {
            children.forEach(child -> {
                childMapOut.computeIfAbsent(parentId, k -> new HashSet<>()).add(child);
                addChildren(child.getId(), childMapIn, childMapOut);
            });
        }
    }

    private boolean checkName(final TaskProgress taskProgress, final String name) {
        if (checkName(taskProgress.getNodeName(), name)) {
            return true;
        }
        if (checkName(taskProgress.getTaskName(), name)) {
            return true;
        }
        if (checkName(DateUtil.createNormalDateTimeString(taskProgress.getSubmitTimeMs()), name)) {
            return true;
        }
        if (checkName(taskProgress.getUserName(), name)) {
            return true;
        }
        if (checkName(taskProgress.getTaskInfo(), name)) {
            return true;
        }
        return checkName(taskProgress.getThreadName(), name);
    }

    private boolean checkName(final String value, final String name) {
        return value != null && value.toLowerCase().contains(name);
    }

    private void buildTree(final Map<TaskId, Set<TaskProgress>> childMap,
                           final TaskProgress parent,
                           final int depth,
                           final List<TaskProgress> returnList,
                           FindTaskProgressCriteria criteria) {
        TaskId parentId = null;
        if (parent != null) {
            parentId = parent.getId();
            parent.setExpander(new Expander(depth, false, true));
            returnList.add(parent);
        }

        final Set<TaskProgress> childSet = childMap.get(parentId);
        if (childSet != null) {
            boolean state = true;
            if (parent != null) {
                // Force expansion on tasks that are younger than a second.
                final boolean forceExpansion = parent.getAgeMs() < 1000;
                final boolean expanded = criteria.isExpanded(parent);
                state = expanded || forceExpansion;
                parent.setExpander(new Expander(depth, state, false));
            }

            if (state) {
                childSet.stream()
                        .sorted(criteria)
                        .forEach(child -> buildTree(childMap, child, depth + 1, returnList, criteria));
            }
        }
    }
}
