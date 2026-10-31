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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestToolSession {

    @Test
    void aChatIsItsIdForStroomAiChat() {
        final ToolSession session = ToolSession.chat(12);

        assertThat(session.kind()).isEqualTo(ToolSession.Kind.CHAT);
        assertThat(session.id()).isEqualTo("12");
        assertThat(session.caller()).isEqualTo(ToolCaller.STROOM_AI_CHAT);
        assertThat(session.caller().isStroomAiChat()).isTrue();
        assertThat(session.isChat()).isTrue();
        assertThat(session.chatId()).hasValue(12);
        assertThat(session.requireChatId("read_turn")).isEqualTo(12);
        // Audit records and logs show a chat by its id, as they always have.
        assertThat(session).hasToString("12");
        assertThat(session).isEqualTo(ToolSession.chat(12));
    }

    @Test
    void anotherSessionIsNotAChat() {
        final ToolSession session = new ToolSession(ToolSession.Kind.MCP, "abc",
                new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A client"));

        assertThat(session.isChat()).isFalse();
        assertThat(session.chatId()).isEmpty();
        assertThat(session.caller().isStroomAiChat()).isFalse();
        assertThatThrownBy(() -> session.requireChatId("read_turn"))
                .isInstanceOf(ToolSessionException.class)
                .hasMessage("read_turn can only be used in a chat.");
    }

    @Test
    void aSessionNeedsAKindIdAndCaller() {
        assertThatThrownBy(() -> new ToolSession(null, "1", ToolCaller.STROOM_AI_CHAT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ToolSession(ToolSession.Kind.CHAT, null, ToolCaller.STROOM_AI_CHAT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ToolSession(ToolSession.Kind.CHAT, "1", null))
                .isInstanceOf(NullPointerException.class);
    }
}
