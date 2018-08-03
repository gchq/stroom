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

package stroom.task;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class TestTaskThread {
    @Test
    public void test_toString() {
        final TaskThread root = new TaskThread(null);
        root.info("root");
        final TaskThread child1 = new TaskThread(null);
        root.addChild(child1);
        child1.info("child1");
        final TaskThread child1child1 = new TaskThread(null);
        child1child1.info("child1child1");
        child1.addChild(child1child1);
        final TaskThread child1child2 = new TaskThread(null);
        child1.addChild(child1child2);
        child1child2.info("child1child2");
        child1child2.info("child1child2");
        final TaskThread child2 = new TaskThread(null);
        root.addChild(child2);
        child2.info("child2");

        final List<TaskThread> list = new ArrayList<>();
        list.add(root);
        list.add(child1);
        list.add(child1child1);
        list.add(child1child2);
        list.add(child2);

        final String tree = TaskThreadInfoUtil.getInfo(list);

        System.out.println(tree);
    }
}
