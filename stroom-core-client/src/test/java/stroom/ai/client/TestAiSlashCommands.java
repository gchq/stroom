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

package stroom.ai.client;

import stroom.ai.shared.AiAgentMode;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestAiSlashCommands {

    @Test
    void ownCommandsAreKnown() {
        assertThat(AiSlashCommands.COMMANDS).extracting(AiSlashCommand::getName)
                .containsExactly("/compact", "/mode", "/plan", "/new", "/clear", "/remember", "/help");
        assertThat(AiSlashCommands.isOwn("/plan")).isTrue();
        // An MCP server's prompt is not one of the chat's own.
        assertThat(AiSlashCommands.isOwn("/onboard_data_source")).isFalse();
    }

    @Test
    void modesAreReadInAnyCase() {
        assertThat(AiSlashCommands.parseMode("plan")).isEqualTo(AiAgentMode.PLAN);
        assertThat(AiSlashCommands.parseMode(" Ask ")).isEqualTo(AiAgentMode.ASK);
        assertThat(AiSlashCommands.parseMode("AGENT")).isEqualTo(AiAgentMode.AGENT);
        assertThat(AiSlashCommands.parseMode("")).isNull();
        assertThat(AiSlashCommands.parseMode("chaos")).isNull();
        assertThat(AiSlashCommands.parseMode(null)).isNull();
    }

    @Test
    void factsAreAddedAsBullets() {
        final String first = AiSlashCommands.addFact(null, " Our firewall feeds start with FW- ");
        assertThat(first).isEqualTo("# Remembered\n\n- Our firewall feeds start with FW-\n");

        final String second = AiSlashCommands.addFact(first, "Times are in UTC\nalways");
        assertThat(second).isEqualTo("# Remembered\n\n- Our firewall feeds start with FW-\n"
                                     + "- Times are in UTC always\n");
    }

    @Test
    void helpListsEveryCommandWithWhatItDoes() {
        final String help = AiSlashCommands.help(List.of(
                new AiSlashCommand("/plan", "<task>", "Plan it"),
                new AiSlashCommand("/help", "", "List the commands")));

        assertThat(help).isEqualTo("/plan <task> - Plan it\n/help - List the commands");
    }
}
