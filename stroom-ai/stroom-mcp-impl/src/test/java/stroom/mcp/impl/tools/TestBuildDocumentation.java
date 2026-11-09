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

import stroom.ai.impl.tools.fielddoc.Descriptions;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestBuildDocumentation {

    @Test
    void fieldsAreAskedToBeDescribedARoundAtATime() {
        // Seen in testing: 75 fields asked for at once, and the agent tried again and again to send every
        // description, with the markdown, in one call, which its context couldn't hold.
        final Descriptions described = Descriptions.asking(true);
        for (int i = 0; i < 60; i++) {
            described.of("field" + i, "EventDetail/Data[@Name='field" + i + "']/@Value", List.of("a", "b"), "");
        }
        final Map<String, Object> first = BuildDocumentation.needsDescriptions(described, List.of());

        assertThat((List<?>) first.get("fields")).hasSize(BuildDocumentation.DESCRIPTIONS_ROUND);
        assertThat(String.valueOf(first.get("hint"))).contains("the first 25 of the 60 to describe", "no markdown");

        // A last round of fewer says nothing of more to come.
        final Descriptions few = Descriptions.asking(true);
        few.of("one", "EventSource/User/Id", List.of("alice"), "");
        assertThat(String.valueOf(BuildDocumentation.needsDescriptions(few, List.of()).get("hint")))
                .doesNotContain("the rest follow");
    }
}
