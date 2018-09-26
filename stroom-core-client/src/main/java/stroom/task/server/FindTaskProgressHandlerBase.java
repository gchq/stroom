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

package stroom.task.server;

import com.google.common.base.Functions;
import stroom.dispatch.shared.Action;
import stroom.entity.cluster.FindServiceClusterTask;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.PageRequest;
import stroom.entity.shared.ResultList;
import stroom.task.cluster.ClusterCallEntry;
import stroom.task.cluster.ClusterDispatchAsyncHelper;
import stroom.task.cluster.DefaultClusterResultCollector;
import stroom.task.cluster.TargetNodeSetFactory.TargetType;
import stroom.task.shared.FindTaskProgressCriteria;
import stroom.task.shared.TaskProgress;
import stroom.util.shared.Expander;
import stroom.util.shared.SharedObject;
import stroom.util.shared.Task;
import stroom.util.shared.TaskId;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

            final FindServiceClusterTask<FindTaskProgressCriteria, TaskProgress> clusterTask = new FindServiceClusterTask<>(
                    action.getUserToken(), action.getTaskName(), TaskManager.class, criteria);
            final DefaultClusterResultCollector<ResultList<TaskProgress>> collector = dispatchHelper
                    .execAsync(clusterTask, TargetType.ACTIVE);

            final Map<TaskId, TaskProgress> totalMap = collector.getResponseMap().values()
                    .stream()
                    .filter(value -> value.getResult() != null)
                    .map(ClusterCallEntry::getResult)
                    .map(ResultList::getValues)
                    .flatMap(List::stream)
                    .collect(Collectors.toMap(TaskProgress::getId, Functions.identity()));

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

        final List<TaskProgress> returnList = new ArrayList<>();
        buildTree(childMap, null, -1, returnList, criteria);

        return returnList;


//        final Map<TaskId, List<TaskProgress>> totalChildMap = new HashMap<>();
//
//        final List<TaskProgress> rootNodes = new ArrayList<>();
//        for (final TaskProgress taskProgress : taskProgressMap.values()) {
//            final TaskId taskId = taskProgress.getId();
//            TaskProgress child = taskProgress;
//            boolean newChild = true;
//
//            // Has it got ancestors?
//            if (taskId != null && taskId.getParentId() != null) {
//                TaskId parentId = taskId.getParentId();
//
//                // Build relationships to parents creating dummy dead
//                // parents if necessary.
//                while (parentId != null) {
//                    // See if we already know about this parent?
//                    TaskProgress parent = taskProgressMap.get(parentId);
//                    final boolean unknownParent = parent == null;
//
//                    if (unknownParent) {
//                        // If we have no record of this parent then create a
//                        // dummy dead one.
//                        parent = new TaskProgress();
//                        parent.setId(parentId);
//                        parent.setSubmitTimeMs(child.getSubmitTimeMs());
//                        parent.setTimeNowMs(child.getTimeNowMs());
//                        parent.setTaskName("<<dead>>");
//
//                        taskProgressMap.put(parentId, parent);
//
//                        // If the newly created node is a root then add it
//                        // to the root list.
//                        if (parentId.getParentId() == null) {
//                            rootNodes.add(parent);
//                        }
//                    }
//
//                    // Only add the child to the child map for this parent
//                    // if it is one that hasn't been added before.
//                    if (newChild) {
//                        // Add this task progress to the child list for this
//                        // parent.
//                        totalChildMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(child);
//                    }
//
//                    // Assign the parent to the child for the next ancestor
//                    // child list to use.
//                    child = parent;
//
//                    // If we did not previously know anything about the
//                    // parent and have had to create a dummy one then make
//                    // sure the child map for the next parent contains the
//                    // newly created dummy.
//                    newChild = unknownParent;
//
//                    parentId = parentId.getParentId();
//                }
//
//            } else {
//                rootNodes.add(taskProgress);
//            }
//        }
//
//        final List<TaskProgress> rtnList = new ArrayList<>();
//
//        sortTaskProgressList(rootNodes, criteria);
//        for (final TaskProgress taskProgress : rootNodes) {
//            buildTreeNode(rtnList, criteria, taskProgress, totalChildMap, 0);
//        }
//
//        return rtnList;
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


//    private void sortTaskProgressList(final List<TaskProgress> rtnList, FindTaskProgressCriteria criteria) {
//        criteria.validateSortField();
//        rtnList.sort(criteria);
//    }
//
//    private void buildTreeNode(final List<TaskProgress> rtnList, final FindTaskProgressCriteria criteria,
//                               final TaskProgress node, final Map<TaskId, List<TaskProgress>> totalChildMap, final int depth) {
//        rtnList.add(node);
//        final List<TaskProgress> childList = totalChildMap.get(node.getId());
//        if (childList != null) {
//            sortTaskProgressList(childList, criteria);
//            // Force expansion on tasks that are younger than a second.
//            final boolean forceExpansion = node.getAgeMs() < 1000;
//            final boolean expanded = criteria.isExpanded(node);
//
//            final boolean state = expanded || forceExpansion;
//            node.setExpander(new Expander(depth, state, false));
//            if (state) {
//                for (final TaskProgress child : childList) {
//                    buildTreeNode(rtnList, criteria, child, totalChildMap, depth + 1);
//                }
//            }
//        } else {
//            node.setExpander(new Expander(depth, false, true));
//        }
//    }
}
