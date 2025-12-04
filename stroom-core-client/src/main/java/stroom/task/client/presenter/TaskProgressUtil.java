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

package stroom.task.client.presenter;

import stroom.task.shared.FindTaskProgressCriteria;
import stroom.task.shared.TaskId;
import stroom.task.shared.TaskProgress;
import stroom.util.shared.CompareUtil;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.Expander;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import com.google.gwt.view.client.Range;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

class TaskProgressUtil {

    public static final String DEAD_TASK_NAME = "<<dead>>";

    private TaskProgressUtil() {
    }

    static ResultPage<TaskProgress> combine(final Range range,
                                            final FindTaskProgressCriteria criteria,
                                            final Collection<List<TaskProgress>> input,
                                            final TaskManagerTreeAction treeAction) {
        // Validate criteria.
        criteria.validateSortField();

//        input
//                .stream()
//                .flatMap(List::stream)
//                .map(TaskProgress::getId)
//                .map(taskId -> NullSafe.get(taskId.getParentId(), TaskId::getId)
//                        + " - "
//                        + taskId.getId())
//                .forEach(GWT::log);

        final Map<TaskId, TaskProgress> totalMap;
        try {
            totalMap = input
                    .stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toMap(TaskProgress::getId, Function.identity()));
        } catch (final Exception e) {
//            try {
//                input
//                        .stream()
//                        .flatMap(List::stream)
//                        .collect(Collectors.groupingBy(TaskProgress::getId, Collectors.toList()))
//                        .entrySet()
//                        .stream()
//                        .filter(entry -> entry.getValue().size() > 1)
//                        .forEach(entry -> {
//                            GWT.log("Task " + entry.getKey().getId() + " has "
//                                    + entry.getValue().size() + " instances");
//                        });
//            } catch (Exception ex) {
//                GWT.log("Error trying to debug error: " + ex.getMessage());
//            }
            throw new RuntimeException(e);
        }

