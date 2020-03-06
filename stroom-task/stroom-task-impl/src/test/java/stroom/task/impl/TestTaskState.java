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


import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class TestTaskState {
    @Test
    void test_toString() {
        final TaskState root = new TaskState(null, null);
        root.info(() -> "root");
        final TaskState child1 = new TaskState(null, null);
        root.addChild(child1);
        child1.info(() -> "child1");
        final TaskState child1child1 = new TaskState(null, null);
        child1child1.info(() -> "child1child1");
        child1.addChild(child1child1);
        final TaskState child1child2 = new TaskState(null, null);
        child1.addChild(child1child2);
        child1child2.info(() -> "child1child2");
        child1child2.info(() -> "child1child2");
        final TaskState child2 = new TaskState(null, null);
        root.addChild(child2);
        child2.info(() -> "child2");

        final List<TaskState> list = new ArrayList<>();
        list.add(root);
        list.add(child1);
        list.add(child1child1);
        list.add(child1child2);
        list.add(child2);

        final String tree = TaskThreadInfoUtil.getInfo(list);

        System.out.println(tree);
    }
}
