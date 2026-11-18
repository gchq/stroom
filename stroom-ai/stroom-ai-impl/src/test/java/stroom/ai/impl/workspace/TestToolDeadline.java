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

package stroom.ai.impl.workspace;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestToolDeadline {

    @Test
    void timeWaitingForTheUserDoesNotUseUpTheLimit() {
        // An MCP call's form waits for up to the form timeout: the call keeps the time it had left.
        final String result = ToolDeadline.run(Duration.ofMillis(300), () -> {
            ToolDeadline.paused(() -> sleep(500));
            ToolDeadline.check();
            assertThat(ToolDeadline.remaining()).hasValueSatisfying(left ->
                    assertThat(left).isPositive());
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void aPauseInsideAnInnerLimitPushesBackTheOuterOneToo() {
        final String result = ToolDeadline.run(Duration.ofMillis(300), () -> {
            ToolDeadline.run(Duration.ofHours(1), () -> ToolDeadline.paused(() -> sleep(500)));
            ToolDeadline.check();
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void workingTimeStillCounts() {
        assertThatThrownBy(() -> ToolDeadline.run(Duration.ofMillis(100), () -> {
            ToolDeadline.paused(() -> sleep(10));
            sleep(300);
            ToolDeadline.check();
            return "not reached";
        })).isInstanceOf(ToolTimeoutException.class);
    }

    @Test
    void aPauseWithNoLimitInForceDoesNothing() {
        assertThat(ToolDeadline.paused(() -> "ok")).isEqualTo("ok");
        assertThat(ToolDeadline.remaining()).isEmpty();
    }

    private static String sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "slept";
    }
}
