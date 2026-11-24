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

    /// @param missing The one data source that doesn't exist.
    /// @param like    The data sources named like it.
    /// @return Facts as [#permissive()], but for that data source.
    static SkillChecks.Facts missingDataSource(final String missing, final List<String> like) {
        final SkillChecks.Facts permissive = permissive();
        return new SkillChecks.Facts() {
            @Override
            public Optional<ToolDefinition> tool(final String name) {
                return permissive.tool(name);
            }

            @Override
            public Set<String> toolNames() {
                return permissive.toolNames();
            }

            @Override
            public boolean allowed(final String name) {
                return true;
            }

            @Override
            public DataSource dataSource(final String dataSource) {
                return new DataSource(!missing.equalsIgnoreCase(dataSource), null);
            }

            @Override
            public List<String> dataSourcesLike(final String name) {
                return missing.equalsIgnoreCase(name)
                        ? like
                        : List.of();
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
