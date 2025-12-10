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


import stroom.node.shared.Node;
import stroom.task.shared.FindTaskProgressCriteria;
import stroom.task.shared.TaskId;
import stroom.task.shared.TaskProgress;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

class TestTaskProgressUtil {

    @Test
    void testSortByAge() {
        final FindTaskProgressCriteria criteria = new FindTaskProgressCriteria();
        final TaskManagerTreeAction treeAction = new TaskManagerTreeAction();

        final Map<TaskId, TaskProgress> totalMap = createMap(criteria);
        List<TaskProgress> sortedList;

        // Test sorting by age.
        criteria.setSort(FindTaskProgressCriteria.FIELD_AGE, false, false);
        sortedList = TaskProgressUtil.createList(totalMap, criteria, treeAction);
        testList(sortedList, (v1, v2) -> v1.getAgeMs() <= v2.getAgeMs());

        criteria.setSort(FindTaskProgressCriteria.FIELD_AGE, true, false);
        sortedList = TaskProgressUtil.createList(totalMap, criteria, treeAction);
        testList(sortedList, (v1, v2) -> v1.getAgeMs() >= v2.getAgeMs());

        // Test sorting by submit time.
        criteria.setSort(FindTaskProgressCriteria.FIELD_SUBMIT_TIME, false, false);
        sortedList = TaskProgressUtil.createList(totalMap, criteria, treeAction);
        testList(sortedList, (v1, v2) -> v1.getSubmitTimeMs() <= v2.getSubmitTimeMs());

        criteria.setSort(FindTaskProgressCriteria.FIELD_SUBMIT_TIME, true, false);
        sortedList = TaskProgressUtil.createList(totalMap, criteria, treeAction);
        testList(sortedList, (v1, v2) -> v1.getSubmitTimeMs() >= v2.getSubmitTimeMs());

        // Test sorting by name.
        criteria.setSort(FindTaskProgressCriteria.FIELD_NAME, false, false);
        sortedList = TaskProgressUtil.createList(totalMap, criteria, treeAction);
        testList(sortedList, (v1, v2) -> v1.getTaskName().compareTo(v2.getTaskName()) <= 0);

        criteria.setSort(FindTaskProgressCriteria.FIELD_NAME, true, false);
        sortedList = TaskProgressUtil.createList(totalMap, criteria, treeAction);
        testList(sortedList, (v1, v2) -> v1.getTaskName().compareTo(v2.getTaskName()) >= 0);

        // Test sorting by user.
        criteria.setSort(FindTaskProgressCriteria.FIELD_USER, false, false);
        sortedList = TaskProgressUtil.createList(totalMap, criteria, treeAction);
        testList(sortedList, (v1, v2) ->
                v1.getUserRef().getDisplayName().compareTo(v2.getUserRef().getDisplayName()) <= 0);

        criteria.setSort(FindTaskProgressCriteria.FIELD_USER, true, false);
        sortedList = TaskProgressUtil.createList(totalMap, criteria, treeAction);
        testList(sortedList, (v1, v2) ->
                v1.getUserRef().getDisplayName().compareTo(v2.getUserRef().getDisplayName()) >= 0);

        // Test sorting by node.
        criteria.setSort(FindTaskProgressCriteria.FIELD_NODE, false, false);
        sortedList = TaskProgressUtil.createList(totalMap, criteria, treeAction);
        testList(sortedList, (v1, v2) -> v1.getNodeName().compareTo(v2.getNodeName()) <= 0);

        criteria.setSort(FindTaskProgressCriteria.FIELD_NODE, true, false);
        sortedList = TaskProgressUtil.createList(totalMap, criteria, treeAction);
        testList(sortedList, (v1, v2) -> v1.getNodeName().compareTo(v2.getNodeName()) >= 0);
    }

    private void testList(final List<TaskProgress> sortedList,
                          final BiFunction<TaskProgress, TaskProgress, Boolean> compareFunction) {
        TaskProgress lastParent = null;
        TaskProgress lastChild = null;
        for (final TaskProgress taskProgress : sortedList) {
            // Is this a parent.
            if (taskProgress.getId().getParentId() == null) {
                if (lastParent != null) {
                    assertThat(compareFunction.apply(lastParent, taskProgress)).isTrue();
                }

                lastParent = taskProgress;
                lastChild = null;
            } else {
                if (lastChild != null) {
                    assertThat(compareFunction.apply(lastChild, taskProgress)).isTrue();
                }

                lastChild = taskProgress;
            }
        }
    }

    private Map<TaskId, TaskProgress> createMap(final FindTaskProgressCriteria criteria) {
        final Node node1 = Node.create("node1");
        final Node node2 = Node.create("node2");

        final long now = 10000000;
        final long baseSubmitTime = now - 1000000;
        final Map<TaskId, TaskProgress> totalMap = new HashMap<>();

        for (int i = 0; i < 10; i++) {
            final long parentSubmitTime = baseSubmitTime + (long) (Math.random() * 100);
            final Node node;
            if (((int) (Math.random() * 100)) % 2 == 0) {
                node = node1;
            } else {
                node = node2;
            }

            final String user;
            if (((int) (Math.random() * 100)) % 2 == 0) {
                user = "user1";
            } else {
                user = "user2";
            }

            final TaskId parentId = new TaskId("parent" + i, null);
            final TaskProgress parent = new TaskProgress();
            parent.setId(parentId);
            parent.setSubmitTimeMs(parentSubmitTime);
            parent.setTimeNowMs(now);
            parent.setNodeName(node.getName());
            parent.setTaskName("parent task " + i);
            parent.setThreadName("thread");
            parent.setUserRef(UserRef.builder().displayName(user).build());

            totalMap.put(parent.getId(), parent);

            criteria.setExpanded(parent, true);

            for (int j = 0; j < 10; j++) {
                final TaskId childId = new TaskId("child" + i + ":" + j, parentId);
                final TaskProgress child = new TaskProgress();
                child.setId(childId);
                child.setSubmitTimeMs(parentSubmitTime + (long) (Math.random() * 100));
                child.setTimeNowMs(now);
                child.setNodeName(node.getName());
                child.setTaskName("child task " + i + ":" + j);
                child.setThreadName("thread");
                child.setUserRef(UserRef.builder().displayName(user).build());

                totalMap.put(child.getId(), child);
            }
        }

        return totalMap;
    }
}
