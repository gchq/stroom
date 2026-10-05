/*
 * Copyright 2026 Crown Copyright
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

package stroom.gwt.workbench.framework.client.play;

import stroom.gwt.workbench.framework.client.play.PlayStep.Kind;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestPlayStep {

    @Test
    void testRunAll_groupsInsideWaitFor() {
        // Regression: the runner called run() on each step in a waitFor, which threw a
        // NullPointerException for a nested step(...) or waitFor(...) group
        final List<String> ran = new ArrayList<>();
        final Play play = new Play();
        play.waitFor(() -> {
            play.addStep(action("a", ran));
            play.step("Group", () -> {
                play.addStep(action("b", ran));
                play.waitFor(() -> play.addStep(action("c", ran)));
            });
        });
        final PlayStep waitFor = play.getSteps().get(0);
        final Recorder recorder = new Recorder();

        for (final PlayStep child : waitFor.getChildren()) {
            child.runAll(null, recorder);
        }

        assertThat(ran).containsExactly("a", "b", "c");
        assertThat(recorder.passed).containsExactly("a", "b", "c", "waitFor(anonymous)", "step(\"Group\")");
    }

    @Test
    void testRunAll_stopsAtFirstFailure() {
        final List<String> ran = new ArrayList<>();
        final PlayStep failing = PlayStep.action(root -> "fail", root -> {
            throw new AssertionError("Boom");
        });
        final PlayStep group = PlayStep.group(Kind.STEP, root -> "group",
                List.of(action("a", ran), failing, action("b", ran)));
        final Recorder recorder = new Recorder();

        // Errors as well as exceptions are passed on so the runner can report them
        assertThatThrownBy(() -> group.runAll(null, recorder))
                .isInstanceOf(AssertionError.class)
                .hasMessage("Boom");
        assertThat(ran).containsExactly("a");
        assertThat(recorder.started).containsExactly("group", "a", "fail");
        assertThat(recorder.passed).containsExactly("a");
    }

    @Test
    void testRun_group() {
        final PlayStep group = PlayStep.group(Kind.STEP, root -> "group", List.of());
        assertThatThrownBy(() -> group.run(null))
                .isInstanceOf(IllegalStateException.class);
    }

    private static PlayStep action(final String name, final List<String> ran) {
        return PlayStep.action(root -> name, root -> ran.add(name));
    }


    // --------------------------------------------------------------------------------


    private static final class Recorder implements PlayStep.Listener {

        private final List<String> started = new ArrayList<>();
        private final List<String> passed = new ArrayList<>();

        @Override
        public void started(final PlayStep step) {
            started.add(step.describe(null));
        }

        @Override
        public void passed(final PlayStep step) {
            passed.add(step.describe(null));
        }
    }
}