        final List<TaskProgress> resultList = createList(totalMap, criteria, treeAction);
        final long total = resultList.size();
        final List<TaskProgress> trimmed = new ArrayList<>();
        for (int i = range.getStart(); i < range.getStart() + range.getLength() && i < resultList.size(); i++) {
            trimmed.add(resultList.get(i));
        }
        return new ResultPage<>(trimmed, new PageResponse(range.getStart(), trimmed.size(), total, true));
    }

    static List<TaskProgress> createList(final Map<TaskId, TaskProgress> taskProgressMap,
                                         final FindTaskProgressCriteria criteria,
                                         final TaskManagerTreeAction treeAction) {
        final Map<TaskId, TaskProgress> completeIdMap = new HashMap<>(taskProgressMap);
        final Map<TaskId, Set<TaskProgress>> childMap = new HashMap<>();

        taskProgressMap.forEach((taskId, taskProgress) -> {
            childMap.computeIfAbsent(
                    taskProgress.getId().getParentId(),
                    k -> new HashSet<>()).add(taskProgress);

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
                    parent.setTaskName(DEAD_TASK_NAME);
                    return parent;
                });

                childMap.computeIfAbsent(
                        taskProgress.getId().getParentId(),
                        k -> new HashSet<>()).add(taskProgress);
            }
        });

        // Filter the child map.
        Map<TaskId, Set<TaskProgress>> filteredMap = childMap;
        if (criteria.getNameFilter() != null && !criteria.getNameFilter().isEmpty()) {
            filteredMap = filter(completeIdMap, childMap);
        }

        final List<TaskProgress> returnList = new ArrayList<>();
        buildTree(filteredMap, null, -1, returnList, criteria, treeAction);

        return returnList;
    }

    private static Map<TaskId, Set<TaskProgress>> filter(final Map<TaskId, TaskProgress> completeIdMap,
                                                         final Map<TaskId, Set<TaskProgress>> childMapIn) {
        final Map<TaskId, Set<TaskProgress>> childMapOut = new HashMap<>();

        // For any task that matches the filter, we also want to see its ancestors/descendants
        childMapIn.forEach((taskId, set) ->
                set.forEach(taskProgress -> {
                    if (taskProgress.isMatchedInFilter()) {
                        childMapOut.computeIfAbsent(
                                        taskId,
                                        k -> new HashSet<>())
                                .add(taskProgress);

                        // Add children
                        addChildren(taskProgress.getId(), childMapIn, childMapOut);

                        // Add parents.
                        TaskId parent = taskId;
                        while (parent != null) {
                            childMapOut.computeIfAbsent(
                                            parent.getParentId(),
                                            k -> new HashSet<>())
                                    .add(completeIdMap.get(parent));
                            parent = parent.getParentId();
                        }
                    }
                }));

        return childMapOut;
    }

    private static void addChildren(final TaskId parentId,
                                    final Map<TaskId, Set<TaskProgress>> childMapIn,
                                    final Map<TaskId, Set<TaskProgress>> childMapOut) {
        final Set<TaskProgress> children = childMapIn.get(parentId);
        if (children != null) {
            children.forEach(child -> {
                childMapOut.computeIfAbsent(parentId, k -> new HashSet<>()).add(child);
                addChildren(child.getId(), childMapIn, childMapOut);
            });
        }
    }

    private static void buildTree(final Map<TaskId, Set<TaskProgress>> childMap,
                                  final TaskProgress parent,
                                  final int depth,
                                  final List<TaskProgress> returnList,
                                  final FindTaskProgressCriteria criteria,
                                  final TaskManagerTreeAction treeAction) {
        TaskId parentId = null;
        if (parent != null) {
            parentId = parent.getId();
            returnList.add(parent);
        }

        final Set<TaskProgress> childSet = childMap.get(parentId);
        if (childSet != null && !childSet.isEmpty()) {
            final boolean state;
            if (parent != null) {
                if (treeAction.isExpandAllRequested()) {
                    state = true;
                } else if (treeAction.hasExpandedState(parent)) {
                    state = treeAction.isRowExpanded(parent);
//                    GWT.log(ClientDateUtil.toISOString(System.currentTimeMillis()) +
//                    ": " + parentId.getId() + " " + parent.getTaskName() + " hasExpandedState:" + state);
                } else {
                    // Expansion state has not been set so decide the initial state here
                    state = getDefaultExpansionState(criteria, parent, childMap);
                    treeAction.setRowExpanded(parent, state);
                }
//                GWT.log(ClientDateUtil.toISOString(System.currentTimeMillis()) +
//                ": " + parentId.getId() + " " + parent.getTaskName() + " " + childSet.size() + " " + state);
                parent.setExpander(new Expander(depth, state, false));
            } else {
                state = true;
            }

            if (state) {
                childSet.stream()
                        .sorted(new TaskProgressComparator(criteria))
                        .forEach(child -> buildTree(
                                childMap,
                                child,
                                depth + 1,
                                returnList,
                                criteria,
                                treeAction));
            }
        } else {
            // no children
            if (parent != null) {
                // This is a leaf so no need to do anything with the treeAction
                parent.setExpander(new Expander(depth, false, true));
            }
        }
    }

    private static boolean getDefaultExpansionState(final FindTaskProgressCriteria criteria,
                                                    final TaskProgress parent,
                                                    final Map<TaskId, Set<TaskProgress>> childTasksMap) {

        boolean isExpanded = false;
        if (criteria != null && criteria.getNameFilter() != null && !criteria.getNameFilter().isEmpty()) {
            // We want to expand all ancestors of a task that is a match of the filter so it
            // is clear to the user what is matching
            final Set<TaskProgress> childTasks = childTasksMap.get(parent.getId());
            if (childTasks != null && !childTasks.isEmpty()) {
                for (final TaskProgress childTask : childTasks) {
                    if (hasMatchedDescendant(childTask, childTasksMap)) {
                        isExpanded = true;
                        break;
                    }
                }
            }
        }

        if (!isExpanded && parent.getAgeMs() < 1_000) {
            // Force expansion on tasks that are younger than a second.
            isExpanded = true;
        }
        return isExpanded;
    }

    /**
     * Return true if taskProgress is a match in the filter or one of its descendants is
     */
    private static boolean hasMatchedDescendant(final TaskProgress taskProgress,
                                                final Map<TaskId, Set<TaskProgress>> childTasksMap) {

        boolean result = false;
        if (taskProgress != null) {
            if (taskProgress.isMatchedInFilter()) {
                // This task is a match so return true

//            GWT.log(ClientDateUtil.toISOString(System.currentTimeMillis()) + ": "
//                    + (root != null ? root.getId().getId() : "null")
//                    + " " + (root != null ? root.getTaskName() : "null") + " - "
//                    + (taskProgress != null ? taskProgress.getId().getId() : "null")
//                    + " " + (taskProgress != null ? taskProgress.getTaskName() : "null")
//                    + " returning:" + true + "FILTERED OUT");

                result = true;
            } else {
                // See if any of this task's children are a match
                final Set<TaskProgress> childTasks = childTasksMap.get(taskProgress.getId());
                if (childTasks != null && !childTasks.isEmpty()) {
                    for (final TaskProgress childTask : childTasks) {
                        if (hasMatchedDescendant(childTask, childTasksMap)) {
                            result = true;
                            break;
                        }
                    }
//                GWT.log(ClientDateUtil.toISOString(System.currentTimeMillis()) + ": "
//                        + (root != null ? root.getId().getId() : "null")
//                        + " " + (root != null ? root.getTaskName() : "null") + " - "
//                        + (taskProgress != null ? taskProgress.getId().getId() : "null")
//                        + " " + (taskProgress != null ? taskProgress.getTaskName() : "null")
//                        + " returning:" + result + "HAS_MATCHED_KIDS");
                }
            }
        }
        return result;
    }

    private static class TaskProgressComparator implements Comparator<TaskProgress> {

        private final FindTaskProgressCriteria criteria;

        private TaskProgressComparator(final FindTaskProgressCriteria criteria) {
            this.criteria = criteria;
        }

        @Override
        public int compare(final TaskProgress o1, final TaskProgress o2) {
            if (criteria.getSortList() != null) {
                for (final CriteriaFieldSort sort : criteria.getSortList()) {
                    final String field = sort.getId();

                    int compare = 0;
                    switch (field) {
                        case FindTaskProgressCriteria.FIELD_NAME:
                            compare = CompareUtil.compareString(o1.getTaskName(), o2.getTaskName());
                            break;
                        case FindTaskProgressCriteria.FIELD_USER:
                            compare = CompareUtil.compareString(
                                    o1.getUserRef().getDisplayName(),
                                    o2.getUserRef().getDisplayName());
                            break;
                        case FindTaskProgressCriteria.FIELD_SUBMIT_TIME:
                            compare = CompareUtil.compareLong(o1.getSubmitTimeMs(), o2.getSubmitTimeMs());
                            break;
                        case FindTaskProgressCriteria.FIELD_AGE:
                            compare = CompareUtil.compareLong(o1.getAgeMs(), o2.getAgeMs());
                            break;
                        case FindTaskProgressCriteria.FIELD_INFO:
                            compare = CompareUtil.compareString(o1.getTaskInfo(), o2.getTaskInfo());
                            break;
                        case FindTaskProgressCriteria.FIELD_NODE:
                            compare = CompareUtil.compareString(o1.getNodeName(), o2.getNodeName());
                            break;
                    }

                    if (sort.isDesc()) {
                        compare = compare * -1;
                    }

                    if (compare != 0) {
                        return compare;
                    }
                }
            }

            return 0;
        }
    }
}
