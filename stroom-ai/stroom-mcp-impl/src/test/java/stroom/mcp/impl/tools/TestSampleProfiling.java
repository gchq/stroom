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

package stroom.mcp.impl.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSampleProfiling {

    private final SampleProfiling profiling = new SampleProfiling.Profiled();

    @Test
    void aSampleIsProfiledByThePortedProfiler() {
        final Map<String, Object> profiled = profiling.profile("""
                {"user": "alice", "action": "login"}
                {"user": "bob", "action": "logout"}
                """);

        assertThat(profiled).containsEntry("format", "json lines");
    }

    @Test
    void severalSamplesAreProfiledTogether() {
        final Map<String, Object> profiled = profiling.profileMany(Map.of(
                "a.log", "{\"user\": \"alice\"}\n",
                "b.log", "{\"user\": \"bob\"}\n"));

        assertThat(profiled).containsKey("files");
    }

    @Test
    void aSampleTheProfilerRefusesIsAToolRefusal() {
        // Text that starts as JSON and isn't.
        assertThatThrownBy(() -> profiling.profile("{\"user\": "))
                .isInstanceOf(ToolRefusal.class);
    }
}
