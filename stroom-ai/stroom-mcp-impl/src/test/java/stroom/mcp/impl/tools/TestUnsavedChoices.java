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

import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolSession;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestUnsavedChoices {

    private final ToolCallContext context = new TranslationWorld().context();

    @Test
    void choicesAreRecalledForTheirTargetAndBlock() {
        UnsavedChoices.remember(context, "acme-v1/N", TranslationBuilds.DOMAIN_CHOSEN, Map.of("user", "Split"));

        assertThat(UnsavedChoices.recalled(context, "acme-v1/N", TranslationBuilds.DOMAIN_CHOSEN))
                .containsExactly(Map.entry("user", "Split"));
        assertThat(UnsavedChoices.recalled(context, "acme-v1/N", TranslationBuilds.PLACEHOLDERS_CHOSEN)).isEmpty();
        assertThat(UnsavedChoices.recalled(context, "acme-v1/Other", TranslationBuilds.DOMAIN_CHOSEN)).isEmpty();
    }

    @Test
    void laterChoicesAreAddedToThoseBeforeAndReplaceOnesForTheSameKey() {
        UnsavedChoices.remember(context, "uuid-1", TranslationBuilds.PLACEHOLDERS_CHOSEN,
                Map.of("-", "Leave out", "N/A", "Keep as written"));
        UnsavedChoices.remember(context, "uuid-1", TranslationBuilds.PLACEHOLDERS_CHOSEN, Map.of("N/A", "Leave out"));
        UnsavedChoices.remember(context, "uuid-1", TranslationBuilds.DOMAIN_CHOSEN, Map.of("user", "Keep as is"));

        assertThat(UnsavedChoices.recalled(context, "uuid-1", TranslationBuilds.PLACEHOLDERS_CHOSEN))
                .containsOnly(Map.entry("-", "Leave out"), Map.entry("N/A", "Leave out"));
        assertThat(UnsavedChoices.recalled(context, "uuid-1", TranslationBuilds.DOMAIN_CHOSEN))
                .containsExactly(Map.entry("user", "Keep as is"));
    }

    @Test
    void nothingIsKeptWithoutATargetOrAWorkspace() {
        UnsavedChoices.remember(context, "", TranslationBuilds.DOMAIN_CHOSEN, Map.of("user", "Split"));
        assertThat(UnsavedChoices.recalled(context, "", TranslationBuilds.DOMAIN_CHOSEN)).isEmpty();

        final ToolCallContext noWorkspace = ToolCallContext.builder(new ToolSession(ToolSession.Kind.MCP, "s",
                new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A test client"))).build();
        UnsavedChoices.remember(noWorkspace, "uuid-1", TranslationBuilds.DOMAIN_CHOSEN, Map.of("user", "Split"));
        assertThat(UnsavedChoices.recalled(noWorkspace, "uuid-1", TranslationBuilds.DOMAIN_CHOSEN)).isEmpty();
    }
}
