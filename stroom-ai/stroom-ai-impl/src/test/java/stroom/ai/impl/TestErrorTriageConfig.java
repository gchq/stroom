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

package stroom.ai.impl;

import stroom.ai.impl.tools.ErrorTriage;
import stroom.util.json.JsonUtil;
import stroom.util.yaml.YamlUtil;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestErrorTriageConfig {

    @Test
    void theRulesAreAYamlListOfMappingsInConfigYml() throws Exception {
        // As an administrator writes them under appConfig.ai.errorTriage in config.yml.
        final String yaml = """
                rules:
                  - element: decorationFilter
                    severity: [WARNING, INFO]
                    message: lookup
                    class: benign
                    reason: User decoration miss for accounts not in reference data
                  - {element: splitFilter, own: true, class: blocking, reason: lost record, cause: true}
                """;

        final ErrorTriageConfig config = YamlUtil.getMapper().readValue(yaml, ErrorTriageConfig.class);

        assertThat(config.getRules()).containsExactly(
                new ErrorTriageRuleConfig(List.of("WARNING", "INFO"), "decorationFilter", null, "lookup", "benign",
                        "User decoration miss for accounts not in reference data", null),
                new ErrorTriageRuleConfig(null, "splitFilter", true, null, "blocking", "lost record", true));
        assertThat(ErrorTriage.withEnvironmentRules(config.getRules())
                .classify("WARNING", "decorationFilter", false, "Failed lookup for user x")
                .classification())
                .isEqualTo("benign");
    }

    @Test
    void aRuleIsWrittenWithItsClassAsClassAndLeavesUnsetFieldsOut() {
        // The Properties screen shows a list of rules as a JSON array.
        final ErrorTriageRuleConfig rule = new ErrorTriageRuleConfig(null, "decorationFilter", null, null,
                "benign", "known miss", null);

        final String json = JsonUtil.writeValueAsString(rule, false);

        assertThat(json).isEqualTo("{\"element\":\"decorationFilter\",\"class\":\"benign\",\"reason\":\"known miss\"}");
        assertThat(JsonUtil.readValue(json, ErrorTriageRuleConfig.class)).isEqualTo(rule);
    }

    @Test
    void thereAreNoRulesByDefault() {
        assertThat(new AiConfig().getErrorTriage().getRules()).isEmpty();
    }
}
