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

package stroom.ai.impl.tools.skills;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestSkillInvocations {

    @Test
    void aSkillCommandIsReadWithItsValues() {
        final SkillInvocations.Invocation invocation = SkillInvocations.parse(
                "/skill:User-Activity-Timeline user=jsmith days=\"1 day\"").orElseThrow();

        assertThat(invocation.name()).isEqualTo("user-activity-timeline");
        assertThat(invocation.arguments()).isEqualTo(Map.of("user", "jsmith", "days", "1 day"));
        assertThat(invocation.text()).isNull();
    }

    @Test
    void wordsThatArentNamedValuesAreKeptAsTheyWere() {
        final SkillInvocations.Invocation invocation = SkillInvocations.parse("/skill:timeline for jsmith")
                .orElseThrow();

        assertThat(invocation.arguments()).isEmpty();
        assertThat(invocation.text()).isEqualTo("for jsmith");
    }

    @Test
    void theModelIsAskedToReadTheSkillWithFindSkills() {
        final String expanded = SkillInvocations.expand("/skill:timeline user=jsmith");

        assertThat(expanded).startsWith(SkillInvocations.EXPANDED_PREFIX)
                .contains("run the skill 'timeline'", "find_skills {\"name\":\"timeline\",\"arguments\":"
                                                      + "{\"user\":\"jsmith\"}}",
                        "asks the user for any required parameter not given");
    }

    @Test
    void anythingElseIsLeftAsItIs() {
        assertThat(SkillInvocations.expand("/compact")).isEqualTo("/compact");
        assertThat(SkillInvocations.expand("run the skill: timeline")).isEqualTo("run the skill: timeline");
        assertThat(SkillInvocations.parse(null)).isEmpty();
    }
}
