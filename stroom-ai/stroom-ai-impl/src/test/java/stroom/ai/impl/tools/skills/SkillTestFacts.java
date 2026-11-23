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

import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolSchema;
import stroom.ai.impl.tools.skills.SkillChecks.DataSource;
import stroom.ai.impl.tools.skills.SkillChecks.OtherSkill;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/// Facts for the skill checks in tests: every tool a tool (taking any arguments), allowed, and every data source
/// usable; or no tools at all.
final class SkillTestFacts {

    private SkillTestFacts() {
    }

    /// @return Facts in which every tool is one, allowed, with any arguments, and every data source usable.
    static SkillChecks.Facts permissive() {
        return facts(true);
    }

    /// @return Facts in which no tool is one.
    static SkillChecks.Facts noTools() {
        return facts(false);
    }

    private static SkillChecks.Facts facts(final boolean tools) {
        return new SkillChecks.Facts() {
            @Override
            public Optional<ToolDefinition> tool(final String name) {
                return tools
                        ? Optional.of(ToolDefinition.builder().name(name).description(name)
                                .parameters(ToolSchema.ObjectSchema.builder().build()).build())
                        : Optional.empty();
            }

            @Override
            public Set<String> toolNames() {
                return Set.of();
            }

            @Override
            public boolean allowed(final String name) {
                return true;
            }

            @Override
            public DataSource dataSource(final String dataSource) {
                return new DataSource(true, null);
            }

            @Override
            public Optional<String> closestByPurpose(final Set<String> words) {
                return Optional.empty();
            }

            @Override
            public List<OtherSkill> otherSkills() {
                return List.of();
            }

            @Override
            public SkillUsage.Counts usage(final String skill) {
                return null;
            }
        };
    }
}
