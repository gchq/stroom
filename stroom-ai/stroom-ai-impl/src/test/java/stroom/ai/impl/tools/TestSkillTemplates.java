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

package stroom.ai.impl.tools;

import stroom.ai.impl.tools.SkillChecks.Finding;
import stroom.ai.impl.tools.SkillTemplates.Template;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class TestSkillTemplates {

    @Test
    void eachTemplateIsASkillWithPlaceholdersToFillAndNoOtherProblem() {
        assertThat(SkillTemplates.all()).extracting(Template::name).containsExactlyElementsOf(SkillTemplates.NAMES);
        for (final Template template : SkillTemplates.all()) {
            assertThat(template.skill().description()).as(template.name()).isNotBlank();
            assertThat(template.skill().placeholders()).as(template.name()).isNotEmpty();
            final List<Finding> problems = SkillChecks.problems(SkillChecks.check(template.skill(), null,
                    SkillTestFacts.permissive()));
            assertThat(problems).as(template.name()).extracting(Finding::rule).containsOnly("unfilled_placeholder");
        }
    }

    @Test
    void aTemplateIsFoundByItsNameOrItsSkillsName() {
        assertThat(SkillTemplates.find("User activity timeline")).map(Template::name)
                .contains("user-activity-timeline");
        assertThat(SkillTemplates.find("nope")).isEmpty();
    }

    @Test
    void theTemplatesHoldNoProductSpecificContent() {
        for (final Template template : SkillTemplates.all()) {
            assertThat(template.text().toLowerCase(Locale.ROOT)).as(template.name())
                    .doesNotContain("sysmon", "windows security", "microsoft", "4624", "crowdstrike");
        }
    }
}
