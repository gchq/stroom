/*
 * Copyright 2020 Crown Copyright
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

import stroom.task.api.TerminationCause;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestTaskContextImpl {

    @Test
    void test_toString() {
        final TaskContextImpl root = create();
        root.info(() -> "root");
        final TaskContextImpl child1 = create();
        root.addChild(child1);
        child1.info(() -> "child1");
        final TaskContextImpl child1child1 = create();
        child1child1.info(() -> "child1child1");
        child1.addChild(child1child1);
        final TaskContextImpl child1child2 = create();
        child1.addChild(child1child2);
        child1child2.info(() -> "child1child2");
        child1child2.info(() -> "child1child2");
        final TaskContextImpl child2 = create();
        root.addChild(child2);
        child2.info(() -> "child2");

        final List<TaskContextImpl> list = new ArrayList<>();
        list.add(root);
        list.add(child1);
        list.add(child1child1);
        list.add(child1child2);
        list.add(child2);

        final String tree = TaskThreadInfoUtil.getInfo(list);

        System.out.println(tree);
    }

    @Test
    void test_notTerminated() {
        final TaskContextImpl taskContext = create();

        assertThat(taskContext.isTerminated()).isFalse();
        assertThat(taskContext.getTerminationCause()).isEmpty();
    }

    @Test
    void test_firstTerminationCauseIsKept() {
        final TaskContextImpl taskContext = create();

        taskContext.terminate(TerminationCause.user("someone"));
        taskContext.terminate(TerminationCause.NODE_STOPPING);

        assertThat(taskContext.isTerminated()).isTrue();
        assertThat(taskContext.getTerminationCause()).contains(TerminationCause.user("someone"));
    }

    @Test
    void test_terminationWithoutACauseIsKept() {
        final TaskContextImpl taskContext = create();

        taskContext.terminate();
        taskContext.terminate(TerminationCause.NODE_STOPPING);

        assertThat(taskContext.getTerminationCause()).contains(TerminationCause.NOT_GIVEN);
    }

    @Test
    void test_terminationCauseIsPassedToChildren() {
        final TaskContextImpl parent = create();
        final TaskContextImpl child = create();
        final TaskContextImpl grandchild = create();
        final TaskContextImpl terminatedChild = create();
        parent.addChild(child);
        child.addChild(grandchild);
        parent.addChild(terminatedChild);
        terminatedChild.terminate(TerminationCause.HEARTBEAT_OVERDUE);
        assertThat(parent.isTerminated()).isFalse();

        parent.terminate(TerminationCause.NODE_STOPPING);
        final TaskContextImpl lateChild = create();
        parent.addChild(lateChild);

        assertThat(child.getTerminationCause()).contains(TerminationCause.NODE_STOPPING);
        assertThat(grandchild.getTerminationCause()).contains(TerminationCause.NODE_STOPPING);
        assertThat(lateChild.getTerminationCause()).contains(TerminationCause.NODE_STOPPING);
        assertThat(terminatedChild.getTerminationCause()).contains(TerminationCause.HEARTBEAT_OVERDUE);
    }

    private TaskContextImpl create() {
        return new TaskContextImpl(TaskIdFactory.create(), "test", () -> null, false);
    }
}
