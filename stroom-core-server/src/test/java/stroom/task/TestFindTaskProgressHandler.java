/*
 * Copyright 2018 Crown Copyright
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

package stroom.task;

import org.junit.Assert;
import org.junit.Test;
import stroom.entity.shared.Sort.Direction;
import stroom.node.shared.Node;
import stroom.task.shared.FindTaskProgressCriteria;
import stroom.task.shared.TaskId;
import stroom.task.shared.TaskIdImpl;
import stroom.task.shared.TaskProgress;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class TestFindTaskProgressHandler {
    @Test
    public void testSortByAge() {
        final FindTaskProgressHandler findTaskProgressHandler = new FindTaskProgressHandler(null, null);
        final FindTaskProgressCriteria criteria = new FindTaskProgressCriteria();

        final Map<TaskId, TaskProgress> totalMap = createMap(criteria);
        List<TaskProgress> sortedList;

        // Test sorting by age.
        criteria.setSort(FindTaskProgressCriteria.FIELD_AGE, Direction.ASCENDING, false);
        sortedList = findTaskProgressHandler.createList(totalMap, criteria);
        testList(sortedList, (v1, v2) -> v1.getAgeMs() <= v2.getAgeMs());

        criteria.setSort(FindTaskProgressCriteria.FIELD_AGE, Direction.DESCENDING, false);
        sortedList = findTaskProgressHandler.createList(totalMap, criteria);
        testList(sortedList, (v1, v2) -> v1.getAgeMs() >= v2.getAgeMs());

        // Test sorting by submit time.
        criteria.setSort(FindTaskProgressCriteria.FIELD_SUBMIT_TIME, Direction.ASCENDING, false);
        sortedList = findTaskProgressHandler.createList(totalMap, criteria);
        testList(sortedList, (v1, v2) -> v1.getSubmitTimeMs() <= v2.getSubmitTimeMs());

        criteria.setSort(FindTaskProgressCriteria.FIELD_SUBMIT_TIME, Direction.DESCENDING, false);
        sortedList = findTaskProgressHandler.createList(totalMap, criteria);
        testList(sortedList, (v1, v2) -> v1.getSubmitTimeMs() >= v2.getSubmitTimeMs());

        // Test sorting by name.
        criteria.setSort(FindTaskProgressCriteria.FIELD_NAME, Direction.ASCENDING, false);
        sortedList = findTaskProgressHandler.createList(totalMap, criteria);
        testList(sortedList, (v1, v2) -> v1.getTaskName().compareTo(v2.getTaskName()) <= 0);

        criteria.setSort(FindTaskProgressCriteria.FIELD_NAME, Direction.DESCENDING, false);
        sortedList = findTaskProgressHandler.createList(totalMap, criteria);
        testList(sortedList, (v1, v2) -> v1.getTaskName().compareTo(v2.getTaskName()) >= 0);

        // Test sorting by user.
        criteria.setSort(FindTaskProgressCriteria.FIELD_USER, Direction.ASCENDING, false);
        sortedList = findTaskProgressHandler.createList(totalMap, criteria);
        testList(sortedList, (v1, v2) -> v1.getUserName().compareTo(v2.getUserName()) <= 0);

        criteria.setSort(FindTaskProgressCriteria.FIELD_USER, Direction.DESCENDING, false);
        sortedList = findTaskProgressHandler.createList(totalMap, criteria);
        testList(sortedList, (v1, v2) -> v1.getUserName().compareTo(v2.getUserName()) >= 0);

        // Test sorting by node.
        criteria.setSort(FindTaskProgressCriteria.FIELD_NODE, Direction.ASCENDING, false);
        sortedList = findTaskProgressHandler.createList(totalMap, criteria);
        testList(sortedList, (v1, v2) -> v1.getNode().getName().compareTo(v2.getNode().getName()) <= 0);

        criteria.setSort(FindTaskProgressCriteria.FIELD_NODE, Direction.DESCENDING, false);
        sortedList = findTaskProgressHandler.createList(totalMap, criteria);
        testList(sortedList, (v1, v2) -> v1.getNode().getName().compareTo(v2.getNode().getName()) >= 0);
    }

    private void testList(final List<TaskProgress> sortedList, final BiFunction<TaskProgress, TaskProgress, Boolean> compareFunction) {
        TaskProgress lastParent = null;
        TaskProgress lastChild = null;
        for (final TaskProgress taskProgress : sortedList) {
            // Is this a parent.
            if (taskProgress.getId().getParentId() == null) {
                if (lastParent != null) {
                    Assert.assertTrue(compareFunction.apply(lastParent, taskProgress));
                }

                lastParent = taskProgress;
                lastChild = null;
            } else {
                if (lastChild != null) {
                    Assert.assertTrue(compareFunction.apply(lastChild, taskProgress));
                }

                lastChild = taskProgress;
            }
        }
    }

    private Map<TaskId, TaskProgress> createMap(final FindTaskProgressCriteria criteria) {
        final Node node1 = Node.create(null, "node1");
        final Node node2 = Node.create(null, "node2");

        final long now = 10000000;
        final long baseSubmitTime = now - 1000000;
        final Map<TaskId, TaskProgress> totalMap = new HashMap<>();

        for (int i = 0; i < 10; i++) {
            final long parentSubmitTime = baseSubmitTime + (long) (Math.random() * 100);
            Node node;
            if (((int) (Math.random() * 100)) % 2 == 0) {
                node = node1;
            } else {
                node = node2;
            }

            String user;
            if (((int) (Math.random() * 100)) % 2 == 0) {
                user = "user1";
            } else {
                user = "user2";
            }

            final TaskId parentId = new TaskIdImpl("parent" + i, null);
            final TaskProgress parent = new TaskProgress();
            parent.setId(parentId);
            parent.setSubmitTimeMs(parentSubmitTime);
            parent.setTimeNowMs(now);
            parent.setNode(node);
            parent.setTaskName("parent task " + i);
            parent.setThreadName("thread");
            parent.setUserName(user);
            parent.setSessionId("session1");

            totalMap.put(parent.getId(), parent);

            criteria.setExpanded(parent, true);

            for (int j = 0; j < 10; j++) {
                final TaskId childId = new TaskIdImpl("child" + i + ":" + j, parentId);
                final TaskProgress child = new TaskProgress();
                child.setId(childId);
                child.setSubmitTimeMs(parentSubmitTime + (long) (Math.random() * 100));
                child.setTimeNowMs(now);
                child.setNode(node);
                child.setTaskName("child task " + i + ":" + j);
                child.setThreadName("thread");
                child.setUserName(user);
                child.setSessionId("session1");

                totalMap.put(child.getId(), child);
            }
        }

        return totalMap;
    }
}
